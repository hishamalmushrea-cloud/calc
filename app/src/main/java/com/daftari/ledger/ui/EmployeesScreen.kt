package com.daftari.ledger.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.daftari.ledger.R
import com.daftari.ledger.data.EmployeeEntity
import com.daftari.ledger.data.EmployeePerformanceRow
import com.daftari.ledger.data.EmployeeShiftEntity
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.StaffPermission
import com.daftari.ledger.domain.StaffRoles
import com.daftari.ledger.domain.hasPermission
import java.math.BigDecimal
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmployeesScreen(state: UiState, onEvent: (UiEvent) -> Unit, padding: PaddingValues) {
    state.employees.selectedEmployee?.let {
        EmployeeDetailScreen(state, onEvent, padding, it)
        return
    }
    var showAdd by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf<String?>(null) }
    val employeeState = state.employees
    val canViewReports = state.can(StaffPermission.VIEW_REPORTS)
    val performance = if (canViewReports) employeeState.performance.associateBy { it.employeeId } else emptyMap()
    val filtered = employeeState.employees.filter {
        (query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true)) &&
            (status == null || it.status == status) && (role == null || it.role == role)
    }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onEvent(UiEvent.CloseEmployees) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.employees_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (state.can(StaffPermission.MANAGE_EMPLOYEES)) {
                Button(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.add_employee))
                }
            }
        }
        OutlinedTextField(
            query, { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_employee)) }, singleLine = true
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(status == null, { status = null }, label = { Text(stringResource(R.string.all)) })
            listOf("ACTIVE" to R.string.employee_active, "LEAVE" to R.string.employee_leave, "SUSPENDED" to R.string.employee_suspended, "LEFT" to R.string.employee_left).forEach { (code, label) ->
                FilterChip(status == code, { status = code }, label = { Text(stringResource(label)) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("SELLER" to R.string.role_seller, "MANAGER" to R.string.role_manager, "ACCOUNTANT" to R.string.role_accountant).forEach { (code, label) ->
                FilterChip(role == code, { role = if (role == code) null else code }, label = { Text(stringResource(label)) })
            }
        }
        if (canViewReports) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(SalesBookRange.TODAY, SalesBookRange.YESTERDAY, SalesBookRange.THIS_WEEK, SalesBookRange.LAST_WEEK, SalesBookRange.THIS_MONTH, SalesBookRange.LAST_MONTH).forEach { range ->
                    FilterChip(
                        employeeState.range == range,
                        { onEvent(UiEvent.SetEmployeeRange(range)) },
                        label = { Text(employeeRangeLabel(range)) }
                    )
                }
            }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                val total = employeeState.performance.sumOf { it.salesMinor }
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.employee_report_total, displayMoney(total)), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.employee_report_operations, employeeState.performance.sumOf { it.transactionCount }))
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { employee ->
                EmployeeCard(employee, performance[employee.id]) { onEvent(UiEvent.SelectEmployee(employee.id)) }
            }
        }
    }
    if (showAdd) EmployeeDialog(null, onDismiss = { showAdd = false }) {
        onEvent(UiEvent.AddEmployee(it)); showAdd = false
    }
}

@Composable
private fun EmployeeCard(employee: EmployeeEntity, performance: EmployeePerformanceRow?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(employee.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(employee.jobTitle.ifBlank { roleLabel(employee.role) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(statusLabel(employee.status), style = MaterialTheme.typography.labelMedium)
            }
            performance?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.employee_sales_value, displayMoney(it.salesMinor)))
                    Text(stringResource(R.string.employee_operations_value, it.transactionCount))
                }
            }
            TextButton(onClick = onClick) { Text(stringResource(R.string.view_details)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmployeeDetailScreen(
    state: UiState,
    onEvent: (UiEvent) -> Unit,
    padding: PaddingValues,
    employee: EmployeeEntity
) {
    val details = state.employees
    var editing by remember { mutableStateOf(false) }
    var statusDialog by remember { mutableStateOf(false) }
    var openShift by remember { mutableStateOf(false) }
    var closeShift by remember { mutableStateOf<EmployeeShiftEntity?>(null) }
    val canViewSales = state.can(StaffPermission.VIEW_REPORTS) ||
        (details.currentEmployee?.id == employee.id && state.can(StaffPermission.VIEW_OWN_SALES))
    val monthSales = details.selectedStats.month?.salesMinor ?: 0
    val commission = monthSales * employee.commissionBasisPoints / 10_000
    val targetRatio = if (employee.monthlyTargetMinor > 0) (monthSales.toFloat() / employee.monthlyTargetMinor).coerceIn(0f, 1f) else 0f
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onEvent(UiEvent.CloseEmployeeDetail) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Column(Modifier.weight(1f)) {
                    Text(employee.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(employee.jobTitle.ifBlank { roleLabel(employee.role) })
                }
                if (state.can(StaffPermission.MANAGE_EMPLOYEES)) {
                    TextButton(onClick = { editing = true }) { Text(stringResource(R.string.action_edit)) }
                }
            }
            if (canViewSales) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmployeeStatCard(stringResource(R.string.period_today), details.selectedStats.today?.salesMinor ?: 0, Modifier.weight(1f))
                EmployeeStatCard(stringResource(R.string.this_week), details.selectedStats.week?.salesMinor ?: 0, Modifier.weight(1f))
                EmployeeStatCard(stringResource(R.string.this_month), monthSales, Modifier.weight(1f))
            }
            Text(stringResource(R.string.employee_total_operations, details.selectedStats.month?.transactionCount ?: 0))
            details.selectedStats.month?.let { month ->
                Text(stringResource(R.string.employee_average_sale, displayMoney(month.averageSaleMinor)))
                Text(stringResource(R.string.payment_cash) + ": " + displayMoney(month.cashMinor))
                Text(stringResource(R.string.payment_bank) + ": " + displayMoney(month.bankMinor))
                Text(stringResource(R.string.payment_card) + ": " + displayMoney(month.cardMinor))
                Text(stringResource(R.string.payment_credit) + ": " + displayMoney(month.creditMinor))
            }
            if (employee.monthlyTargetMinor > 0) {
                Text(stringResource(R.string.employee_target_progress, displayMoney(monthSales), displayMoney(employee.monthlyTargetMinor)))
                LinearProgressIndicator(progress = { targetRatio }, modifier = Modifier.fillMaxWidth())
            }
            if (employee.commissionBasisPoints > 0 && state.can(StaffPermission.VIEW_PAYROLL)) {
                Text(stringResource(R.string.employee_estimated_commission, displayMoney(commission)))
            }
            }
            if (state.can(StaffPermission.VIEW_PAYROLL)) {
                Text(stringResource(R.string.employee_base_salary, displayMoney(employee.baseSalaryMinor)))
            }
            if (state.can(StaffPermission.MANAGE_EMPLOYEES)) {
                FlowRow {
                    TextButton(onClick = { statusDialog = true }) { Text(stringResource(R.string.change_status)) }
                    state.shops.forEach { shop ->
                        val active = details.selectedShopLinks.any { it.shopId == shop.id && it.active }
                        FilterChip(
                            selected = active,
                            onClick = { onEvent(UiEvent.AssignEmployeeShop(employee.id, shop.id, !active)) },
                            label = { Text(shop.name) }
                        )
                    }
                }
            }
            if (state.can(StaffPermission.MANAGE_SHIFTS)) {
                Button(onClick = { openShift = true }) { Text(stringResource(R.string.open_shift)) }
            }
            if (canViewSales) Text(stringResource(R.string.employee_sales_operations), fontWeight = FontWeight.Bold)
        }
        if (canViewSales) {
            items(details.selectedSales, key = { it.id }) { sale ->
                SalesBookEntryRow(sale, state, readOnly = true)
            }
        }
        item {
            if (state.can(StaffPermission.MANAGE_SHIFTS)) {
            Text(stringResource(R.string.employee_shifts), fontWeight = FontWeight.Bold)
            details.shifts.filter { it.employeeId == employee.id }.forEach { shift ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(shift.label.ifBlank { DateFormat.getDateTimeInstance().format(Date(shift.openedAt)) }, fontWeight = FontWeight.Bold)
                        Text(statusLabel(shift.status))
                        if (shift.status == "OPEN" && state.can(StaffPermission.MANAGE_SHIFTS)) {
                            TextButton(onClick = { closeShift = shift }) { Text(stringResource(R.string.close_shift)) }
                        } else if (shift.actualCashMinor != null) {
                            Text(stringResource(R.string.shift_expected_actual, displayMoney(shift.expectedCashMinor), displayMoney(shift.actualCashMinor), displayMoney(shift.differenceMinor ?: 0)))
                        }
                    }
                }
            }
            }
            if (state.can(StaffPermission.VIEW_AUDIT)) {
                Text(stringResource(R.string.employee_activity), fontWeight = FontWeight.Bold)
                details.selectedActivity.take(50).forEach { log ->
                    Text(
                        "${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(log.at))} — ${log.action} ${log.detail}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (log.beforeValue.isNotBlank() || log.afterValue.isNotBlank()) {
                        Text("${log.beforeValue} → ${log.afterValue}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (editing) EmployeeDialog(employee, { editing = false }) { onEvent(UiEvent.UpdateEmployee(employee.id, it)); editing = false }
    if (statusDialog) EmployeeStatusDialog(employee, onEvent) { statusDialog = false }
    if (openShift) OpenShiftDialog(employee, onEvent) { openShift = false }
    closeShift?.let { CloseShiftDialog(it, onEvent) { closeShift = null } }
}

@Composable
private fun EmployeeStatCard(title: String, value: Long, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(10.dp)) { Text(title, style = MaterialTheme.typography.labelSmall); Text(displayMoney(value), fontWeight = FontWeight.Bold) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmployeeDialog(employee: EmployeeEntity?, onDismiss: () -> Unit, onSave: (EmployeeDraft) -> Unit) {
    var name by remember { mutableStateOf(employee?.name.orEmpty()) }
    var phone by remember { mutableStateOf(employee?.phone.orEmpty()) }
    var job by remember { mutableStateOf(employee?.jobTitle.orEmpty()) }
    var role by remember { mutableStateOf(employee?.role ?: StaffRoles.SELLER) }
    var permissions by remember { mutableStateOf(employee?.permissions ?: StaffRoles.defaultPermissions(role)) }
    var salary by remember { mutableStateOf(employee?.let { Money(it.baseSalaryMinor).toBigDecimal().toPlainString() }.orEmpty()) }
    var commission by remember { mutableStateOf(employee?.let { BigDecimal(it.commissionBasisPoints).movePointLeft(2).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var target by remember { mutableStateOf(employee?.let { Money(it.monthlyTargetMinor).toBigDecimal().toPlainString() }.orEmpty()) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false } }
    var startDate by remember { mutableStateOf(dateFormat.format(Date(employee?.startDate ?: System.currentTimeMillis()))) }
    var notes by remember { mutableStateOf(employee?.notes.orEmpty()) }
    var username by remember { mutableStateOf(employee?.username.orEmpty()) }
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (employee == null) R.string.add_employee else R.string.edit_employee)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.employee_name)) })
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone_optional)) })
                OutlinedTextField(job, { job = it }, label = { Text(stringResource(R.string.job_title)) })
                OutlinedTextField(startDate, { startDate = it }, label = { Text(stringResource(R.string.employee_start_date)) }, singleLine = true)
                Text(stringResource(R.string.employee_role), fontWeight = FontWeight.Bold)
                FlowRow {
                    listOf(StaffRoles.SELLER to R.string.role_seller, StaffRoles.MANAGER to R.string.role_manager, StaffRoles.ACCOUNTANT to R.string.role_accountant).forEach { (code, label) ->
                        FilterChip(role == code, {
                            role = code; permissions = StaffRoles.defaultPermissions(code)
                        }, label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(R.string.permissions), fontWeight = FontWeight.Bold)
                FlowRow {
                    StaffPermission.entries.forEach { permission ->
                        FilterChip(
                            selected = permissions.hasPermission(permission),
                            onClick = { permissions = permissions xor permission.mask },
                            label = { Text(permissionLabel(permission)) }
                        )
                    }
                }
                OutlinedTextField(salary, { salary = it }, label = { Text(stringResource(R.string.base_salary_optional)) })
                OutlinedTextField(commission, { commission = it }, label = { Text(stringResource(R.string.commission_optional)) })
                OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.monthly_target_optional)) })
                OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.username_optional)) })
                OutlinedTextField(pin, { pin = it }, label = { Text(stringResource(R.string.employee_pin_optional)) }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_optional)) })
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedStart = runCatching { dateFormat.parse(startDate)?.time }.getOrNull()
                    ?: employee?.startDate ?: System.currentTimeMillis()
                onSave(EmployeeDraft(name, phone, job, role, permissions, salary, commission, target, parsedStart, notes, username, pin))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun EmployeeStatusDialog(employee: EmployeeEntity, onEvent: (UiEvent) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_status)) },
        text = { Column { listOf("ACTIVE" to R.string.employee_active, "LEAVE" to R.string.employee_leave, "SUSPENDED" to R.string.employee_suspended, "LEFT" to R.string.employee_left).forEach { (status, label) -> TextButton(onClick = { onEvent(UiEvent.ChangeEmployeeStatus(employee.id, status)); onDismiss() }) { Text(stringResource(label)) } } } },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun OpenShiftDialog(employee: EmployeeEntity, onEvent: (UiEvent) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }; var cash by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.open_shift)) }, text = { Column { OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.shift_label)) }); OutlinedTextField(cash, { cash = it }, label = { Text(stringResource(R.string.opening_cash)) }) } }, confirmButton = { Button(onClick = { onEvent(UiEvent.OpenEmployeeShift(employee.id, label, cash)); onDismiss() }) { Text(stringResource(R.string.open_shift)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun CloseShiftDialog(shift: EmployeeShiftEntity, onEvent: (UiEvent) -> Unit, onDismiss: () -> Unit) {
    var actual by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.close_shift)) }, text = { Column { Text(stringResource(R.string.blind_cash_count_hint)); OutlinedTextField(actual, { actual = it }, label = { Text(stringResource(R.string.actual_cash)) }); OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes_optional)) }) } }, confirmButton = { Button(onClick = { onEvent(UiEvent.CloseEmployeeShift(shift.id, actual, notes)); onDismiss() }) { Text(stringResource(R.string.close_shift)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
internal fun EmployeeSwitcherDialog(state: UiState, onEvent: (UiEvent) -> Unit) {
    var selected by remember { mutableStateOf<Long?>(null) }; var pin by remember { mutableStateOf("") }; var owner by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onEvent(UiEvent.SetEmployeeSwitcher(false)) },
        title = { Text(stringResource(R.string.switch_employee)) },
        text = {
            Column {
                TextButton(onClick = { owner = true; selected = null }) { Text(stringResource(R.string.owner_mode)) }
                state.employees.employees.filter { it.status == "ACTIVE" && it.pinHash != null }.forEach { employee ->
                    TextButton(onClick = { selected = employee.id; owner = false }) { Text(employee.name) }
                }
                OutlinedTextField(pin, { pin = it }, label = { Text(stringResource(R.string.pin)) }, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            Button(onClick = { if (owner) onEvent(UiEvent.SwitchToOwner(pin)) else selected?.let { onEvent(UiEvent.LoginEmployee(it, pin)) } }) {
                Text(stringResource(R.string.unlock))
            }
        },
        dismissButton = { TextButton(onClick = { onEvent(UiEvent.SetEmployeeSwitcher(false)) }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable private fun roleLabel(role: String): String = when (role) { StaffRoles.MANAGER -> stringResource(R.string.role_manager); StaffRoles.ACCOUNTANT -> stringResource(R.string.role_accountant); StaffRoles.OWNER -> stringResource(R.string.owner_mode); else -> stringResource(R.string.role_seller) }
@Composable private fun statusLabel(status: String): String = when (status) { "ACTIVE" -> stringResource(R.string.employee_active); "LEAVE" -> stringResource(R.string.employee_leave); "SUSPENDED" -> stringResource(R.string.employee_suspended); "LEFT" -> stringResource(R.string.employee_left); "OPEN" -> stringResource(R.string.shift_open); "CLOSED" -> stringResource(R.string.shift_closed); else -> status }
@Composable private fun employeeRangeLabel(range: SalesBookRange): String = when (range) { SalesBookRange.TODAY -> stringResource(R.string.period_today); SalesBookRange.YESTERDAY -> stringResource(R.string.period_yesterday); SalesBookRange.THIS_WEEK -> stringResource(R.string.this_week); SalesBookRange.LAST_WEEK -> stringResource(R.string.last_week); SalesBookRange.THIS_MONTH -> stringResource(R.string.this_month); SalesBookRange.LAST_MONTH -> stringResource(R.string.last_month); SalesBookRange.CUSTOM -> stringResource(R.string.period_custom) }
@Composable private fun permissionLabel(permission: StaffPermission): String = stringResource(when (permission) {
    StaffPermission.RECORD_SALE -> R.string.permission_record_sale; StaffPermission.RECORD_OUTFLOW -> R.string.permission_record_outflow; StaffPermission.EDIT_OWN_SALE -> R.string.permission_edit_own; StaffPermission.EDIT_ANY_SALE -> R.string.permission_edit_any; StaffPermission.DELETE_OWN_SALE -> R.string.permission_delete_own; StaffPermission.DELETE_ANY_SALE -> R.string.permission_delete_any; StaffPermission.VIEW_OWN_SALES -> R.string.permission_view_own; StaffPermission.VIEW_ALL_SALES -> R.string.permission_view_all; StaffPermission.VIEW_ACCOUNTS -> R.string.permission_accounts; StaffPermission.VIEW_REPORTS -> R.string.permission_reports; StaffPermission.VIEW_PROFIT -> R.string.permission_profit; StaffPermission.MANAGE_EMPLOYEES -> R.string.permission_employees; StaffPermission.VIEW_PAYROLL -> R.string.permission_payroll; StaffPermission.MANAGE_SETTINGS -> R.string.permission_settings; StaffPermission.MANAGE_SHIFTS -> R.string.permission_shifts; StaffPermission.VIEW_AUDIT -> R.string.permission_audit; StaffPermission.ASSIGN_SALESPERSON -> R.string.permission_assign_salesperson; StaffPermission.MANAGE_ACCOUNTS -> R.string.permission_manage_accounts
})
