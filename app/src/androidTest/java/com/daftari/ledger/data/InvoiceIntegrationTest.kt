package com.daftari.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.domain.DocType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبارات تكاملية لدورة الفاتورة: الإنشاء، الأثر على المخزون، عرض البنود،
 * منع التعديل غير الآمن، والأرشفة/الاستعادة مع انعكاس المخزون.
 */
@RunWith(AndroidJUnit4::class)
class InvoiceIntegrationTest {
    private lateinit var db: AppDb
    private lateinit var repo: LedgerRepository
    private var shopId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        shopId = repo.createShop("محل الفواتير")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createItem(qtyMilli: Long = 10_000L): Long = repo.upsertItem(
        shopId = shopId,
        id = null,
        name = "صنف اختبار",
        sku = "SKU-1",
        unit = "قطعة",
        sellPriceMinor = 5_000L,
        costPriceMinor = 3_000L,
        qtyMilli = qtyMilli,
        reorderQtyMilli = 0L,
        trackStock = true
    )

    @Test
    fun saleInvoiceDecreasesTrackedStock() = runBlocking {
        val itemId = createItem()
        val docId = repo.postInvoice(
            shopId = shopId,
            type = DocType.SALE,
            lines = listOf(InvoiceLineInput(itemId, qtyMilli = 1_000L, unitPriceMinor = 5_000L)),
            occurredAt = System.currentTimeMillis()
        )
        assertEquals(9_000L, repo.items.get(itemId)?.qtyMilli)

        val lines = repo.documentLines.forDocument(docId)
        assertEquals(1, lines.size)
        assertEquals("صنف اختبار", lines.single().itemName)
        assertEquals(5_000L, lines.single().lineTotalMinor)
    }

    @Test
    fun archiveReversesStockAndRestoreDeductsAgain() = runBlocking {
        val itemId = createItem()
        val docId = repo.postInvoice(
            shopId = shopId,
            type = DocType.SALE,
            lines = listOf(InvoiceLineInput(itemId, qtyMilli = 2_000L, unitPriceMinor = 5_000L)),
            occurredAt = System.currentTimeMillis()
        )
        assertEquals(8_000L, repo.items.get(itemId)?.qtyMilli)

        repo.softDeleteDocument(docId)
        assertEquals(10_000L, repo.items.get(itemId)?.qtyMilli)

        repo.restoreDocument(docId)
        assertEquals(8_000L, repo.items.get(itemId)?.qtyMilli)
    }

    @Test
    fun updateRejectsFinancialChangeForInvoicedDocument() = runBlocking {
        val itemId = createItem()
        val docId = repo.postInvoice(
            shopId = shopId,
            type = DocType.SALE,
            lines = listOf(InvoiceLineInput(itemId, qtyMilli = 1_000L, unitPriceMinor = 5_000L)),
            occurredAt = System.currentTimeMillis()
        )

        // تغيير المبلغ لفاتورة ذات بنود يجب أن يُرفض حتى لا ينفصل عن البنود والمخزون.
        var rejected: LedgerException? = null
        try {
            repo.updateDocument(
                id = docId,
                amountMinor = 9_999L,
                occurredAt = System.currentTimeMillis(),
                notes = "محاولة",
                docNumber = "",
                paymentMethod = "CASH"
            )
        } catch (error: LedgerException) {
            rejected = error
        }
        org.junit.Assert.assertTrue(rejected != null)

        // تغيير غير مالي (الملاحظات) يبقى مسموحًا.
        repo.updateDocument(
            id = docId,
            amountMinor = 5_000L,
            occurredAt = System.currentTimeMillis(),
            notes = "ملاحظة آمنة",
            docNumber = "",
            paymentMethod = "CASH"
        )
        assertEquals("ملاحظة آمنة", repo.documents.get(docId)?.notes)
        assertEquals(9_000L, repo.items.get(itemId)?.qtyMilli)
    }
}
