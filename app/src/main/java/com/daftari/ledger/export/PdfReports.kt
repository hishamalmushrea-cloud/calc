package com.daftari.ledger.export

import android.content.Context
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

    /** خلية في جدول PDF؛ [widthOverride] لدمج أعمدة متجاورة في خلية واحدة. */
    private data class Cell(
        val text: String,
        val bold: Boolean = false,
        val color: Int = Color.BLACK,
        val widthOverride: Float? = null
    )

    /** صف في جدول؛ [emphasized] لخلفية رمادية، و[vLines] للخطوط العمودية (قليلة عند دمج أعمدة). */
    private data class Row(
        val cells: List<Cell>,
        val emphasized: Boolean = false,
        val vLines: List<Int>? = null
    )

    /**
     * بانٍ مشترك لملفات PDF الجدولية كي تبدو كل كشوف التطبيق متسقة: عنوان متمركز،
     * ترويسة رمادية، شبكة، تذييل (نص + تاريخ التوليد + رقم الصفحة)، وترقيم صفحات.
     * الاتجاه من اليمين لليسار مع تشكيل العربية عبر StaticLayout.
     */
    private class PdfBuilder(val ctx: Context, private val footerText: String) {
        val doc = PdfDocument()
        private val direction = if (ctx.resources.configuration.layoutDirection == LayoutDirection.RTL) {
            TextDirectionHeuristics.RTL
        } else {
            TextDirectionHeuristics.LTR
        }
        private val titlePaint = TextPaint().apply { isAntiAlias = true; textSize = 15f; isFakeBoldText = true; color = Color.BLACK }
        private val subtitlePaint = TextPaint().apply { isAntiAlias = true; textSize = 10f; color = Color.GRAY }
        private val headingPaint = TextPaint().apply { isAntiAlias = true; textSize = 10.5f; isFakeBoldText = true; color = Color.BLACK }
        private val footerPaint = TextPaint().apply { isAntiAlias = true; textSize = 9f; color = Color.GRAY }
        private val gridPaint = Paint().apply { color = Color.rgb(150, 150, 150); strokeWidth = 0.8f }
        private val headerBg = Paint().apply { color = Color.rgb(228, 228, 228); style = Paint.Style.FILL }
        private val emphasisBg = Paint().apply { color = Color.rgb(242, 242, 242); style = Paint.Style.FILL }
        private val generatedText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        private val tableLeft = MARGIN
        private val tableRight = PAGE_W - MARGIN
        private var pageNumber = 1
        private var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        private var canvas = page.canvas
        private var y = MARGIN

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

        private fun centered(text: String, paint: TextPaint) {
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

        fun title(text: String) = centered(text, titlePaint)
        fun subtitle(text: String) {
            if (text.isNotBlank()) centered(text, subtitlePaint)
        }
        fun heading(text: String) = centered(text, headingPaint)
        fun space(points: Float) {
            y += points
        }

        private fun footer() {
            val fy = PAGE_H - 26f
            val rightLayout = cell(footerText, 220f, color = Color.GRAY)
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

        private fun newPage() {
            footer()
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        private fun ensureSpace(height: Float) {
            if (y + height > PAGE_H - 40f) newPage()
        }

        private fun hLine(yy: Float) {
            canvas.drawLine(tableLeft, yy, tableRight, yy, gridPaint)
        }

        private fun vLines(top: Float, bottom: Float, xs: FloatArray, at: List<Int>) {
            at.forEach { i -> canvas.drawLine(xs[i], top, xs[i], bottom, gridPaint) }
        }

        private fun band(top: Float, bottom: Float, paint: Paint) {
            canvas.drawRect(tableLeft, top, tableRight, bottom, paint)
        }

        private fun place(layout: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x + 4f, top + 5f)
            layout.draw(canvas)
            canvas.restore()
        }

        /** جدول كامل: ترويسة رمادية ثم الصفوف، مع ترقيم صفحات تلقائي عند الامتلاء. */
        fun table(xs: FloatArray, header: List<String>, rows: List<Row>) {
            val headerLayouts = header.mapIndexed { i, text -> cell(text, xs[i + 1] - xs[i] - 8f, bold = true) }
            val headerHeight = (headerLayouts.maxOf { it.height } + 10f).coerceAtLeast(24f)
            ensureSpace(headerHeight)
            band(y, y + headerHeight, headerBg)
            headerLayouts.forEachIndexed { i, layout -> place(layout, xs[i], y) }
            hLine(y)
            hLine(y + headerHeight)
            vLines(y, y + headerHeight, xs, xs.indices.toList())
            y += headerHeight
            rows.forEach { row ->
                val layouts = row.cells.mapIndexed { i, c ->
                    cell(c.text, c.widthOverride ?: (xs[i + 1] - xs[i] - 8f), c.bold, c.color)
                }
                val rowHeight = (layouts.maxOf { it.height } + 10f).coerceAtLeast(24f)
                ensureSpace(rowHeight)
                if (row.emphasized) band(y, y + rowHeight, emphasisBg)
                layouts.forEachIndexed { i, layout -> place(layout, xs[i], y) }
                hLine(y + rowHeight)
                vLines(y, y + rowHeight, xs, row.vLines ?: xs.indices.toList())
                y += rowHeight
            }
        }

        fun finish(fileName: String): File {
            footer()
            doc.finishPage(page)
            val f = File(ctx.cacheDir, fileName)
            f.outputStream().use { doc.writeTo(it) }
            doc.close()
            return f
        }
    }

    /** التقرير المالي لفترة: جدول (المبلغ | البيان) بنفس أسلوب بقية الكشوف. */
    fun writePeriodReport(ctx: Context, s: UiState): File {
        val t = s.totals
        val b = PdfBuilder(ctx, ctx.getString(R.string.pdf_footer_app))
        val xs = floatArrayOf(MARGIN, MARGIN + 200f, PAGE_W - MARGIN)
        fun money(minor: Long): String = s.displayMoney(minor, Locale.getDefault())
        b.title(ctx.getString(R.string.report_financial_title))
        b.subtitle(s.shop?.name.orEmpty())
        b.space(4f)
        val rows = listOf(
            Row(listOf(Cell(money(t.sales)), Cell(ctx.getString(R.string.sales)))),
            Row(listOf(Cell(money(t.purchases)), Cell(ctx.getString(R.string.purchases)))),
            Row(listOf(Cell(money(t.expenses)), Cell(ctx.getString(R.string.expenses)))),
            Row(listOf(Cell(money(t.collections)), Cell(ctx.getString(R.string.collections)))),
            Row(listOf(Cell(money(t.payments)), Cell(ctx.getString(R.string.payments)))),
            Row(listOf(Cell(money(t.cashNet), bold = true), Cell(ctx.getString(R.string.cash_net), bold = true)), emphasized = true),
            Row(listOf(Cell(money(t.estimatedProfit), bold = true), Cell(ctx.getString(R.string.estimated_profit), bold = true)), emphasized = true),
            Row(listOf(Cell(money(s.owedToYou)), Cell(ctx.getString(R.string.owed_to_you)))),
            Row(listOf(Cell(money(s.youOwe)), Cell(ctx.getString(R.string.you_owe))))
        )
        b.table(xs, listOf(ctx.getString(R.string.amount), ctx.getString(R.string.pdf_col_description)), rows)
        return b.finish("daftari-report.pdf")
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
        val b = PdfBuilder(ctx, ctx.getString(R.string.book_pdf_footer))
        // حدود الأعمدة من اليسار: الرصيد، له، عليه، التفاصيل، التاريخ (الأخير أقصى اليمين).
        val xs = floatArrayOf(MARGIN, MARGIN + 100f, MARGIN + 185f, MARGIN + 270f, MARGIN + 420f, PAGE_W - MARGIN)
        val header = listOf(
            ctx.getString(R.string.balance),
            ctx.getString(R.string.book_le),
            ctx.getString(R.string.book_debt),
            ctx.getString(R.string.book_pdf_details),
            ctx.getString(R.string.date)
        )
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

        b.title(data.title)
        b.subtitle(data.subtitle)
        b.space(4f)
        data.sections.forEach { section ->
            if (data.sections.size > 1) {
                b.heading(ctx.getString(R.string.book_pdf_currency, section.currencyName))
                b.space(2f)
            }
            val rows = buildList {
                section.rows.forEach { row ->
                    add(
                        Row(
                            listOf(
                                Cell(fmt(section, row.runningMinor), color = runningColor(row.runningMinor)),
                                Cell(if (row.creditMinor > 0L) fmt(section, row.creditMinor) else ""),
                                Cell(if (row.debtMinor > 0L) fmt(section, row.debtMinor) else ""),
                                Cell(row.details),
                                Cell(row.dateText)
                            )
                        )
                    )
                }
                // صف إجمالي العمليات: التسمية تمتد على عمودي التفاصيل والتاريخ.
                add(
                    Row(
                        cells = listOf(
                            Cell(""),
                            Cell(fmt(section, section.totalCreditMinor)),
                            Cell(fmt(section, section.totalDebtMinor)),
                            Cell(ctx.getString(R.string.book_pdf_total_row), bold = true, widthOverride = xs[5] - xs[3] - 8f),
                            Cell("")
                        ),
                        emphasized = true,
                        vLines = listOf(0, 1, 2, 3, 5)
                    )
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
                add(
                    Row(
                        cells = listOf(
                            Cell(netValue, bold = true, color = runningColor(section.netMinor), widthOverride = xs[3] - xs[0] - 8f),
                            Cell(""),
                            Cell(""),
                            Cell(netLabel, bold = true, widthOverride = xs[5] - xs[3] - 8f),
                            Cell("")
                        ),
                        emphasized = true,
                        vLines = listOf(0, 3, 5)
                    )
                )
            }
            b.table(xs, header, rows)
            b.space(10f)
        }
        return b.finish(fileName)
    }

    /** سند (قبض/صرف/…) كجدول (القيمة | البيان) بنفس أسلوب بقية الكشوف. */
    fun writeDocumentReceipt(
        ctx: Context,
        document: DocumentEntity,
        party: PartyEntity?,
        shop: ShopEntity?,
        state: UiState
    ): File {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val type = ctx.getString(documentTypeString(document.type))
        val b = PdfBuilder(ctx, ctx.getString(R.string.pdf_footer_app))
        val xs = floatArrayOf(MARGIN, MARGIN + 200f, PAGE_W - MARGIN)
        b.title(ctx.getString(R.string.receipt_title, type))
        b.subtitle(shop?.name.orEmpty())
        b.space(4f)
        val rows = listOf(
            Row(listOf(Cell(document.docNumber.ifBlank { "—" }), Cell(ctx.getString(R.string.pdf_label_doc_number)))),
            Row(listOf(Cell(format.format(Date(document.occurredAt))), Cell(ctx.getString(R.string.date)))),
            Row(listOf(Cell(party?.name ?: "—"), Cell(ctx.getString(R.string.pdf_label_party)))),
            Row(
                listOf(
                    Cell(state.displayMoney(document.amountMinor, Locale.getDefault()), bold = true),
                    Cell(ctx.getString(R.string.amount), bold = true)
                ),
                emphasized = true
            ),
            Row(listOf(Cell(document.notes), Cell(ctx.getString(R.string.notes))))
        )
        b.table(xs, listOf(ctx.getString(R.string.pdf_col_value), ctx.getString(R.string.pdf_col_description)), rows)
        return b.finish("daftari-receipt-${document.id}.pdf")
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
}
