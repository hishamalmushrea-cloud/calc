package com.daftari.ledger.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import com.daftari.ledger.domain.Money
import com.daftari.ledger.ui.UiState
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
            ctx.getString(R.string.sales_value, Money(t.sales).format()),
            ctx.getString(R.string.purchases_value, Money(t.purchases).format()),
            ctx.getString(R.string.report_line, ctx.getString(R.string.expenses), Money(t.expenses).format()),
            ctx.getString(R.string.collections_value, Money(t.collections).format()),
            ctx.getString(R.string.payments_value, Money(t.payments).format()),
            ctx.getString(R.string.report_line, ctx.getString(R.string.cash_net), Money(t.cashNet).format()),
            ctx.getString(R.string.report_line, ctx.getString(R.string.estimated_profit), Money(t.estimatedProfit).format()),
            ctx.getString(R.string.report_receivables_payables, Money(s.owedToYou).format(), Money(s.youOwe).format())
        )
        return writeLines(ctx, "daftari-report.pdf", lines)
    }

    fun writeStatement(ctx: Context, title: String, lines: List<String>): File =
        writeLines(ctx, "daftari-statement.pdf", listOf(title) + lines)

    fun writeDocumentReceipt(
        ctx: Context,
        document: DocumentEntity,
        party: PartyEntity?,
        shop: ShopEntity?
    ): File {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val type = ctx.getString(documentTypeString(document.type))
        val lines = listOf(
            ctx.getString(R.string.receipt_title, type),
            shop?.name.orEmpty(),
            ctx.getString(R.string.receipt_number, document.docNumber.ifBlank { "—" }),
            ctx.getString(R.string.date_value, format.format(Date(document.occurredAt))),
            ctx.getString(R.string.receipt_party, party?.name ?: "—"),
            ctx.getString(R.string.report_line, ctx.getString(R.string.amount), Money(document.amountMinor).format()),
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
