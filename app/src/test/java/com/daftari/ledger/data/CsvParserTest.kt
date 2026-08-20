package com.daftari.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {

    @Test fun parsesRowsWithEnglishHeader() {
        val rows = CsvParser.parse("name,kind,amount,type\nAli,CUSTOMER,100.5,SALE")
        assertEquals(1, rows.size)
        assertEquals("Ali", rows[0].name)
        assertEquals("CUSTOMER", rows[0].kind)
        assertEquals("100.5", rows[0].amount)
        assertEquals("SALE", rows[0].type)
        assertEquals(null, rows[0].error)
        assertEquals(2, rows[0].line)
    }

    @Test fun skipsArabicHeader() {
        val rows = CsvParser.parse("الاسم,النوع,المبلغ\nمحمد")
        assertEquals(1, rows.size)
        assertEquals("محمد", rows[0].name)
    }

    @Test fun flagsInvalidAmountAndEmptyName() {
        val rows = CsvParser.parse("name,kind,amount,type\n, ,abc,SALE\nAli,,abc,SALE")
        assertEquals(2, rows.size)
        assertEquals("اسم فارغ", rows[0].error)
        assertEquals("مبلغ غير صالح", rows[1].error)
    }

    @Test fun emptyInput() {
        assertTrue(CsvParser.parse("").isEmpty())
    }

    @Test fun semicolonSeparator() {
        val rows = CsvParser.parse("Ali;CUSTOMER;40;SALE")
        assertEquals(1, rows.size)
        assertEquals("Ali", rows[0].name)
        assertEquals("40", rows[0].amount)
    }
}
