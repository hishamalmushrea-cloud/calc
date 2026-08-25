package com.daftari.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryMathTest {
    @Test
    fun lineTotalUsesMilliQuantity() {
        assertEquals(15_00L, InventoryMath.lineTotal(qtyMilli = 1_500, unitPriceMinor = 1_000))
        assertEquals(2_500L, InventoryMath.lineTotal(qtyMilli = 1_000, unitPriceMinor = 2_500))
    }

    @Test
    fun invoiceTotalSumsLines() {
        val total = InventoryMath.invoiceTotal(listOf(1_000L to 1_000L, 2_000L to 250L))
        assertEquals(1_500L, total)
    }

    @Test
    fun saleDecreasesTrackedStockAndPurchaseIncreases() {
        assertEquals(-2_000L, InventoryMath.stockDelta("SALE", 2_000, true))
        assertEquals(2_000L, InventoryMath.stockDelta("PURCHASE", 2_000, true))
        assertEquals(0L, InventoryMath.stockDelta("SALE", 2_000, false))
        assertEquals(0L, InventoryMath.stockDelta("COLLECT", 2_000, true))
    }

    @Test
    fun qtyParsingAcceptsDecimals() {
        assertEquals(1_500L, InventoryMath.parseQty("1.5"))
        assertEquals(2_000L, InventoryMath.parseQty("2"))
        assertEquals("1.5", InventoryMath.formatQty(1_500))
        assertNull(InventoryMath.parseQty("abc"))
    }
}
