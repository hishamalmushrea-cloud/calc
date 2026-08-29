package com.daftari.ledger.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.LayoutDirection
import com.daftari.ledger.R
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.data.ShopEntity
import com.daftari.ledger.domain.BookMoneyFormatter
import com.daftari.ledger.domain.Money
import com.daftari.ledger.ui.UiState
import com.daftari.ledger.ui.displayMoney
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * تصدير PDF مع دعم RTL وتشكيل العربية عبر StaticLayout
 * (بدل drawText الذي لا يشكّل العربية).
 */
object PdfReports {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    fun writePeriodReport(ctx: Context, s: UiState): File {
        val t = s.totals
        val lines = listOf(
            ctx.getString(R.string.report_financial_title),
            s.shop?.name.orEmpty(),
            ctx.getString(R.string.sales_value, s.displayMoney(t.sales, Locale.getDefault())),
            ctx.getString(R.string.purchases_value, s.displayMoney(t.purchases, Locale.getDefault())),
            ctx.getString(R.string.report_line, ctx.getString(R.string.expenses), s.displayMoney(t.expenses, Locale.getDefault())),
            ctx.getString(R.string.collections_value, s.displayMoney(t.collections, Locale.getDefault())),
            ctx.getString(R.string.payments_value, s.displayMoney(t.payments, Locale.getDefault())),
            ctx.getString(R.string.report_line, ctx.getString(R.string.cash_net), s.displayMoney(t.cashNet, Locale.getDefault())),
            ctx.getString(R.string.report_line, ctx.getString(R.string.estimated_profit), s.displayMoney(t.estimatedProfit, Locale.getDefault())),
            ctx.getString(R.string.report_receivables_payables, s.displayMoney(s.owedToYou, Locale.getDefault()), s.displayMoney(s.youOwe, Locale.getDefault()))
        )
        return writeLines(ctx, "daftari-report.pdf", lines)
    }

    /** صف واحد في جدول كشف الحساب: التاريخ والتفاصيل والمبلغ باتجاهه والرصيد الجاري. */
    data class PdfStatementRow(
        val dateText: String,
        val details: String,
        val debtMinor: Long,
        val creditMinor: Long,
        val runningMinor: Long
    )

    /** قسم بعملة واحدة داخل الكشف؛ لكل عملة جدول مستقل ولا تُخلط العملات. */
    data class PdfStatementSection(
        val currencyName: String,
        val symbol: String,
        val fractionDigits: Int,
        val rows: List<PdfStatementRow>,
        val totalDebtMinor: Long,
        val totalCreditMinor: Long,
        val netMinor: Long
    )

    /** كل ما يحتاجه جدول كشف الحساب؛ يبنيه ViewModel ويعرضه هذا المولّد. */
    data class PdfStatementData(
        val title: String,
        val subtitle: String = "",
        val latinDigits: Boolean = true,
        val hideBalances: Boolean = false,
        val sections: List<PdfStatementSection>
    )

    /**
     * كشف حساب شخص كجدول PDF مماثل لشاشة التطبيق: عنوان، ثم لكل عملة جدول بأعمدة
     * (التاريخ، التفاصيل، عليه، له، الرصيد) بترويسة رمادية، فصف «إجمالي العمليات»،
     * فصف «الرصيد الإجمالي»، وتذييل «بواسطة دفتر الحسابات» مع رقم الصفحة وتاريخها.
     *
     * الأعمدة تُرسم من اليمين لليسار، والأرقام تُنسّق كما في التطبيق، والرصيد الجاري
     * ملوّن (عليه بالأحمر وله بالأخضر)، ومع تفعيل إخفاء الأرصدة تُقنّع المبالغ.
     */
    fun writeStatement(ctx: Context, data: PdfStatementData, fileName: String): File {
        val doc = PdfDocument()
        val direction = if (ctx.resources.configuration.layoutDirection == LayoutDirection.RTL) {
            TextDirectionHeuristics.RTL
        } else {
            TextDirectionHeuristics.LTR
        }
        val titlePaint = TextPaint().apply { isAntiAlias = true; textSize = 15f; isFakeBoldText = true; color = Color.BLACK }
        val subtitlePaint = TextPaint().apply { isAntiAlias = true; textSize = 10f; color = Color.GRAY }
        val headingPaint = TextPaint().apply { isAntiAlias = true; textSize = 10.5f; isFakeBoldText = true; color = Color.BLACK }
        val footerPaint = TextPaint().apply { isAntiAlias = true; textSize = 9f; color = Color.GRAY }
        val gridPaint = Paint().apply { color = Color.rgb(150, 150, 150); strokeWidth = 0.8f }
        val headerBg = Paint().apply { color = Color.rgb(228, 228, 228); style = Paint.Style.FILL }
        val totalsBg = Paint().apply { color = Color.rgb(242, 242, 242); style = Paint.Style.FILL }
        val generatedText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val tableLeft = MARGIN
        val tableRight = PAGE_W - MARGIN
        // حدود الأعمدة من اليسار: الرصيد، له، عليه، التفاصيل، التاريخ (الأخير أقصى اليمين).
        val xs = floatArrayOf(tableLeft, tableLeft + 100f, tableLeft + 185f, tableLeft + 270f, tableLeft + 420f, tableRight)

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        // نبني كل خلية بصباغها الخاص (الحجم/العرض/اللون) لأن StaticLayout.draw يستخدم صباغ البناء.
        fun cell(text: String, width: Float, bold: Boolean = false, color: Int = Color.BLACK): StaticLayout {
            val paint = TextPaint().apply {
                isAntiAlias = true
                textSize = if (bold) 10.5f else 10f
                isFakeBoldText = bold
                this.color = color
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(direction)
                .setLineSpacing(2f, 1f)
                .setIncludePad(false)
                .build()
        }

        fun centered(text: String, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (PAGE_W - MARGIN * 2).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(direction)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 6f
        }

        fun footer() {
            val fy = PAGE_H - 26f
            val rightLayout = cell(ctx.getString(R.string.book_pdf_footer), 220f, color = Color.GRAY)
            canvas.save()
            canvas.translate(tableRight - rightLayout.width, fy)
            rightLayout.draw(canvas)
            canvas.restore()
            canvas.save()
            canvas.translate(MARGIN, fy)
            cell(generatedText, 220f, color = Color.GRAY).draw(canvas)
            canvas.restore()
            canvas.drawText(pageNumber.toString(), PAGE_W / 2f, fy + 10f, footerPaint)
        }

        fun newPage() {
            footer()
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(height: Float) {
            if (y + height > PAGE_H - 40f) newPage()
        }

        fun hLine(yy: Float) {
            canvas.drawLine(tableLeft, yy, tableRight, yy, gridPaint)
        }

        fun vLines(top: Float, bottom: Float, at: List<Int> = xs.indices.toList()) {
            at.forEach { i -> canvas.drawLine(xs[i], top, xs[i], bottom, gridPaint) }
        }

        fun fmt(section: PdfStatementSection, minor: Long): String =
            if (data.hideBalances) {
                "••••"
            } else {
                BookMoneyFormatter.format(
                    minor = minor,
                    fractionDigits = section.fractionDigits,
                    symbol = section.symbol,
                    latinDigits = data.latinDigits,
                    locale = if (data.latinDigits) Locale.US else Locale("ar")
                )
            }

        fun runningColor(minor: Long): Int = when {
            minor > 0L -> Color.rgb(139, 0, 0)
            minor < 0L -> Color.rgb(0, 100, 0)
            else -> Color.BLACK
        }

        fun tableHeader() {
            val labels = listOf(
                ctx.getString(R.string.balance),
                ctx.getString(R.string.book_le),
                ctx.getString(R.string.book_debt),
                ctx.getString(R.string.book_pdf_details),
                ctx.getString(R.string.date)
            )
            val layouts = labels.mapIndexed { i, text -> cell(text, xs[i + 1] - xs[i] - 8f, bold = true) }
            val height = (layouts.maxOf { it.height } + 10f).coerceAtLeast(24f)
            ensureSpace(height)
            canvas.drawRect(tableLeft, y, tableRight, y + height, headerBg)
            layouts.forEachIndexed { i, layout ->
                canvas.save()
                canvas.translate(xs[i] + 4f, y + 5f)
                layout.draw(canvas)
                canvas.restore()
            }
            hLine(y)
            hLine(y + height)
            vLines(y, y + height)
            y += height
        }

        fun drawRow(cells: List<StaticLayout>, background: Paint? = null, vAt: List<Int> = xs.indices.toList()) {
            val height = (cells.maxOf { it.height } + 10f).coerceAtLeast(24f)
            ensureSpace(height)
            if (background != null) {
                canvas.drawRect(tableLeft, y, tableRight, y + height, background)
            }
            cells.forEachIndexed { i, layout ->
                canvas.save()
                canvas.translate(xs[i] + 4f, y + 5f)
                layout.draw(canvas)
                canvas.restore()
            }
            hLine(y + height)
            vLines(y, y + height, vAt)
            y += height
        }

        centered(data.title, titlePaint)
        if (data.subtitle.isNotBlank()) centered(data.subtitle, subtitlePaint)
        y += 4f

        data.sections.forEach { section ->
            if (data.sections.size > 1) {
                centered(ctx.getString(R.string.book_pdf_currency, section.currencyName), headingPaint)
                y += 2f
            }
            tableHeader()
            section.rows.forEach { row ->
                drawRow(
                    cells = listOf(
                        cell(fmt(section, row.runningMinor), xs[1] - xs[0] - 8f, color = runningColor(row.runningMinor)),
                        cell(if (row.creditMinor > 0L) fmt(section, row.creditMinor) else "", xs[2] - xs[1] - 8f),
                        cell(if (row.debtMinor > 0L) fmt(section, row.debtMinor) else "", xs[3] - xs[2] - 8f),
                        cell(row.details, xs[4] - xs[3] - 8f),
                        cell(row.dateText, xs[5] - xs[4] - 8f)
                    )
                )
            }
            // صف إجمالي العمليات: التسمية تمتد على عمودي التفاصيل والتاريخ.
            drawRow(
                cells = listOf(
                    cell("", xs[1] - xs[0] - 8f),
                    cell(fmt(section, section.totalCreditMinor), xs[2] - xs[1] - 8f),
                    cell(fmt(section, section.totalDebtMinor), xs[3] - xs[2] - 8f),
                    cell(ctx.getString(R.string.book_pdf_total_row), xs[5] - xs[3] - 8f, bold = true),
                    cell("", xs[5] - xs[4] - 8f)
                ),
                background = totalsBg,
                vAt = listOf(0, 1, 2, 3, 5)
            )
            // صف الرصيد الإجمالي: القيمة ممتدة يسارًا والتسمية يمينًا.
            val netLabel = when {
                section.netMinor > 0L -> ctx.getString(R.string.book_pdf_net_owed)
                section.netMinor < 0L -> ctx.getString(R.string.book_pdf_net_credit)
                else -> ctx.getString(R.string.book_pdf_net_settled)
            }
            val netValue = if (data.hideBalances) {
                "••••"
            } else {
                "${fmt(section, kotlin.math.abs(section.netMinor))} ${section.currencyName}"
            }
            drawRow(
                cells = listOf(
                    cell(netValue, xs[3] - xs[0] - 8f, bold = true, color = runningColor(section.netMinor)),
                    cell("", xs[3] - xs[2] - 8f),
                    cell("", xs[4] - xs[3] - 8f),
                    cell(netLabel, xs[5] - xs[3] - 8f, bold = true),
                    cell("", xs[5] - xs[4] - 8f)
                ),
                background = totalsBg,
                vAt = listOf(0, 3, 5)
            )
            y += 10f
        }

        footer()
        doc.finishPage(page)
        val f = File(ctx.cacheDir, fileName)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun writeDocumentReceipt(
        ctx: Context,
        document: DocumentEntity,
        party: PartyEntity?,
        shop: ShopEntity?,
        state: UiState
    ): File {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val type = ctx.getString(documentTypeString(document.type))
        val lines = listOf(
            ctx.getString(R.string.receipt_title, type),
            shop?.name.orEmpty(),
            ctx.getString(R.string.receipt_number, document.docNumber.ifBlank { "—" }),
            ctx.getString(R.string.date_value, format.format(Date(document.occurredAt))),
            ctx.getString(R.string.receipt_party, party?.name ?: "—"),
            ctx.getString(R.string.report_line, ctx.getString(R.string.amount), state.displayMoney(document.amountMinor, Locale.getDefault())),
            ctx.getString(R.string.notes) + ": " + document.notes
        )
        return writeLines(ctx, "daftari-receipt-${document.id}.pdf", lines)
    }

    private fun documentTypeString(type: String): Int = when (type) {
        "SALE" -> R.string.doc_type_sale
        "PURCHASE" -> R.string.doc_type_purchase
        "EXPENSE" -> R.string.doc_type_expense
        "INCOME" -> R.string.doc_type_income
        "COLLECT" -> R.string.doc_type_collect
        "PAY" -> R.string.doc_type_pay
        "TRANSFER" -> R.string.doc_type_transfer
        "OPENING" -> R.string.doc_type_opening
        else -> R.string.doc_type_unknown
    }

    private fun writeLines(ctx: Context, name: String, lines: List<String>): File {
        val doc = PdfDocument()
        val titlePaint = TextPaint().apply {
            isAntiAlias = true; textSize = 16f; isFakeBoldText = true; color = Color.BLACK
        }
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true; textSize = 13f; color = Color.BLACK
        }
        val width = (PAGE_W - MARGIN * 2).toInt()
        val textDirection = if (ctx.resources.configuration.layoutDirection == LayoutDirection.RTL) {
            TextDirectionHeuristics.RTL
        } else {
            TextDirectionHeuristics.LTR
        }
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        lines.forEachIndexed { i, line ->
            val paint = if (i == 0) titlePaint else bodyPaint
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(textDirection)
                .setLineSpacing(4f, 1f)
                .setIncludePad(false)
                .build()
            val h = layout.height.toFloat()
            if (y + h > PAGE_H - MARGIN) {
                doc.finishPage(page)
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, page.info.pageNumber + 1).create()
                )
                canvas = page.canvas
                y = MARGIN
            }
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += h + 6f
        }
        doc.finishPage(page)
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
