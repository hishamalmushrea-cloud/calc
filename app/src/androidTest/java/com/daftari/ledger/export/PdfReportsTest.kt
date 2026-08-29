package com.daftari.ledger.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * كشف حساب دفتر الحسابات يُرسل كملف PDF، وهذا يتأكد أن الملف يُنتَج فعلًا على جهاز
 * حقيقي وأنه PDF صالح — المحتوى عربي والاتجاه من اليمين لليسار، فلا يكفي اختبار JVM.
 */
@RunWith(AndroidJUnit4::class)
class PdfReportsTest {

    @Test
    fun writeStatementProducesAReadablePdf() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val file = PdfReports.writeStatement(
            context,
            "كشف حساب: محمد",
            listOf(
                "يمني — له: 2,000 | عليه: 5,000 | الصافي: 3,000",
                "2026-08-29 10:15 | عليه | 5,000 | 5,000",
                "2026-08-29 10:20 | له | 2,000 | 3,000 | دفعة أولى"
            )
        )

        assertTrue("file was not created", file.exists())
        assertTrue("file is empty", file.length() > 200L)
        val header = file.inputStream().use { it.readBytes(5) }.toString(Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }
}
