package com.daftari.ledger

import com.daftari.ledger.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun sarNeverShowsDollarSignEvenWithUsLocale() {
        // عند تفعيل الأرقام اللاتينية يُستخدم Locale.US؛ يجب ألا يظهر رمز الدولار لعملة الريال.
        val formatted = Money(1_234_56).format(java.util.Locale.US, "SAR", includeCurrency = true)
        assertFalse("SAR مع Locale.US يجب ألا يعرض «$»: $formatted", formatted.contains('$'))
    }

    @Test
    fun usdKeepsItsOwnSymbol() {
        val formatted = Money(12_34).format(java.util.Locale.US, "USD", includeCurrency = true)
        assertTrue(formatted.contains('$'))
    }

    @Test
    fun currencyFormatFallsBackToIsoCodeRatherThanWrongSymbol() {
        // أي عملة غير مدعومة محليًا يجب أن تعرض رمز ISO وليس رمز عملة أخرى.
        val formatted = Money(1000).format(java.util.Locale.US, "YER", includeCurrency = true)
        assertFalse(formatted.contains('$'))
        assertTrue(formatted.contains("YER"))
    }
}
