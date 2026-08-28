package com.daftari.ledger.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * مبلغ مالي بوحدة صغرى (هللة/فلس). ممنوع Float/Double.
 */
data class Money(val minor: Long, val fractionDigits: Int = 2) {
    operator fun plus(other: Money): Money {
        require(fractionDigits == other.fractionDigits)
        return copy(minor = Math.addExact(minor, other.minor))
    }
    operator fun minus(other: Money): Money {
        require(fractionDigits == other.fractionDigits)
        return copy(minor = Math.subtractExact(minor, other.minor))
    }
    operator fun unaryMinus() = copy(minor = Math.negateExact(minor))
    fun isZero() = minor == 0L
    fun abs() = copy(minor = kotlin.math.abs(minor))

    fun toBigDecimal(): BigDecimal =
        BigDecimal.valueOf(minor).movePointLeft(fractionDigits)

    fun format(
        locale: Locale = Locale("ar"),
        currencyCode: String? = null,
        includeCurrency: Boolean = currencyCode != null
    ): String {
        val formatter = if (includeCurrency && currencyCode != null) {
            NumberFormat.getCurrencyInstance(locale).apply {
                val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
                if (currency != null) {
                    this.currency = currency
                    // ضبط الرمز صراحةً: getCurrencyInstance(locale) قد يعرض «$» الافتراضي
                    // حتى بعد تغيير العملة، فيجب استبدال الرمز برمز العملة الصحيح.
                    if (this is java.text.DecimalFormat) {
                        val symbols = decimalFormatSymbols
                        symbols.currencySymbol = currencySymbol(currency, locale)
                        decimalFormatSymbols = symbols
                    }
                }
            }
        } else NumberFormat.getNumberInstance(locale)
        formatter.minimumFractionDigits = if (minor % powerOfTen(fractionDigits) == 0L) 0 else fractionDigits
        formatter.maximumFractionDigits = fractionDigits
        return formatter.format(toBigDecimal())
    }

    /** رمز عرض العملة: الرمز المحلي إن وُجد، وإلا رمز العملة الدولي (وليس رمزًا خاطئًا من Locale آخر). */
    private fun currencySymbol(currency: Currency, locale: Locale): String {
        val local = currency.getSymbol(locale)
        if (local != currency.currencyCode) return local
        return currency.getSymbol(Locale("ar")).takeIf { it != currency.currencyCode }
            ?: currency.getSymbol(Locale.US).takeIf { it != currency.currencyCode }
            ?: currency.currencyCode
    }

    private fun powerOfTen(digits: Int): Long = (1..digits).fold(1L) { value, _ -> value * 10L }

    companion object {
        fun fromMajor(text: String, fractionDigits: Int = 2): Money? {
            val t = text.trim().replace(",", "").replace("،", "")
            if (t.isEmpty()) return null
            return try {
                val bd = BigDecimal(t).setScale(fractionDigits, RoundingMode.HALF_UP)
                Money(bd.movePointRight(fractionDigits).longValueExact(), fractionDigits)
            } catch (_: Exception) {
                null
            }
        }
        val ZERO = Money(0)
    }
}

enum class PartyKind { CUSTOMER, SUPPLIER }

enum class DocType {
    SALE, PURCHASE, EXPENSE, INCOME, COLLECT, PAY, TRANSFER, OPENING, ADJUST, CLOSE
}

enum class AccountType { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }

enum class PaymentMethod { CASH, BANK, CARD, CREDIT, WALLET, OTHER }
