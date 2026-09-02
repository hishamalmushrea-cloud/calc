package com.daftari.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * اختبارات تنسيق مبالغ دفتر الحسابات بعملات المستخدم الخاصة.
 *
 * ملاحظة: لا نختبر أرقام `Locale("ar")` المجردة لأن نظام الترقيم الافتراضي يختلف بين
 * JVM (لاتيني) وAndroid (عربي)؛ نمرّر الامتداد `ar-u-nu-arab` صراحةً لنتيجة حتمية.
 */
class BookMoneyFormatterTest {

    @Test
    fun latinSymbolGoesBeforeNumber() {
        assertEquals("$3,000", BookMoneyFormatter.format(300_000L, 2, "$"))
    }

    @Test
    fun arabicSymbolGoesAfterNumber() {
        assertEquals("250.50 ر.س", BookMoneyFormatter.format(25_050L, 2, "ر.س"))
        assertEquals("1,500 ﷼", BookMoneyFormatter.format(1_500L, 0, "﷼"))
    }

    @Test
    fun currencyWithoutSymbolShowsNumberOnly() {
        assertEquals("2,000", BookMoneyFormatter.format(200_000L, 2, ""))
        assertEquals("2,000", BookMoneyFormatter.format(200_000L, 2, "   "))
    }

    @Test
    fun wholeAmountsHideFractions() {
        assertEquals("$3,000", BookMoneyFormatter.format(300_000L, 2, "$"))
        assertEquals("12.345", BookMoneyFormatter.format(12_345L, 3, ""))
    }

    @Test
    fun signStaysBeforeSymbol() {
        assertEquals("-$3,000", BookMoneyFormatter.format(-300_000L, 2, "$"))
        assertEquals("-250.50 ر.س", BookMoneyFormatter.format(-25_050L, 2, "ر.س"))
    }

    @Test
    fun arabicDigitsWhenRequested() {
        assertEquals(
            "٣٬٠٠٠ ر.س",
            BookMoneyFormatter.format(300_000L, 2, "ر.س", latinDigits = false, locale = Locale.forLanguageTag("ar-u-nu-arab"))
        )
    }

    @Test
    fun fractionDigitsAreClampedToSupportedRange() {
        // نفس الحد المفروض في المستودع على خانات العملة (0..4).
        assertEquals("0.1500", BookMoneyFormatter.format(1_500L, 9, ""))
        assertEquals("1,500", BookMoneyFormatter.format(1_500L, -3, ""))
    }

    @Test
    fun shortLabelFallsBackToName() {
        assertEquals("ر.س", BookMoneyFormatter.shortLabel("ر.س", "سعودي"))
        assertEquals("محلي", BookMoneyFormatter.shortLabel("  ", "محلي"))
    }
}
