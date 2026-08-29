package com.daftari.ledger.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.daftari.ledger.R
import com.daftari.ledger.data.PartyEntity
import com.daftari.ledger.domain.DocType
import com.daftari.ledger.domain.StaffPermission
import com.daftari.ledger.security.AppLock
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftariRoot(
    state: UiState,
    viewModel: MainViewModel,
    activity: FragmentActivity? = null,
    initialTab: Int = 0,
    initialQuickSale: Boolean = false,
    initialOpenBook: Boolean = false
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    var addType by remember { mutableStateOf<DocType?>(null) }
    var quickParty by remember { mutableStateOf<PartyEntity?>(null) }
    var quickType by remember { mutableStateOf<DocType?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    val undoLabel = stringResource(R.string.action_undo)
    val onEvent = viewModel::onEvent
    val useNavigationRail = LocalConfiguration.current.screenWidthDp >= 600
    LaunchedEffect(initialQuickSale) {
        if (initialQuickSale) addType = DocType.SALE
    }
    // ودجت «دفتر الحسابات» يفتح الدفتر مباشرة دون المرور بالتبويبات.
    LaunchedEffect(initialOpenBook) {
        if (initialOpenBook) onEvent(UiEvent.OpenAccountsBook)
    }
    val secondaryOpen = state.book.screenOpen || state.inventory.screenOpen ||
        state.googleBackup.screenOpen || state.employees.screenOpen
    /** اختيار تبويب يُغلق أولًا أي شاشة ثانوية حتى يظهر التبويب المختار فعلًا. */
    val goToTab: (Int) -> Unit = { index ->
        tab = index
        if (secondaryOpen) onEvent(UiEvent.CloseSecondaryScreens)
    }

    val scope = rememberCoroutineScope()
    val exitHint = stringResource(R.string.press_back_again_to_exit)
    var lastBackAt by remember { mutableLongStateOf(0L) }

    // زر الرجوع يرجع خطوة واحدة داخل التطبيق بدل الخروج منه: نافذة مفتوحة ← صفحة الشخص ←
    // الشاشة الثانوية ← اللوحة الرئيسية، ثم ضغطة ثانية خلال ثانيتين للخروج فعلًا.
    // ومع قفل الـ PIN لا يفعل شيئًا إطلاقًا.
    BackHandler {
        if (!state.locked) {
            when {
                state.book.currencyEditor != null -> onEvent(UiEvent.SetBookCurrencyEditor(null))
                state.book.currencyManagerOpen -> onEvent(UiEvent.SetBookCurrencyManager(false))
                state.book.entrySheet != null -> onEvent(UiEvent.CloseBookEntrySheet)
                state.book.personEditor != null -> onEvent(UiEvent.SetBookPersonEditor(null))
                state.book.selectedPersonId != null -> onEvent(UiEvent.CloseBookPerson)
                state.book.screenOpen -> onEvent(UiEvent.CloseAccountsBook)
                state.inventory.invoiceOpen -> onEvent(UiEvent.SetInvoiceSheet(false))
                state.inventory.screenOpen -> onEvent(UiEvent.CloseInventory)
                state.googleBackup.screenOpen -> onEvent(UiEvent.CloseGoogleBackup)
                state.employees.selectedEmployee != null -> onEvent(UiEvent.CloseEmployeeDetail)
                state.employees.screenOpen -> onEvent(UiEvent.CloseEmployees)
                quickParty != null -> {
                    quickParty = null
                    quickType = null
                }
                addType != null -> addType = null
                state.selectedParty != null -> onEvent(UiEvent.ClosePartyDialog)
                state.employees.switcherOpen -> onEvent(UiEvent.SetEmployeeSwitcher(false))
                tab != 0 -> tab = 0
                else -> {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastBackAt < EXIT_WINDOW_MS) {
                        activity?.finish()
                    } else {
                        lastBackAt = now
                        scope.launch { snackbar.showSnackbar(exitHint) }
                    }
                }
            }
        }
    }
    val moneySettings = MoneyDisplaySettings(
        currencyCode = state.shop?.currencyCode ?: "SAR",
        fractionDigits = state.shop?.fractionDigits ?: 2,
        latinDigits = state.latinDigits,
        hideBalances = state.hideBalances
    )

    CompositionLocalProvider(LocalMoneyDisplay provides moneySettings) {
    LaunchedEffect(message) {
        message?.let {
            val result = snackbar.showSnackbar(
                message = it,
                actionLabel = if (state.undoDocumentId != null || state.book.undoEntryId != null) undoLabel else null,
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (state.book.undoEntryId != null) {
                    onEvent(UiEvent.UndoDeleteBookEntry)
                } else {
                    onEvent(UiEvent.UndoDeleteDocument)
                }
            }
            onEvent(UiEvent.ConsumeMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.shop?.name ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (state.employees.enabled) {
                        TextButton(onClick = { onEvent(UiEvent.SetEmployeeSwitcher(true)) }) {
                            Text(state.employees.currentEmployee?.name ?: stringResource(R.string.owner_mode))
                        }
                    }
                    if (state.agingAlert > 0) {
                        BadgedBox(badge = { Badge { Text(state.agingAlert.toString()) } }) {
                            IconButton(onClick = { goToTab(REPORTS_TAB) }) {
                                Icon(Icons.Default.Notifications, stringResource(R.string.notifications))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab <= REPORTS_TAB && tab != SALES_TAB && !secondaryOpen) {
                FloatingActionButton(
                    onClick = { addType = DocType.SALE },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        bottomBar = {
            if (!useNavigationRail) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val items = listOf(
                    stringResource(R.string.nav_dashboard) to Icons.Default.Home,
                    stringResource(R.string.nav_accounts) to Icons.Default.People,
                    stringResource(R.string.nav_documents) to Icons.AutoMirrored.Filled.ReceiptLong,
                    stringResource(R.string.nav_sales) to Icons.Default.PointOfSale,
                    stringResource(R.string.nav_reports) to Icons.Default.Assessment,
                    stringResource(R.string.nav_more) to Icons.Default.MoreHoriz
                )
                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { goToTab(index) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(Modifier.padding(top = padding.calculateTopPadding())) {
                    val railItems = listOf(
                        stringResource(R.string.nav_dashboard) to Icons.Default.Home,
                        stringResource(R.string.nav_accounts) to Icons.Default.People,
                        stringResource(R.string.nav_documents) to Icons.AutoMirrored.Filled.ReceiptLong,
                        stringResource(R.string.nav_sales) to Icons.Default.PointOfSale,
                        stringResource(R.string.nav_reports) to Icons.Default.Assessment,
                        stringResource(R.string.nav_more) to Icons.Default.MoreHoriz
                    )
                    railItems.forEachIndexed { index, (label, icon) ->
                        NavigationRailItem(
                            selected = tab == index,
                            onClick = { goToTab(index) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (state.book.screenOpen) {
                    AccountsBookScreen(state, onEvent, padding)
                } else if (state.inventory.screenOpen) {
                    InventoryScreen(state, onEvent, padding)
                } else if (state.googleBackup.screenOpen) {
                    GoogleBackupScreen(state, onEvent, padding)
                } else if (state.employees.screenOpen && (
                        state.can(StaffPermission.MANAGE_EMPLOYEES) || state.can(StaffPermission.VIEW_REPORTS) ||
                            state.employees.selectedEmployee?.id == state.employees.currentEmployee?.id
                    )
                ) {
                    EmployeesScreen(state, onEvent, padding)
                } else if (state.employees.screenOpen) {
                    AccessDenied(padding)
                } else when (tab) {
                    0 -> if (state.can(StaffPermission.VIEW_REPORTS) || state.can(StaffPermission.VIEW_ACCOUNTS)) DashboardScreen(state, onEvent, padding) { addType = it } else AccessDenied(padding)
                    1 -> if (state.can(StaffPermission.VIEW_ACCOUNTS)) PartiesScreen(state, onEvent, padding) else AccessDenied(padding)
                    2 -> if (state.can(StaffPermission.VIEW_ALL_SALES)) DocsScreen(state, onEvent, padding) else AccessDenied(padding)
                    SALES_TAB -> if (
                        state.can(StaffPermission.RECORD_SALE) || state.can(StaffPermission.VIEW_OWN_SALES) || state.can(StaffPermission.VIEW_ALL_SALES)
                    ) SalesLedgerScreen(state, onEvent, padding) else AccessDenied(padding)
                    REPORTS_TAB -> if (state.can(StaffPermission.VIEW_REPORTS)) ReportsScreen(state, onEvent, padding) else AccessDenied(padding)
                    else -> MoreScreen(state, onEvent, padding)
                }
            }
        }
    }

    addType?.let { type -> DocumentSheet(state, onEvent, type, onDismiss = { addType = null }) }
    if (state.selectedParty != null) {
        PartyDetail(
            state,
            onEvent,
            onDismiss = { onEvent(UiEvent.ClosePartyDialog) },
            onQuick = { type ->
                quickParty = state.selectedParty
                quickType = type
                onEvent(UiEvent.ClosePartyDialog)
            }
        )
    }
    val party = quickParty
    val type = quickType
    if (party != null && type != null) {
        DocumentSheet(state, onEvent, type, initialParty = party) {
            quickParty = null
            quickType = null
        }
    }
    if (state.employees.switcherOpen) EmployeeSwitcherDialog(state, onEvent)
    if (state.locked) LockDialog(state, onEvent, activity)
    }
}

@Composable
private fun AccessDenied(padding: androidx.compose.foundation.layout.PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.access_denied), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LockDialog(state: UiState, onEvent: (UiEvent) -> Unit, activity: FragmentActivity?) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.lock_title)) },
        text = {
            Column {
                OutlinedTextField(
                    pin,
                    { pin = it },
                    label = { Text(stringResource(R.string.pin)) },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (state.biometric && activity != null) {
                    TextButton(onClick = {
                        AppLock.prompt(activity, onOk = { onEvent(UiEvent.BiometricUnlocked) }, onFail = {})
                    }) { Text(stringResource(R.string.unlock_biometric)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(UiEvent.Unlock(pin)) }) { Text(stringResource(R.string.unlock)) }
        }
    )
}

private const val SALES_TAB = 3
private const val REPORTS_TAB = 4

private const val EXIT_WINDOW_MS = 2_000L
