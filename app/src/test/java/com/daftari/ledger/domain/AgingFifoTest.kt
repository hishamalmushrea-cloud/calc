package com.daftari.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AgingFifoTest {
    private val day = 86_400_000L

    @Test fun allocatesOldestFirst() {
        val now = 1_700_000_000_000L
        val invoices = listOf(
            AgingFifo.Invoice(100, now - 100 * day),
            AgingFifo.Invoice(50, now - 20 * day)
        )
        val b = AgingFifo.allocate(120, invoices, now)
        assertEquals(20L, b.b0)
        assertEquals(0L, b.b31)
        assertEquals(0L, b.b61)
        assertEquals(100L, b.b90)
    }

    @Test fun leftoverGoesToOldestBucket() {
        val now = 1_700_000_000_000L
        val invoices = listOf(AgingFifo.Invoice(30, now - 10 * day))
        val b = AgingFifo.allocate(50, invoices, now)
        assertEquals(30L, b.b0)
        assertEquals(20L, b.b90)
    }

    @Test fun ageBoundaries() {
        val now = 1_700_000_000_000L
        val invoices = listOf(
            AgingFifo.Invoice(10, now - 30 * day),
            AgingFifo.Invoice(10, now - 31 * day),
            AgingFifo.Invoice(10, now - 60 * day),
            AgingFifo.Invoice(10, now - 61 * day),
            AgingFifo.Invoice(10, now - 90 * day),
            AgingFifo.Invoice(10, now - 91 * day)
        )
        val b = AgingFifo.allocate(60, invoices, now)
        assertEquals(10L, b.b0)
        assertEquals(20L, b.b31)
        assertEquals(20L, b.b61)
        assertEquals(10L, b.b90)
    }

    @Test fun zeroBalance() {
        val now = 1_700_000_000_000L
        val invoices = listOf(AgingFifo.Invoice(100, now - 100 * day))
        val b = AgingFifo.allocate(0, invoices, now)
        assertEquals(0L, b.b0)
        assertEquals(0L, b.b31)
        assertEquals(0L, b.b61)
        assertEquals(0L, b.b90)
    }
}
