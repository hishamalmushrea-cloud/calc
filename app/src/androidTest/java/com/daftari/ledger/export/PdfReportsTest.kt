package com.daftari.ledger.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * كشوف PDF تُنتَج كجداول (RTL)، وهذا يتأكد أنها تُنتَج فعلًا على جهاز حقيقي وأنها
 * ملفات PDF صالحة — المحتوى عربي والاتجاه من اليمين لليسار، فلا يكفي اختبار JVM.
 */
@RunWith(AndroidJUnit4::class)
class PdfReportsTest {

    private fun assertPdf(file: java.io.File) {
        assertTrue("file was not created", file.exists())
        assertTrue("file is empty", file.length() > 200L)
        val header = file.readBytes().take(5).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }

    @Test
    fun writeStatementProducesAReadablePdf() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val data = PdfReports.PdfStatementData(
            title = "كشف حساب: محمد",
            subtitle = "777000000",
            latinDigits = false,
            hideBalances = false,
            sections = listOf(
                PdfReports.PdfStatementSection(
                    currencyName = "يمني",
                    symbol = "",
                    fractionDigits = 0,
                    rows = listOf(
                        PdfReports.PdfStatementRow("29-08-2026", "بضاعة", 5_000L, 0L, 5_000L),
                        PdfReports.PdfStatementRow("29-08-2026", "دفعة أولى", 0L, 2_000L, 3_000L)
                    ),
                    totalDebtMinor = 5_000L,
                    totalCreditMinor = 2_000L,
                    netMinor = 3_000L
                )
            )
        )

        assertPdf(PdfReports.writeStatement(context, data, "statement-test.pdf"))
    }

    @Test
    fun writeStatementHandlesMultipleSectionsAndMasking() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val data = PdfReports.PdfStatementData(
            title = "كشف حساب: سعاد",
            latinDigits = true,
            hideBalances = true,
            sections = listOf(
                PdfReports.PdfStatementSection("ريال", "﷼", 2, listOf(PdfReports.PdfStatementRow("01-01-2026", "", 0L, 1_000L, -1_000L)), 0L, 1_000L, -1_000L),
                PdfReports.PdfStatementSection("دولار", "$", 2, listOf(PdfReports.PdfStatementRow("02-01-2026", "نقد", 500L, 0L, 500L)), 500L, 0L, 500L)
            )
        )

        assertPdf(PdfReports.writeStatement(context, data, "multi-test.pdf"))
    }

    @Test
    fun writePeriodReportProducesAReadablePdf() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertPdf(PdfReports.writePeriodReport(context, UiState()))
    }

    @Test
    fun writeDocumentReceiptProducesAReadablePdf() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val document = DocumentEntity(
            shopId = 1L,
            type = "SALE",
            amountMinor = 5_000L,
            occurredAt = System.currentTimeMillis(),
            docNumber = "1",
            notes = "ملاحظة"
        )
        assertPdf(PdfReports.writeDocumentReceipt(context, document, null, null, UiState()))
    }
}
