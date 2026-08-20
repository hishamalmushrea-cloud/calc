package com.daftari.ledger.data

import com.daftari.ledger.domain.Money

/**
 * محلّل CSV المستورد (اسم، نوع، مبلغ، نوع عملية) — دالة صرفة قابلة للاختبار.
 */
object CsvParser {
    fun parse(text: String): List<CsvPreviewRow> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val start = if (lines.first().contains("name", true) || lines.first().contains("اسم")) 1 else 0
        return lines.drop(start).mapIndexed { i, line ->
            val p = line.split(',', ';', '\t').map { it.trim().trim('"') }
            val name = p.getOrNull(0).orEmpty()
            val kind = p.getOrNull(1).orEmpty().ifBlank { "CUSTOMER" }
            val amount = p.getOrNull(2).orEmpty()
            val type = p.getOrNull(3).orEmpty().ifBlank { "SALE" }
            val err = when {
                name.isBlank() -> "اسم فارغ"
                Money.fromMajor(amount) == null && amount.isNotBlank() -> "مبلغ غير صالح"
                else -> null
            }
            CsvPreviewRow(start + i + 1, name, kind, amount, type, err)
        }
    }
}
