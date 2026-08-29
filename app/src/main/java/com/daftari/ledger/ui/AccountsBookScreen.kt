package com.daftari.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftari.ledger.R
import com.daftari.ledger.data.BookBalanceRow
import com.daftari.ledger.data.BookEntryEntity
import com.daftari.ledger.data.BookPersonEntity
import com.daftari.ledger.data.CurrencyEntity
import com.daftari.ledger.domain.BookAlerts
import com.daftari.ledger.domain.BookDebtAlert
import com.daftari.ledger.domain.BookDebtor
import com.daftari.ledger.domain.BookEntryKind
import com.daftari.ledger.domain.BookSide
import com.daftari.ledger.domain.BookTotals
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.StaffPermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة «دفتر الحسابات»: قائمة الأشخاص، صفحة مستقلة لكل شخص بسجل عملياته،
 * ونافذة تسجيل عملية بسيطة (اسم، تاريخ، مبلغ، تفاصيل، عملة، وزرّا له/عليه).
 */
@Composable
internal fun AccountsBookScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    val book = state.book
    if (book.selectedPersonId != null) {
        BookPersonPage(state, onEvent, padding)
    } else {
        BookPersonsList(state, onEvent, padding)
    }

    book.personEditor?.let { editor ->
        BookPersonDialog(
            state = state,
            existing = editor.id?.let { id -> book.persons.firstOrNull { it.id == id } },
            onDismiss = { onEvent(UiEvent.SetBookPersonEditor(null)) },
            onSave = { draft -> onEvent(UiEvent.SaveBookPerson(draft)) }
        )
    }

    book.entrySheet?.let { sheet ->
        BookEntryDialog(
            state = state,
            sheet = sheet,
            onDismiss = { onEvent(UiEvent.CloseBookEntrySheet) },
            onSave = { draft -> onEvent(UiEvent.SaveBookEntry(draft)) },
            onDelete = { id -> onEvent(UiEvent.DeleteBookEntry(id)) },
            onManageCurrencies = {
                onEvent(UiEvent.CloseBookEntrySheet)
                onEvent(UiEvent.SetBookCurrencyManager(true))
            }
        )
    }

    if (book.currencyManagerOpen) {
        BookCurrencyManager(state, onEvent)
    }
    book.currencyEditor?.let { draft ->
        BookCurrencyDialog(
            draft = draft,
            onDismiss = { onEvent(UiEvent.SetBookCurrencyEditor(null)) },
            onSave = { onEvent(UiEvent.SaveBookCurrency(it)) }
        )
    }
}

// --------------------------------------------------------------------- القائمة

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookPersonsList(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    val book = state.book
    val currencies = book.currencies.associateBy { it.id }
    val balancesByPerson = book.balances.groupBy { it.personId }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val query = book.query.trim()
    var sort by remember { mutableStateOf(BookPersonSort.NAME) }
    val visiblePersons = remember(book.persons, query, sort, balancesByPerson, book.lastActivity) {
        val filtered = if (query.isEmpty()) {
            book.persons
        } else {
            book.persons.filter { it.name.contains(query, true) || it.phone.contains(query, true) }
        }
        when (sort) {
            BookPersonSort.NAME -> filtered.sortedBy { it.name }
            BookPersonSort.NET -> filtered.sortedWith(
                compareByDescending<BookPersonEntity> { person ->
                    balancesByPerson[person.id]?.maxOfOrNull { it.netMinor } ?: 0L
                }.thenBy { it.name }
            )
            BookPersonSort.ACTIVITY -> filtered.sortedWith(
                compareByDescending<BookPersonEntity> { book.lastActivity[it.id] ?: 0L }.thenBy { it.name }
            )
        }
    }
    val canManage = state.can(StaffPermission.MANAGE_ACCOUNTS) || state.can(StaffPermission.RECORD_SALE)
    val personsById = remember(book.persons) { book.persons.associateBy { it.id } }
    val now = remember { System.currentTimeMillis() }
    var alertsExpanded by remember { mutableStateOf(true) }
    // التنبيهات تتّبع البحث الحالي: لا نُنبِّه عن شخص خارج القائمة المعروضة.
    val alerts = remember(book.balances, book.lastActivity, visiblePersons, now) {
        val visibleIds = visiblePersons.map { it.id }.toSet()
        bookDebtAlerts(book.balances, book.lastActivity, now).filter { it.personId in visibleIds }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onEvent(UiEvent.CloseAccountsBook) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.accounts_book_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(stringResource(R.string.accounts_book_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onEvent(UiEvent.SetBookPersonEditor(BookPersonEditor())) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.book_add_person))
                    }
                    OutlinedButton(onClick = { onEvent(UiEvent.SetBookCurrencyManager(true)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.book_currencies_title))
                    }
                }
            }

            if (alerts.isNotEmpty()) {
                item(key = "book-alerts") {
                    BookAlertsSection(
                        alerts = alerts,
                        persons = personsById,
                        currencies = currencies,
                        expanded = alertsExpanded,
                        onToggle = { alertsExpanded = !alertsExpanded },
                        onOpenPerson = { id -> onEvent(UiEvent.SelectBookPerson(id)) }
                    )
                }
            }

            val totalsByCurrency = bookTotalsByCurrency(book.balances)
            if (totalsByCurrency.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.book_totals_title), fontWeight = FontWeight.Bold)
                }
                items(totalsByCurrency, key = { "total-${it.first}" }) { (currencyId, totals) ->
                    BookTotalsCard(totals, currencies[currencyId])
                }
            }

            item {
                OutlinedTextField(
                    book.query,
                    { onEvent(UiEvent.SearchAccountsBook(it)) },
                    label = { Text(stringResource(R.string.book_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(stringResource(R.string.book_sort_title), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookPersonSort.entries.forEach { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { sort = option },
                            label = { Text(stringResource(option.labelRes())) }
                        )
                    }
                }
            }

            if (visiblePersons.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(if (book.persons.isEmpty()) R.string.book_no_persons else R.string.no_matching_results),
                            fontWeight = FontWeight.Bold
                        )
                        if (book.persons.isEmpty() && canManage) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { onEvent(UiEvent.SetBookPersonEditor(BookPersonEditor())) }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.book_add_person))
                            }
                        }
                    }
                }
            }

            itemsIndexed(visiblePersons, key = { _, person -> person.id }) { index, person ->
                BookPersonCard(
                    index = index,
                    person = person,
                    balances = balancesByPerson[person.id].orEmpty(),
                    currencies = currencies,
                    lastActivity = book.lastActivity[person.id],
                    dateFormat = dateFormat,
                    canManage = canManage,
                    onClick = { onEvent(UiEvent.SelectBookPerson(person.id)) },
                    onQuickAdd = { kind -> onEvent(UiEvent.OpenBookEntrySheet(person.id, kind, null)) }
                )
            }
        }

        if (canManage) {
            FloatingActionButton(
                onClick = { onEvent(UiEvent.SetBookPersonEditor(BookPersonEditor())) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.book_add_person),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun BookTotalsCard(totals: BookTotals, currency: CurrencyEntity?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(currencyLabel(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.book_le), style = MaterialTheme.typography.labelMedium)
                    Text(
                        displayBookMoney(totals.creditMinor, currency),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.book_debt), style = MaterialTheme.typography.labelMedium)
                    Text(
                        displayBookMoney(totals.debtMinor, currency),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.book_net), style = MaterialTheme.typography.labelMedium)
                BookNetText(totals.netMinor, currency)
            }
            if (totals.settledMinor > 0L) {
                Text(
                    stringResource(R.string.book_settled_value, displayBookMoney(totals.settledMinor, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BookPersonCard(
    index: Int,
    person: BookPersonEntity,
    balances: List<BookBalanceRow>,
    currencies: Map<Long, CurrencyEntity>,
    lastActivity: Long?,
    dateFormat: SimpleDateFormat,
    canManage: Boolean,
    onClick: () -> Unit,
    onQuickAdd: (BookEntryKind) -> Unit
) {
    AnimatedCard(
        index = index,
        modifier = Modifier.fillMaxWidth().pulseOnClick().clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        person.name.take(1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    if (person.phone.isNotBlank()) {
                        Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (lastActivity == null) {
                            stringResource(R.string.book_no_activity)
                        } else {
                            stringResource(R.string.book_last_activity, dateFormat.format(Date(lastActivity)))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (balances.isEmpty()) {
                        Text(
                            displayBookMoney(0L, currencies.values.firstOrNull()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    balances.forEach { balance ->
                        val currency = currencies[balance.currencyId]
                        Text(
                            stringResource(
                                R.string.book_row_balances,
                                displayBookMoney(balance.creditMinor, currency),
                                displayBookMoney(balance.debtMinor, currency)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BookNetText(balance.netMinor, currency)
                    }
                }
            }

            if (canManage) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BookEntryKind.entries.forEach { kind ->
                        TextButton(
                            onClick = { onQuickAdd(kind) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = bookKindColor(kind))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(kind.labelRes()), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ صفحة الشخص

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookPersonPage(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    val book = state.book
    val personId = book.selectedPersonId ?: return
    val person = book.persons.firstOrNull { it.id == personId } ?: return
    val currencies = book.currencies.associateBy { it.id }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val entriesByCurrency = book.selectedEntries.groupBy { it.currencyId }
    val runningById = remember(book.selectedEntries) {
        book.selectedEntries.groupBy { it.currencyId }.flatMap { (_, rows) ->
            BookLedgerBridge.statement(rows).map { line -> line.entry.sequence to line.runningNetMinor }
        }.toMap()
    }
    var kindFilter by remember(personId) { mutableStateOf<BookEntryKind?>(null) }
    var currencyFilter by remember(personId) { mutableStateOf<Long?>(null) }
    val orderedEntries = remember(book.selectedEntries, kindFilter, currencyFilter) {
        book.selectedEntries
            .filter { kindFilter == null || entryKindOf(it) == kindFilter }
            .filter { currencyFilter == null || it.currencyId == currencyFilter }
            .sortedWith(compareByDescending<BookEntryEntity> { it.occurredAt }.thenByDescending { it.id })
    }
    val filtering = kindFilter != null || currencyFilter != null
    var confirmArchive by remember { mutableStateOf(false) }
    var showShareOptions by remember { mutableStateOf(false) }
    val canManage = state.can(StaffPermission.MANAGE_ACCOUNTS) || state.can(StaffPermission.RECORD_SALE)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onEvent(UiEvent.CloseBookPerson) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (person.phone.isNotBlank()) {
                            Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { onEvent(UiEvent.SetBookPersonEditor(BookPersonEditor(person.id))) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { showShareOptions = true }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share_statement))
                    }
                    IconButton(onClick = { confirmArchive = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_archive))
                    }
                }
                if (person.notes.isNotBlank()) {
                    Text(person.notes, style = MaterialTheme.typography.bodySmall)
                }
            }

            val balances = entriesByCurrency.map { (currencyId, rows) ->
                val totals = BookLedgerBridge.totals(rows)
                Triple(currencyId, totals, rows)
            }
            if (balances.isEmpty()) {
                item { Text(stringResource(R.string.no_operations_yet), style = MaterialTheme.typography.bodyMedium) }
            }
            items(balances, key = { "balance-${it.first}" }) { (currencyId, totals, _) ->
                BookTotalsCard(totals, currencies[currencyId])
            }

            item {
                Text(stringResource(R.string.book_operations_title), fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = kindFilter == null,
                        onClick = { kindFilter = null },
                        label = { Text(stringResource(R.string.book_filter_all)) }
                    )
                    BookEntryKind.entries.forEach { kind ->
                        FilterChip(
                            selected = kindFilter == kind,
                            onClick = { kindFilter = if (kindFilter == kind) null else kind },
                            label = { Text(stringResource(kind.labelRes())) }
                        )
                    }
                }
                if (entriesByCurrency.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        entriesByCurrency.keys.forEach { id ->
                            FilterChip(
                                selected = currencyFilter == id,
                                onClick = { currencyFilter = if (currencyFilter == id) null else id },
                                label = { Text(currencyLabel(currencies[id])) }
                            )
                        }
                    }
                }
                if (filtering) {
                    Text(
                        stringResource(R.string.book_filtered_count, orderedEntries.size, book.selectedEntries.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canManage) {
                    Text(
                        stringResource(R.string.book_quick_add_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BookEntryKind.entries.forEach { kind ->
                            BookKindChoice(
                                kind = kind,
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onSelect = { onEvent(UiEvent.OpenBookEntrySheet(person.id, kind, null)) }
                            )
                        }
                    }
                }
            }

            if (orderedEntries.isEmpty()) {
                item {
                    Text(
                        stringResource(
                            if (filtering) R.string.book_no_matching_operations else R.string.no_operations_yet
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(orderedEntries, key = { it.id }) { entry ->
                BookEntryRow(
                    entry = entry,
                    currency = currencies[entry.currencyId],
                    runningNet = runningById[entry.id],
                    dateFormat = dateFormat,
                    onClick = { onEvent(UiEvent.OpenBookEntrySheet(person.id, null, entry.id)) }
                )
            }
        }

        if (canManage) {
            FloatingActionButton(
                onClick = { onEvent(UiEvent.OpenBookEntrySheet(person.id, null, null)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.book_new_entry),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    if (showShareOptions) {
        AlertDialog(
            onDismissRequest = { showShareOptions = false },
            title = { Text(stringResource(R.string.action_share_statement)) },
            text = {
                Column {
                    TextButton(onClick = {
                        showShareOptions = false
                        onEvent(UiEvent.ShareBookStatementPdf)
                    }) { Text(stringResource(R.string.book_statement_pdf)) }
                    TextButton(onClick = {
                        showShareOptions = false
                        onEvent(UiEvent.ShareBookStatement)
                    }) { Text(stringResource(R.string.book_statement_text)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareOptions = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.archive_confirm_title)) },
            text = { Text(stringResource(R.string.book_archive_person_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    onEvent(UiEvent.ArchiveBookPerson(person.id))
                }) { Text(stringResource(R.string.action_archive)) }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun BookEntryRow(
    entry: BookEntryEntity,
    currency: CurrencyEntity?,
    runningNet: Long?,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val kind = entryKindOf(entry)
    val color = when (entrySideOf(entry)) {
        BookSide.DEBT -> MaterialTheme.colorScheme.error
        BookSide.LE -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth().pulseOnClick().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(kind.labelRes()), color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        entry.opening -> stringResource(R.string.book_opening_entry_label)
                        entry.details.isNotBlank() -> entry.details
                        else -> stringResource(kind.labelRes())
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.book_entry_date, dateFormat.format(Date(entry.occurredAt))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(displayBookMoney(entry.amountMinor, currency), fontWeight = FontWeight.Bold, color = color)
                Text(
                    currencyLabel(currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (runningNet != null) {
                    Text(
                        stringResource(R.string.book_running_balance, displayBookMoney(runningNet, currency)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- نافذة عملية

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BookEntryDialog(
    state: UiState,
    sheet: BookEntrySheet,
    onDismiss: () -> Unit,
    onSave: (BookEntryDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onManageCurrencies: () -> Unit
) {
    val book = state.book
    val person = book.persons.firstOrNull { it.id == sheet.personId }
    val existing = sheet.editEntryId?.let { id -> book.selectedEntries.firstOrNull { it.id == id } }
    val currencies = book.currencies
    var amount by remember {
        mutableStateOf(
            existing?.let { entry ->
                val digits = currencies.firstOrNull { it.id == entry.currencyId }?.fractionDigits ?: 2
                Money(entry.amountMinor, digits).toBigDecimal().toPlainString()
            }.orEmpty()
        )
    }
    var details by remember { mutableStateOf(existing?.details.orEmpty()) }
    var occurredAt by remember { mutableStateOf(existing?.occurredAt ?: System.currentTimeMillis()) }
    var currencyId by remember {
        // عملة العملية المحرَّرة ← العملة المعتادة للشخص ← العملة الافتراضية العامة.
        mutableStateOf(
            existing?.currencyId
                ?: currencies.firstOrNull { it.id == person?.currencyId }?.id
                ?: book.defaultCurrencyId
                ?: currencies.firstOrNull()?.id
        )
    }
    // النوع اختيار قابل للتغيير دائمًا؛ الزر السريع يحدّد الاختيار المبدئي فقط.
    var kind by remember {
        mutableStateOf(existing?.let { entryKindOf(it) } ?: sheet.presetKind ?: BookEntryKind.LE)
    }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val canSave = amount.isNotBlank() && currencyId != null
    // إغلاق نافذة عملية جديدة فيها بيانات مكتوبة يطلب تأكيدًا حتى لا تضيع بالخطأ.
    val dirty = existing == null && (amount.isNotBlank() || details.isNotBlank())
    fun requestDismiss() {
        if (dirty) confirmDiscard = true else onDismiss()
    }

    fun draft(selected: BookEntryKind) = BookEntryDraft(
        id = existing?.id,
        personId = sheet.personId,
        currencyId = currencyId ?: 0L,
        kind = selected,
        amount = amount,
        occurredAt = occurredAt,
        details = details
    )

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = {
            Column {
                Text(stringResource(if (existing == null) R.string.book_new_entry else R.string.book_edit_entry))
                if (person != null) {
                    Text(person.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.book_operation_type), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    BookEntryKind.entries.forEach { option ->
                        BookKindChoice(
                            kind = option,
                            selected = kind == option,
                            modifier = Modifier.weight(1f),
                            onSelect = { kind = option }
                        )
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    details,
                    { details = it },
                    label = { Text(stringResource(R.string.book_details)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.book_currency), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    currencies.forEach { currency ->
                        FilterChip(
                            selected = currency.id == currencyId,
                            onClick = { currencyId = currency.id },
                            label = { Text(currencyChipLabel(currency)) }
                        )
                    }
                    TextButton(onClick = onManageCurrencies) {
                        Text(stringResource(R.string.book_add_currency))
                    }
                }
                TextButton(onClick = { showDate = true }) {
                    Text(stringResource(R.string.book_entry_date, dateFormat.format(Date(occurredAt))))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { onSave(draft(kind)) },
                colors = ButtonDefaults.buttonColors(containerColor = bookKindColor(kind))
            ) { Text(stringResource(R.string.book_save_kind, stringResource(kind.labelRes()))) }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_remove)) }
                }
                TextButton(onClick = { requestDismiss() }) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.book_discard_title)) },
            text = { Text(stringResource(R.string.book_discard_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { occurredAt = combineWithCurrentTime(it) }
                    showDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = dateState) }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.archive_confirm_title)) },
            text = { Text(stringResource(R.string.book_delete_entry_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(existing.id)
                    onDismiss()
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun bookKindColor(kind: BookEntryKind): Color = when (kind) {
    BookEntryKind.LE -> MaterialTheme.colorScheme.primary
    BookEntryKind.DEBT -> MaterialTheme.colorScheme.error
    BookEntryKind.SETTLEMENT -> MaterialTheme.colorScheme.tertiary
}

/**
 * اختيار نوع العملية: له / عليه / تسديد.
 * يُستخدم في نافذة العملية (اختيار قابل للتغيير) وفي صفحة الشخص (أزرار إضافة سريعة).
 */
@Composable
private fun BookKindChoice(
    kind: BookEntryKind,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val label = stringResource(kind.labelRes())
    if (selected) {
        Button(
            onClick = onSelect,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = bookKindColor(kind))
        ) { Text(label) }
    } else if (kind == BookEntryKind.SETTLEMENT) {
        OutlinedButton(onClick = onSelect, modifier = modifier) { Text(label) }
    } else {
        Button(
            onClick = onSelect,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = bookKindColor(kind).copy(alpha = 0.16f),
                contentColor = bookKindColor(kind)
            )
        ) { Text(label) }
    }
}

// --------------------------------------------------------------- نافذة شخص

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookPersonDialog(
    state: UiState,
    existing: BookPersonEntity?,
    onDismiss: () -> Unit,
    onSave: (BookPersonDraft) -> Unit
) {
    val book = state.book
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var currencyId by remember { mutableStateOf(existing?.currencyId) }
    var opening by remember { mutableStateOf("") }
    var openingKind by remember { mutableStateOf(BookEntryKind.DEBT) }
    var openingCurrencyId by remember { mutableStateOf(book.defaultCurrencyId ?: book.currencies.firstOrNull()?.id) }
    val isEdit = existing != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEdit) R.string.book_edit_person else R.string.book_add_person)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_optional)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.book_person_currency), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = currencyId == null,
                        onClick = { currencyId = null },
                        label = { Text(stringResource(R.string.book_currency_follow_default)) }
                    )
                    book.currencies.forEach { currency ->
                        FilterChip(
                            selected = currencyId == currency.id,
                            onClick = { currencyId = currency.id },
                            label = { Text(currencyChipLabel(currency)) }
                        )
                    }
                }
                if (!isEdit) {
                    Text(stringResource(R.string.book_opening_balance), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        opening,
                        { opening = it },
                        label = { Text(stringResource(R.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = openingKind == BookEntryKind.DEBT, onClick = { openingKind = BookEntryKind.DEBT }, label = { Text(stringResource(R.string.book_debt)) })
                        FilterChip(selected = openingKind == BookEntryKind.LE, onClick = { openingKind = BookEntryKind.LE }, label = { Text(stringResource(R.string.book_le)) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        book.currencies.forEach { currency ->
                            FilterChip(
                                selected = currency.id == openingCurrencyId,
                                onClick = { openingCurrencyId = currency.id },
                                label = { Text(currencyChipLabel(currency)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        BookPersonDraft(
                            id = existing?.id,
                            name = name,
                            phone = phone,
                            notes = notes,
                            currencyId = currencyId,
                            openingAmount = opening,
                            openingKind = openingKind,
                            openingCurrencyId = openingCurrencyId
                        )
                    )
                    onDismiss()
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// --------------------------------------------------------------- العملات

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCurrencyManager(state: UiState, onEvent: (UiEvent) -> Unit) {
    val book = state.book
    AlertDialog(
        onDismissRequest = { onEvent(UiEvent.SetBookCurrencyManager(false)) },
        title = { Text(stringResource(R.string.book_currencies_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.book_currencies_hint), style = MaterialTheme.typography.bodySmall)
                book.currencies.forEach { currency ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(currency.name, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(
                                    R.string.book_currency_value,
                                    currencyChipLabel(currency),
                                    currency.fractionDigits
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currency.id == book.defaultCurrencyId) {
                            Text(
                                stringResource(R.string.book_currency_default),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = { onEvent(UiEvent.SetDefaultBookCurrency(currency.id)) }) {
                                Text(stringResource(R.string.book_set_default))
                            }
                        }
                        TextButton(onClick = {
                            onEvent(
                                UiEvent.SetBookCurrencyEditor(
                                    BookCurrencyDraft(
                                        id = currency.id,
                                        name = currency.name,
                                        symbol = currency.symbol,
                                        fractionDigits = currency.fractionDigits,
                                        code = currency.code
                                    )
                                )
                            )
                        }) { Text(stringResource(R.string.action_edit)) }
                        TextButton(onClick = { onEvent(UiEvent.ArchiveBookCurrency(currency.id)) }) {
                            Text(stringResource(R.string.action_archive))
                        }
                    }
                }
                Button(onClick = { onEvent(UiEvent.SetBookCurrencyEditor(BookCurrencyDraft())) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.book_add_currency))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(UiEvent.SetBookCurrencyManager(false)) }) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun BookCurrencyDialog(draft: BookCurrencyDraft, onDismiss: () -> Unit, onSave: (BookCurrencyDraft) -> Unit) {
    var name by remember { mutableStateOf(draft.name) }
    var symbol by remember { mutableStateOf(draft.symbol) }
    var code by remember { mutableStateOf(draft.code) }
    var digits by remember { mutableStateOf(draft.fractionDigits) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.id == null) R.string.book_add_currency else R.string.book_edit_currency)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.book_currency_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(symbol, { symbol = it }, label = { Text(stringResource(R.string.book_currency_symbol)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (draft.id == null) {
                    OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.book_currency_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.book_currency_digits), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FRACTION_DIGIT_OPTIONS.forEach { option ->
                        FilterChip(selected = digits == option, onClick = { digits = option }, label = { Text(option.toString()) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(draft.copy(name = name, symbol = symbol, code = code, fractionDigits = digits))
                    onDismiss()
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// --------------------------------------------------------------- مساعدات

@Composable
private fun BookNetText(netMinor: Long, currency: CurrencyEntity?) {
    val color = when {
        netMinor > 0L -> MaterialTheme.colorScheme.error
        netMinor < 0L -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        netMinor > 0L -> stringResource(R.string.book_net_owed_by_him, displayBookMoney(netMinor, currency))
        netMinor < 0L -> stringResource(R.string.book_net_owed_to_him, displayBookMoney(-netMinor, currency))
        else -> stringResource(R.string.book_net_settled)
    }
    Text(label, fontWeight = FontWeight.Bold, color = color)
}

private fun bookTotalsByCurrency(balances: List<BookBalanceRow>): List<Pair<Long, BookTotals>> =
    balances.groupBy { it.currencyId }.map { (currencyId, rows) ->
        currencyId to BookTotals(
            creditMinor = rows.sumOf { it.creditMinor },
            debtMinor = rows.sumOf { it.debtMinor },
            settledMinor = rows.sumOf { it.settledMinor }
        )
    }.sortedBy { it.first }

/** ترتيب قائمة الأشخاص في الواجهة فقط؛ لا يغيّر التخزين ولا الحساب. */
private enum class BookPersonSort { NAME, NET, ACTIVITY }

private fun BookPersonSort.labelRes(): Int = when (this) {
    BookPersonSort.NAME -> R.string.book_sort_name
    BookPersonSort.NET -> R.string.book_sort_net
    BookPersonSort.ACTIVITY -> R.string.book_sort_activity
}

private fun currencyLabel(currency: CurrencyEntity?): String =
    currency?.symbol?.trim()?.ifEmpty { currency.name } ?: currency?.name.orEmpty()

private fun currencyChipLabel(currency: CurrencyEntity): String =
    if (currency.symbol.isBlank()) currency.name else "${currency.name} ${currency.symbol}"

private fun entryKindOf(entry: BookEntryEntity): BookEntryKind =
    runCatching { BookEntryKind.valueOf(entry.kind) }.getOrDefault(BookEntryKind.DEBT)

private fun entrySideOf(entry: BookEntryEntity): BookSide =
    runCatching { BookSide.valueOf(entry.side) }.getOrDefault(BookSide.DEBT)

private val FRACTION_DIGIT_OPTIONS = listOf(0, 2, 3)

// ------------------------------------------------------------- تنبيهات الديون

/** كل الديون المتوقفة (عليه + بلا حركة منذ [BookAlerts.STALE_AFTER_DAYS] يومًا). */
private fun bookDebtAlerts(
    balances: List<BookBalanceRow>,
    lastActivity: Map<Long, Long>,
    now: Long
): List<BookDebtAlert> = BookAlerts.staleDebts(
    balances.map { balance ->
        BookDebtor(
            personId = balance.personId,
            currencyId = balance.currencyId,
            netMinor = balance.netMinor,
            lastActivityAt = lastActivity[balance.personId]
        )
    },
    now
)

/**
 * قسم تنبيهات الديون: يظهر فقط عندما يوجد ما يستحق المتابعة، ويمكن طيُّه.
 * الضغط على أي سطر يفتح حساب ذلك الشخص.
 */
@Composable
private fun BookAlertsSection(
    alerts: List<BookDebtAlert>,
    persons: Map<Long, BookPersonEntity>,
    currencies: Map<Long, CurrencyEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenPerson: (Long) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.book_alerts_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.book_alerts_count,
                            alerts.size,
                            BookAlerts.STALE_AFTER_DAYS
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(stringResource(if (expanded) R.string.book_alerts_hide else R.string.book_alerts_show))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                alerts.forEach { alert ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPerson(alert.personId) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                persons[alert.personId]?.name.orEmpty(),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.book_alert_idle_days, alert.idleDays),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                R.string.book_net_owed_by_him,
                                displayBookMoney(alert.netMinor, currencies[alert.currencyId])
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
