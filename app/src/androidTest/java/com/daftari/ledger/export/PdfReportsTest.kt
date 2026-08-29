package com.daftari.ledger.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * كشف حساب دفتر الحسابات يُرسل كملف PDF جدولي، وهذا يتأكد أن الملف يُنتَج فعلًا على جهاز
 * حقيقي وأنه PDF صالح — المحتوى عربي والاتجاه من اليمين لليسار، فلا يكفي اختبار JVM.
 */
@RunWith(AndroidJUnit4::class)
class PdfReportsTest {

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

        val file = PdfReports.writeStatement(context, data, "statement-test.pdf")

        assertTrue("file was not created", file.exists())
        assertTrue("file is empty", file.length() > 200L)
        val header = file.readBytes().take(5).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("%PDF-", header)
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

        val file = PdfReports.writeStatement(context, data, "multi-test.pdf")

        assertTrue("file was not created", file.exists())
        val header = file.readBytes().take(5).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }
}
