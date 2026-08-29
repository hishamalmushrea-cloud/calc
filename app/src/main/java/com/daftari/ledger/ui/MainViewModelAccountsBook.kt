package com.daftari.ledger.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.daftari.ledger.R
import com.daftari.ledger.data.BookEntryEntity
import com.daftari.ledger.data.BookOpeningBalance
import com.daftari.ledger.data.CurrencyEntity
import com.daftari.ledger.data.LedgerException
import com.daftari.ledger.domain.AccountsBookMath
import com.daftari.ledger.domain.BookEntryCore
import com.daftari.ledger.domain.BookEntryKind
import com.daftari.ledger.domain.BookSide
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.StaffPermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.openAccountsBook() {
    if (!state.value.can(StaffPermission.VIEW_ACCOUNTS)) return
    mutableState.update { it.copy(book = it.book.copy(screenOpen = true)) }
    viewModelScope.launch {
        try {
            book.ensureSeeded()
            val currency = book.defaultCurrency(state.value.shop?.currencyCode)
            mutableState.update { it.copy(book = it.book.copy(defaultCurrencyId = currency?.id)) }
        } catch (error: LedgerException) {
            dynamicError(error)
        }
    }
}

internal fun MainViewModel.closeAccountsBook() {
    cancelBookEntriesJob()
    mutableState.update {
        it.copy(
            book = it.book.copy(
                screenOpen = false,
                selectedPersonId = null,
                selectedEntries = emptyList(),
                entrySheet = null,
                personEditor = null,
                currencyManagerOpen = false,
                currencyEditor = null,
                undoEntryId = null
            )
        )
    }
}

internal fun MainViewModel.searchAccountsBook(query: String) {
    mutableState.update { it.copy(book = it.book.copy(query = query)) }
}

internal fun MainViewModel.selectBookPerson(id: Long) {
    cancelBookEntriesJob()
    mutableState.update { it.copy(book = it.book.copy(selectedPersonId = id, selectedEntries = emptyList())) }
    bookEntriesJob = viewModelScope.launch {
        book.observeEntries(id).collectLatest { rows ->
            mutableState.update { it.copy(book = it.book.copy(selectedEntries = rows)) }
        }
    }
}

internal fun MainViewModel.closeBookPerson() {
    cancelBookEntriesJob()
    mutableState.update {
        it.copy(book = it.book.copy(selectedPersonId = null, selectedEntries = emptyList(), entrySheet = null))
    }
}

private fun MainViewModel.cancelBookEntriesJob() {
    bookEntriesJob?.cancel()
    bookEntriesJob = null
}

internal fun MainViewModel.setBookPersonEditor(editor: BookPersonEditor?) {
    mutableState.update { it.copy(book = it.book.copy(personEditor = editor)) }
}

internal fun MainViewModel.saveBookPerson(draft: BookPersonDraft) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        val actorId = currentActorId()
        val opening = openingBalanceOf(draft)
        if (draft.id == null) {
            book.addPerson(
                shopId = shop.id,
                name = draft.name,
                phone = draft.phone,
                notes = draft.notes,
                currencyId = draft.currencyId,
                opening = opening,
                actorEmployeeId = actorId
            )
        } else {
            book.updatePerson(
                draft.id,
                draft.name,
                draft.phone,
                draft.notes,
                currencyId = draft.currencyId,
                actorEmployeeId = actorId
            )
        }
        mutableState.update {
            it.copy(book = it.book.copy(personEditor = null), message = text(R.string.msg_book_person_saved))
        }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

/** الرصيد الافتتاحي يُسجَّل كعملية عادية حتى يظهر في السجل مثل بقية العمليات. */
private fun MainViewModel.openingBalanceOf(draft: BookPersonDraft): BookOpeningBalance? {
    val raw = draft.openingAmount.trim()
    if (raw.isEmpty()) return null
    val currencyId = draft.openingCurrencyId
        ?: state.value.book.defaultCurrencyId
        ?: throw LedgerException(getApplication<Application>().getString(R.string.msg_book_currency_missing))
    val currency = state.value.book.currencies.firstOrNull { it.id == currencyId }
        ?: throw LedgerException(getApplication<Application>().getString(R.string.msg_book_currency_missing))
    val amount = Money.fromMajor(raw, currency.fractionDigits)
        ?: throw LedgerException(getApplication<Application>().getString(R.string.msg_invalid_amount))
    if (amount.minor <= 0L) throw LedgerException(getApplication<Application>().getString(R.string.msg_invalid_amount))
    return BookOpeningBalance(currencyId = currencyId, kind = draft.openingKind.name, amountMinor = amount.minor)
}

internal fun MainViewModel.archiveBookPerson(id: Long) = viewModelScope.launch {
    try {
        book.archivePerson(id, actorEmployeeId = currentActorId())
        closeBookPerson()
        message(R.string.msg_book_person_archived)
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.openBookEntrySheet(personId: Long, presetKind: BookEntryKind?, editEntryId: Long?) {
    mutableState.update {
        it.copy(book = it.book.copy(entrySheet = BookEntrySheet(personId, presetKind, editEntryId)))
    }
}

internal fun MainViewModel.closeBookEntrySheet() {
    mutableState.update { it.copy(book = it.book.copy(entrySheet = null)) }
}

internal fun MainViewModel.saveBookEntry(draft: BookEntryDraft) = viewModelScope.launch {
    try {
        val currency = state.value.book.currencies.firstOrNull { it.id == draft.currencyId }
            ?: return@launch message(R.string.msg_book_currency_missing)
        val amount = Money.fromMajor(draft.amount, currency.fractionDigits)
            ?: return@launch message(R.string.msg_invalid_amount)
        if (amount.minor <= 0L) return@launch message(R.string.msg_invalid_amount)
        if (draft.id == null) {
            book.addEntry(
                personId = draft.personId,
                currencyId = draft.currencyId,
                kind = draft.kind,
                amountMinor = amount.minor,
                occurredAt = draft.occurredAt,
                details = draft.details,
                actorEmployeeId = currentActorId()
            )
        } else {
            book.updateEntry(
                id = draft.id,
                kind = draft.kind,
                amountMinor = amount.minor,
                occurredAt = draft.occurredAt,
                details = draft.details,
                actorEmployeeId = currentActorId()
            )
        }
        mutableState.update {
            it.copy(book = it.book.copy(entrySheet = null), message = text(R.string.msg_book_entry_saved))
        }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.deleteBookEntry(id: Long) = viewModelScope.launch {
    try {
        book.deleteEntry(id, actorEmployeeId = currentActorId())
        // الحذف أرشفة ناعمة، فنُبقي المعرّف لعرض «تراجع» في شريط الرسائل.
        mutableState.update {
            it.copy(
                message = text(R.string.msg_book_entry_deleted),
                book = it.book.copy(entrySheet = null, undoEntryId = id)
            )
        }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.undoDeleteBookEntry() = viewModelScope.launch {
    val id = state.value.book.undoEntryId ?: return@launch
    try {
        book.restoreEntry(id, actorEmployeeId = currentActorId())
        mutableState.update {
            it.copy(message = text(R.string.msg_book_entry_undone), book = it.book.copy(undoEntryId = null))
        }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.setBookCurrencyManager(open: Boolean) {
    mutableState.update { it.copy(book = it.book.copy(currencyManagerOpen = open, currencyEditor = null)) }
}

internal fun MainViewModel.setBookCurrencyEditor(draft: BookCurrencyDraft?) {
    mutableState.update { it.copy(book = it.book.copy(currencyEditor = draft)) }
}

internal fun MainViewModel.saveBookCurrency(draft: BookCurrencyDraft) = viewModelScope.launch {
    try {
        val actorId = currentActorId()
        if (draft.id == null) {
            book.addCurrency(
                name = draft.name,
                symbol = draft.symbol,
                fractionDigits = draft.fractionDigits,
                code = draft.code,
                actorEmployeeId = actorId
            )
        } else {
            book.updateCurrency(
                id = draft.id,
                name = draft.name,
                symbol = draft.symbol,
                fractionDigits = draft.fractionDigits,
                actorEmployeeId = actorId
            )
        }
        mutableState.update {
            it.copy(book = it.book.copy(currencyEditor = null), message = text(R.string.msg_book_currency_saved))
        }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.archiveBookCurrency(id: Long) = viewModelScope.launch {
    try {
        book.archiveCurrency(id, actorEmployeeId = currentActorId())
        val currency = book.defaultCurrency(state.value.shop?.currencyCode)
        mutableState.update { it.copy(book = it.book.copy(defaultCurrencyId = currency?.id)) }
        message(R.string.msg_book_currency_archived)
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.setDefaultBookCurrency(id: Long) = viewModelScope.launch {
    try {
        book.setDefaultCurrency(id, actorEmployeeId = currentActorId())
        mutableState.update { it.copy(book = it.book.copy(defaultCurrencyId = id)) }
        message(R.string.msg_book_default_currency_saved)
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

/** كشف حساب نصي لكل عمليات الشخص مع الرصيد الجاري لكل عملة. */
/**
 * عنوان الكشف وأسطره. مصدر واحد يستعمله الكشف النصي وكشف PDF حتى لا يختلفا،
 * والأسطر نفسها تُرسم في الـ PDF عبر `PdfReports.writeStatement`.
 */
private fun MainViewModel.bookStatementParts(): Pair<String, List<String>>? {
    val snapshot = state.value
    val personId = snapshot.book.selectedPersonId ?: return null
    val person = snapshot.book.persons.firstOrNull { it.id == personId } ?: return null
    val strings = getApplication<Application>()
    val currencies = snapshot.book.currencies.associateBy { it.id }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val title = strings.getString(R.string.book_statement_title, person.name)
    val lines = buildList {
        snapshot.book.selectedEntries.groupBy { it.currencyId }.forEach { (currencyId, rows) ->
            val currency = currencies[currencyId]
            val totals = BookLedgerBridge.totals(rows)
            add("")
            add(
                currency?.name.orEmpty() + " — " +
                    strings.getString(R.string.book_le) + ": " + plainAmount(totals.creditMinor, currency) +
                    " | " + strings.getString(R.string.book_debt) + ": " + plainAmount(totals.debtMinor, currency) +
                    " | " + strings.getString(R.string.book_net) + ": " + plainAmount(totals.netMinor, currency)
            )
            BookLedgerBridge.statement(rows).asReversed().forEach line@{ line ->
                val entry = rows.firstOrNull { it.id == line.entry.sequence } ?: return@line
                val details = if (entry.details.isNotBlank()) " | " + entry.details else ""
                add(
                    dateFormat.format(Date(entry.occurredAt)) + " | " +
                        strings.getString(line.entry.kind.labelRes()) + " | " +
                        plainAmount(entry.amountMinor, currency) + " | " +
                        plainAmount(line.runningNetMinor, currency) + details
                )
            }
        }
    }
    return title to lines
}

internal fun MainViewModel.shareBookStatement() {
    val parts = bookStatementParts() ?: return
    mutableState.update { it.copy(shareText = (listOf(parts.first) + parts.second).joinToString("\n")) }
}

internal fun MainViewModel.shareBookStatementPdf() = viewModelScope.launch {
    val parts = bookStatementParts() ?: return@launch
    try {
        val file = services.bookStatementPdf(parts.first, parts.second)
        mutableState.update { it.copy(shareFile = file, message = text(R.string.msg_export_ready)) }
    } catch (error: Exception) {
        message(R.string.msg_export_failed, error.message.orEmpty())
    }
}

/** مبلغ نصي للتصدير بلا اعتماد على الواجهة. */
private fun plainAmount(minor: Long, currency: CurrencyEntity?): String {
    val value = Money(minor, currency?.fractionDigits ?: 2).toBigDecimal().toPlainString()
    val symbol = currency?.symbol?.trim().orEmpty()
    return if (symbol.isEmpty()) value else "$value $symbol"
}

internal fun BookEntryKind.labelRes(): Int = when (this) {
    BookEntryKind.LE -> R.string.book_le
    BookEntryKind.DEBT -> R.string.book_debt
    BookEntryKind.SETTLEMENT -> R.string.book_settlement
}

/** جسر بين الكيانات المخزّنة ومنطق الحساب الصرفي (مصدر واحد للحقيقة). */
internal object BookLedgerBridge {
    fun totals(rows: List<BookEntryEntity>) = AccountsBookMath.totals(rows.map { it.toCore() })

    fun statement(rows: List<BookEntryEntity>) = AccountsBookMath.statement(rows.map { it.toCore() })

    private fun BookEntryEntity.toCore() = BookEntryCore(
        kind = runCatching { BookEntryKind.valueOf(kind) }.getOrDefault(BookEntryKind.DEBT),
        side = runCatching { BookSide.valueOf(side) }.getOrDefault(BookSide.DEBT),
        amountMinor = amountMinor,
        occurredAt = occurredAt,
        sequence = id
    )
}
