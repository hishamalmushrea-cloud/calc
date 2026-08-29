package com.daftari.ledger.ui

import com.daftari.ledger.data.BookBalanceRow
import com.daftari.ledger.data.BookEntryEntity
import com.daftari.ledger.data.BookPersonEntity
import com.daftari.ledger.data.CurrencyEntity
import com.daftari.ledger.domain.BookEntryKind

/**
 * حالة «دفتر الحسابات»: أشخاص وعملياتهم بعملات يختارها المستخدم.
 * الأرصدة لا تُخزَّن؛ تُجمع من العمليات لكل (شخص، عملة).
 */
data class AccountsBookUiState(
    val screenOpen: Boolean = false,
    val persons: List<BookPersonEntity> = emptyList(),
    val balances: List<BookBalanceRow> = emptyList(),
    val currencies: List<CurrencyEntity> = emptyList(),
    val defaultCurrencyId: Long? = null,
    val lastActivity: Map<Long, Long> = emptyMap(),
    val query: String = "",
    val selectedPersonId: Long? = null,
    val selectedEntries: List<BookEntryEntity> = emptyList(),
    val entrySheet: BookEntrySheet? = null,
    val personEditor: BookPersonEditor? = null,
    val currencyManagerOpen: Boolean = false,
    val currencyEditor: BookCurrencyDraft? = null,
    /** آخر عملية حُذفت، لعرض «تراجع» في شريط الرسائل. */
    val undoEntryId: Long? = null
)

/** نافذة تسجيل عملية: لشخص محدد، ونوع مُختار مسبقًا إن فُتحت من زر سريع. */
data class BookEntrySheet(
    val personId: Long,
    val presetKind: BookEntryKind? = null,
    val editEntryId: Long? = null
)

/** نافذة إضافة/تعديل شخص؛ تُفتح للإضافة عندما يكون [id] = null. */
data class BookPersonEditor(
    val id: Long? = null
)

data class BookPersonDraft(
    val id: Long? = null,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val openingAmount: String = "",
    val openingKind: BookEntryKind = BookEntryKind.DEBT,
    val openingCurrencyId: Long? = null
)

data class BookEntryDraft(
    val id: Long? = null,
    val personId: Long,
    val currencyId: Long,
    val kind: BookEntryKind,
    val amount: String,
    val occurredAt: Long,
    val details: String
)

data class BookCurrencyDraft(
    val id: Long? = null,
    val name: String = "",
    val symbol: String = "",
    val fractionDigits: Int = 2,
    val code: String = ""
)
