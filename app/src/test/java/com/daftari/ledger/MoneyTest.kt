package com.daftari.ledger

import com.daftari.ledger.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parseAndAdd() {
        val a = Money.fromMajor("1000")!!
        val b = Money.fromMajor("400")!!
        assertEquals(60000, (a - b).minor) // remaining after 400 of 1000 in cents? 1000.00 - 400.00 = 600.00 = 60000 minor
        val sale = Money.fromMajor("1000")!!
        val pay = Money.fromMajor("400")!!
        assertEquals(Money.fromMajor("600")!!.minor, (sale - pay).minor)
    }
    @Test fun expenseScenario() {
        // sale 1000 credit, collect 400, remain 600; expense 100 does not change AR
        val ar = Money.fromMajor("1000")!! - Money.fromMajor("400")!!
        assertEquals(60000, ar.minor)
        val profitEst = Money.fromMajor("1000")!! - Money.fromMajor("100")!!
        assertEquals(90000, profitEst.minor)
    }
    @Test fun supplier() {
        val due = Money.fromMajor("2000")!! - Money.fromMajor("500")!!
        assertEquals(150000, due.minor)
    }
    @Test fun rejectEmpty() {
        assertNull(Money.fromMajor(""))
        assertNull(Money.fromMajor("abc"))
    }
    @Test fun noFloat() {
        val x = Money.fromMajor("0.1")!!
        val y = Money.fromMajor("0.2")!!
        assertEquals(Money.fromMajor("0.3")!!.minor, (x + y).minor)
    }
}
