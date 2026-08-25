package com.daftari.ledger.domain

object InventoryMath {
    const val QTY_SCALE = 1_000L

    fun lineTotal(qtyMilli: Long, unitPriceMinor: Long): Long {
        require(qtyMilli > 0) { "أدخل كمية أكبر من صفر" }
        require(unitPriceMinor >= 0) { "سعر الصنف غير صالح" }
        return Math.multiplyExact(qtyMilli, unitPriceMinor) / QTY_SCALE
    }

    fun invoiceTotal(lines: List<Pair<Long, Long>>): Long {
        if (lines.isEmpty()) throw IllegalArgumentException("أضف صنفًا واحدًا على الأقل")
        return lines.fold(0L) { acc, (qty, price) -> Math.addExact(acc, lineTotal(qty, price)) }
    }

    fun stockDelta(documentType: String, qtyMilli: Long, trackStock: Boolean): Long {
        if (!trackStock || qtyMilli == 0L) return 0
        return when (documentType) {
            "SALE" -> -qtyMilli
            "PURCHASE" -> qtyMilli
            else -> 0
        }
    }

    fun parseQty(raw: String): Long? {
        val text = raw.trim().replace(',', '.')
        if (text.isBlank()) return null
        val parts = text.split('.')
        if (parts.size > 2) return null
        val wholeText = parts[0]
        val negative = wholeText.startsWith('-')
        val whole = wholeText.toLongOrNull() ?: return null
        val fracStr = parts.getOrNull(1).orEmpty()
        if (fracStr.length > 3 || fracStr.any { !it.isDigit() }) return null
        val fracValue = if (fracStr.isEmpty()) 0L else fracStr.padEnd(3, '0').toLongOrNull() ?: return null
        val magnitude = kotlin.math.abs(whole) * QTY_SCALE + fracValue
        return if (negative) -magnitude else magnitude
    }

    fun formatQty(qtyMilli: Long): String {
        val sign = if (qtyMilli < 0) "-" else ""
        val abs = kotlin.math.abs(qtyMilli)
        val whole = abs / QTY_SCALE
        val frac = abs % QTY_SCALE
        return if (frac == 0L) "$sign$whole" else "$sign$whole.${frac.toString().padStart(3, '0').trimEnd('0')}"
    }
}
