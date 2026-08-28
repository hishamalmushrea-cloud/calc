package com.daftari.ledger.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * تنسيق مبالغ «دفتر الحسابات» بعملات المستخدم الخاصة.
 *
 * خلافًا لـ [Money.format] لا نعتمد على [java.util.Currency] لأن العملات هنا ليست
 * رسمية بالضرورة (مثل «محلي» بلا رمز، أو عملة يسميها المستخدم نفسه)، ولذلك يُمرَّر
 * الرمز وعدد الخانات العشرية صراحةً.
 */
object BookMoneyFormatter {

    /**
     * @param minor المبلغ بالوحدة الصغرى.
     * @param fractionDigits عدد الخانات العشرية للعملة (0..4).
     * @param symbol رمز العملة؛ قد يكون فارغًا (عملة محلية بلا رمز).
     * @param latinDigits true لأرقام لاتينية، false لأرقام اللغة المختارة.
     * @param locale اللغة المستخدمة عند [latinDigits] = false، والافتراضي العربية.
     */
    fun format(
        minor: Long,
        fractionDigits: Int,
        symbol: String,
        latinDigits: Boolean = true,
        locale: Locale = Locale("ar")
    ): String {
        val digits = fractionDigits.coerceIn(0, MAX_FRACTION_DIGITS)
        val effectiveLocale = if (latinDigits) Locale.US else locale
        val negative = minor < 0L
        val value = BigDecimal.valueOf(if (negative) -minor else minor).movePointLeft(digits)
        // كما في Money.format: تُخفى الكسور عندما يكون المبلغ عددًا صحيحًا.
        val wholeNumber = value.stripTrailingZeros().scale() <= 0
        val pattern = if (wholeNumber || digits == 0) {
            WHOLE_PATTERN
        } else {
            WHOLE_PATTERN + "." + "0".repeat(digits)
        }
        val number = DecimalFormat(pattern, DecimalFormatSymbols(effectiveLocale)).format(value)
        val cleanSymbol = symbol.trim()
        val body = when {
            cleanSymbol.isEmpty() -> number
            // الرمز بعد الرقم إن كان بحروف عربية (ر.س، ﷼) وقبله إن كان لاتينيًا ($).
            hasArabicScript(cleanSymbol) -> "$number $cleanSymbol"
            else -> "$cleanSymbol$number"
        }
        // الإشارة قبل الرمز دائمًا: ‎-$3,000 وليس ‎$-3,000.
        return if (negative) "-$body" else body
    }

    /** هل يحتوي الرمز على حروف عربية (بما فيها أشكال العرض)؟ */
    private fun hasArabicScript(symbol: String): Boolean = symbol.any { char ->
        char.code in ARABIC_BLOCK || char.code in ARABIC_PRESENTATION_A || char.code in ARABIC_PRESENTATION_B
    }

    /** رمز مختصر للعرض داخل الأعمدة: الرمز إن وُجد وإلا اسم العملة. */
    fun shortLabel(symbol: String, name: String): String = symbol.trim().ifEmpty { name.trim() }

    private const val WHOLE_PATTERN = "#,##0"
    private const val MAX_FRACTION_DIGITS = 4
    private val ARABIC_BLOCK = 0x0600..0x06FF
    private val ARABIC_PRESENTATION_A = 0xFB50..0xFDFF
    private val ARABIC_PRESENTATION_B = 0xFE70..0xFEFF
}
