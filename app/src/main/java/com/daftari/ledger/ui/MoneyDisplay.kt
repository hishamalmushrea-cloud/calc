package com.daftari.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.daftari.ledger.data.CurrencyEntity
import com.daftari.ledger.domain.BookMoneyFormatter
import com.daftari.ledger.domain.Money
import java.util.Locale

data class MoneyDisplaySettings(
    val currencyCode: String = "SAR",
    val fractionDigits: Int = 2,
    val latinDigits: Boolean = true,
    val hideBalances: Boolean = false
)

val LocalMoneyDisplay = staticCompositionLocalOf { MoneyDisplaySettings() }

@Composable
internal fun displayMoney(minor: Long): String {
    val settings = LocalMoneyDisplay.current
    if (settings.hideBalances) return "••••"
    val locale = if (settings.latinDigits) Locale.US else LocalConfiguration.current.locales[0]
    return Money(minor, settings.fractionDigits).format(locale, settings.currencyCode, includeCurrency = true)
}

internal fun UiState.displayMoney(minor: Long, locale: Locale): String {
    if (hideBalances) return "••••"
    val effectiveLocale = if (latinDigits) Locale.US else locale
    val shop = shop
    return Money(minor, shop?.fractionDigits ?: 2).format(
        locale = effectiveLocale,
        currencyCode = shop?.currencyCode,
        includeCurrency = true
    )
}

/**
 * تنسيق مبلغ من دفتر الحسابات بعملته الخاصة (قد تكون عملة أنشأها المستخدم بلا رمز).
 * يحترم نفس إعدادات الخصوصية والأرقام اللاتينية المستخدمة في بقية التطبيق.
 */
@Composable
internal fun displayBookMoney(minor: Long, currency: CurrencyEntity?): String {
    val settings = LocalMoneyDisplay.current
    if (settings.hideBalances) return "••••"
    val locale = if (settings.latinDigits) Locale.US else LocalConfiguration.current.locales[0]
    return BookMoneyFormatter.format(
        minor = minor,
        fractionDigits = currency?.fractionDigits ?: 2,
        symbol = currency?.symbol.orEmpty(),
        latinDigits = settings.latinDigits,
        locale = locale
    )
}
