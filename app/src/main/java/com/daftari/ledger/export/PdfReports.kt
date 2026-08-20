package com.daftari.ledger.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.daftari.ledger.domain.Money
import com.daftari.ledger.ui.UiState
import java.io.File

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
            "دفتري — تقرير مالي",
            s.shop?.name.orEmpty(),
            "مبيعات: ${Money(t.sales).format()}",
            "مشتريات: ${Money(t.purchases).format()}",
            "مصروفات: ${Money(t.expenses).format()}",
            "تحصيل: ${Money(t.collections).format()}",
            "سداد: ${Money(t.payments).format()}",
            "صافي نقدي: ${Money(t.cashNet).format()}",
            "ربح تقديري: ${Money(t.estimatedProfit).format()}",
            "لك: ${Money(s.owedToYou).format()}  عليك: ${Money(s.youOwe).format()}"
        )
        return writeLines(ctx, "daftari-report.pdf", lines)
    }

    fun writeStatement(ctx: Context, title: String, lines: List<String>): File =
        writeLines(ctx, "daftari-statement.pdf", listOf(title) + lines)

    private fun writeLines(ctx: Context, name: String, lines: List<String>): File {
        val doc = PdfDocument()
        val titlePaint = TextPaint().apply {
            isAntiAlias = true; textSize = 16f; isFakeBoldText = true; color = Color.BLACK
        }
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true; textSize = 13f; color = Color.BLACK
        }
        val width = (PAGE_W - MARGIN * 2).toInt()
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        lines.forEachIndexed { i, line ->
            val paint = if (i == 0) titlePaint else bodyPaint
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
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
