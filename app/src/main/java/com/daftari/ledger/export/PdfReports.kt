package com.daftari.ledger.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.daftari.ledger.domain.Money
import com.daftari.ledger.ui.UiState
import java.io.File

object PdfReports {
    fun writePeriodReport(ctx: Context, s: UiState): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c: Canvas = page.canvas
        val p = Paint().apply { textSize = 14f; isAntiAlias = true }
        var y = 40f
        fun line(t: String) { c.drawText(t, 40f, y, p); y += 22f }
        line("دفتري — تقرير مالي")
        line(s.shop?.name.orEmpty())
        val t = s.totals
        line("مبيعات: ${Money(t.sales).format()}")
        line("مشتريات: ${Money(t.purchases).format()}")
        line("مصروفات: ${Money(t.expenses).format()}")
        line("تحصيل: ${Money(t.collections).format()}")
        line("سداد: ${Money(t.payments).format()}")
        line("صافي نقدي: ${Money(t.cashNet).format()}")
        line("ربح تقديري: ${Money(t.estimatedProfit).format()}")
        line("لك: ${Money(s.owedToYou).format()}  عليك: ${Money(s.youOwe).format()}")
        doc.finishPage(page)
        val f = File(ctx.cacheDir, "daftari-report.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun writeStatement(ctx: Context, title: String, lines: List<String>): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val p = Paint().apply { textSize = 13f; isAntiAlias = true }
        var y = 40f
        c.drawText(title, 40f, y, p); y += 24f
        lines.take(30).forEach { c.drawText(it, 40f, y, p); y += 18f }
        doc.finishPage(page)
        val f = File(ctx.cacheDir, "daftari-statement.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
