package com.daftari.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftari.ledger.R
import com.daftari.ledger.data.AgingRow
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.domain.Money
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun HeroMetric(title: String, minor: Long, positive: Boolean, subtitle: String) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(6.dp))
            AnimatedCounter(
                targetValue = minor,
                format = { Money(it).format() },
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Spacer(Modifier.size(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SplitMetric(title: String, minor: Long, positive: Boolean, modifier: Modifier = Modifier) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(modifier.padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.size(4.dp))
            AnimatedCounter(
                targetValue = minor,
                format = { Money(it).format() },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            )
        }
    }
}

@Composable
internal fun Metric(title: String, minor: Long, positive: Boolean) {
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(Money(minor).format(), fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
internal fun ComparisonCard(current: Long, previous: Long) {
    val difference = current - previous
    val percent = if (previous == 0L) null else (difference * 100) / previous
    val sign = if (difference >= 0) "+" else ""
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.sales_comparison), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.current_previous_values, Money(current).format(), Money(previous).format()),
                fontWeight = FontWeight.Bold
            )
            Text(
                "$sign${Money(difference).format()}${percent?.let { " ($sign$it%)" }.orEmpty()}",
                color = if (difference >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun AgingCard(row: AgingRow) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(row.party.name, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                AgeCell(stringResource(R.string.age_0_30), row.b0)
                AgeCell(stringResource(R.string.age_31_60), row.b31)
                AgeCell(stringResource(R.string.age_61_90), row.b61)
                AgeCell(stringResource(R.string.age_over_90), row.b90, warn = true)
            }
        }
    }
}

@Composable
private fun AgeCell(label: String, minor: Long, warn: Boolean = false) {
    val color = when {
        minor == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        warn -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(Money(minor).format(), fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
    }
}

@Composable
internal fun LateCard(row: LedgerRepository.LateRow, index: Int, format: SimpleDateFormat) {
    val color = if (row.daysLate > 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AnimatedCard(index = index, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).pulseOnClick()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.party.name, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.last_activity, row.lastDate?.let { format.format(Date(it)) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money(row.balanceMinor).format(), fontWeight = FontWeight.Bold)
                Text(
                    pluralStringResource(R.plurals.late_days, row.daysLate, row.daysLate),
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun Section(title: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (expanded) content()
    }
}

@Composable
internal fun partyBalanceColor(party: PartyEntity): Color {
    if (party.cachedBalanceMinor == 0L) return MaterialTheme.colorScheme.onSurfaceVariant
    val inFavor = if (party.kind == "CUSTOMER") party.cachedBalanceMinor > 0 else party.cachedBalanceMinor < 0
    return if (inFavor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
}

@Composable
internal fun documentTypeLabel(type: String): String = stringResource(
    when (type) {
        "SALE" -> R.string.doc_type_sale
        "PURCHASE" -> R.string.doc_type_purchase
        "EXPENSE" -> R.string.doc_type_expense
        "INCOME" -> R.string.doc_type_income
        "COLLECT" -> R.string.doc_type_collect
        "PAY" -> R.string.doc_type_pay
        "TRANSFER" -> R.string.doc_type_transfer
        "OPENING" -> R.string.doc_type_opening
        else -> R.string.doc_type_unknown
    }
)

@Composable
internal fun documentColor(type: String): Color = when (type) {
    "SALE", "COLLECT", "INCOME" -> MaterialTheme.colorScheme.primary
    "PURCHASE", "EXPENSE", "PAY" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun documentIcon(type: String): String = when (type) {
    "SALE" -> "🛒"
    "COLLECT" -> "💰"
    "PURCHASE" -> "📦"
    "EXPENSE" -> "💸"
    "PAY" -> "🏦"
    "INCOME" -> "📈"
    "TRANSFER" -> "🔄"
    "OPENING" -> "📋"
    else -> "📄"
}
