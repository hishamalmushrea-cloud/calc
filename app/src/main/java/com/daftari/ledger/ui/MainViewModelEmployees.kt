package com.daftari.ledger.ui

import androidx.lifecycle.viewModelScope
import com.daftari.ledger.R
import com.daftari.ledger.data.EmployeeInput
import com.daftari.ledger.data.EmployeePerformanceRow
import com.daftari.ledger.data.LedgerException
import com.daftari.ledger.domain.Money
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun MainViewModel.openEmployees() {
    if (!state.value.can(com.daftari.ledger.domain.StaffPermission.MANAGE_EMPLOYEES) &&
        !state.value.can(com.daftari.ledger.domain.StaffPermission.VIEW_REPORTS)
    ) return
    mutableState.update { it.copy(employees = it.employees.copy(screenOpen = true)) }
    loadEmployeeReport()
}

internal fun MainViewModel.closeEmployees() {
    mutableState.update { it.copy(employees = it.employees.copy(screenOpen = false, selectedEmployee = null)) }
}

internal fun MainViewModel.loadEmployeeReport() {
    val shop = state.value.shop ?: return
    val employeeState = state.value.employees
    val (from, to) = if (employeeState.from == 0L) employeeRange(employeeState.range) else employeeState.from to employeeState.to
    viewModelScope.launch {
        val performance = if (state.value.can(com.daftari.ledger.domain.StaffPermission.VIEW_REPORTS)) {
            staff.performance(shop.id, from, to)
        } else emptyList()
        val shifts = if (state.value.can(com.daftari.ledger.domain.StaffPermission.MANAGE_SHIFTS)) {
            staff.shiftsPeriod(shop.id, from, to)
        } else emptyList()
        mutableState.update {
            it.copy(employees = it.employees.copy(performance = performance, shifts = shifts, from = from, to = to, loading = false))
        }
    }
}

internal fun MainViewModel.setEmployeesEnabled(enabled: Boolean) = viewModelScope.launch {
    try {
        staff.requirePermission(currentActorId(), com.daftari.ledger.domain.StaffPermission.MANAGE_EMPLOYEES)
        staff.setEmployeesEnabled(enabled)
        mutableState.update { it.copy(employees = it.employees.copy(enabled = enabled, currentEmployee = it.employees.currentEmployee.takeIf { enabled })) }
    } catch (error: LedgerException) {
        dynamicError(error)
    }
}

internal fun MainViewModel.addEmployee(draft: EmployeeDraft) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    try {
        staff.create(shop.id, draft.toInput(), currentActorId())
        message(R.string.msg_employee_added)
        loadEmployeeReport()
    } catch (error: Exception) {
        dynamicError(error)
    }
}

internal fun MainViewModel.updateEmployee(id: Long, draft: EmployeeDraft) = viewModelScope.launch {
    try {
        staff.update(id, draft.toInput(), currentActorId())
        message(R.string.msg_employee_updated)
        selectEmployee(id)
    } catch (error: Exception) {
        dynamicError(error)
    }
}

internal fun MainViewModel.changeEmployeeStatus(id: Long, status: String) = viewModelScope.launch {
    try {
        staff.changeStatus(id, status, currentActorId())
        message(R.string.msg_employee_status_changed)
        loadEmployeeReport()
    } catch (error: Exception) {
        dynamicError(error)
    }
}

internal fun MainViewModel.selectEmployee(id: Long) {
    val shop = state.value.shop ?: return
    val own = currentActorId() == id
    if (!state.value.can(com.daftari.ledger.domain.StaffPermission.MANAGE_EMPLOYEES) &&
        !state.value.can(com.daftari.ledger.domain.StaffPermission.VIEW_REPORTS) && !own
    ) return
    viewModelScope.launch {
        val employee = staff.employees.get(id) ?: return@launch
        val today = employeeRange(SalesBookRange.TODAY)
        val week = employeeRange(SalesBookRange.THIS_WEEK)
        val month = employeeRange(SalesBookRange.THIS_MONTH)
        val selectedRange = state.value.employees.let { if (it.from == 0L) month else it.from to it.to }
        fun find(rows: List<EmployeePerformanceRow>) = rows.firstOrNull { it.employeeId == id }
        val canViewSales = state.value.can(com.daftari.ledger.domain.StaffPermission.VIEW_REPORTS) ||
            (own && state.value.can(com.daftari.ledger.domain.StaffPermission.VIEW_OWN_SALES))
        val stats = if (canViewSales) EmployeeDetailStats(
            today = find(staff.performance(shop.id, today.first, today.second)),
            week = find(staff.performance(shop.id, week.first, week.second)),
            month = find(staff.performance(shop.id, month.first, month.second))
        ) else EmployeeDetailStats()
        val sales = if (canViewSales) staff.employeeSales(id, selectedRange.first, selectedRange.second) else emptyList()
        val activity = if (state.value.can(com.daftari.ledger.domain.StaffPermission.VIEW_AUDIT)) staff.activity(id) else emptyList()
        val shopLinks = if (state.value.can(com.daftari.ledger.domain.StaffPermission.MANAGE_EMPLOYEES)) {
            staff.employeeShops.listForEmployee(id)
        } else emptyList()
        mutableState.update {
            it.copy(
                employees = it.employees.copy(
                    selectedEmployee = employee,
                    selectedStats = stats,
                    selectedSales = sales,
                    selectedActivity = activity,
                    selectedShopLinks = shopLinks
                )
            )
        }
    }
}

internal fun MainViewModel.selectEmployeePeriod(id: Long, from: Long, to: Long) {
    mutableState.update {
        it.copy(employees = it.employees.copy(screenOpen = true, from = from, to = to))
    }
    selectEmployee(id)
}

internal fun MainViewModel.closeEmployeeDetail() {
    mutableState.update { it.copy(employees = it.employees.copy(selectedEmployee = null, selectedSales = emptyList(), selectedActivity = emptyList())) }
}

internal fun MainViewModel.setEmployeeRange(range: SalesBookRange) {
    val (from, to) = employeeRange(range)
    mutableState.update { it.copy(employees = it.employees.copy(range = range, from = from, to = to)) }
    loadEmployeeReport()
}

internal fun MainViewModel.loginEmployee(id: Long, pin: String) = viewModelScope.launch {
    if (staff.login(id, pin)) {
        val employee = staff.employees.get(id)
        mutableState.update {
            it.copy(
                employees = it.employees.copy(
                    currentEmployee = employee, switcherOpen = false, enabled = true,
                    screenOpen = false, selectedEmployee = null, performance = emptyList(), selectedSales = emptyList(), selectedActivity = emptyList()
                ),
                selectedParty = null,
                partyStats = null
            )
        }
        loadSalesLedger()
        message(R.string.msg_employee_login_success)
    } else {
        message(R.string.msg_employee_login_failed)
    }
}

internal fun MainViewModel.switchToOwner(pin: String) = viewModelScope.launch {
    if (staff.switchToOwner(pin)) {
        mutableState.update { it.copy(employees = it.employees.copy(currentEmployee = null, switcherOpen = false)) }
        loadSalesLedger()
        loadEmployeeReport()
        message(R.string.msg_owner_mode)
    } else message(R.string.msg_owner_pin_required)
}

internal fun MainViewModel.setEmployeeSwitcher(open: Boolean) {
    mutableState.update { it.copy(employees = it.employees.copy(switcherOpen = open)) }
}

internal fun MainViewModel.assignEmployeeShop(employeeId: Long, shopId: Long, active: Boolean) = viewModelScope.launch {
    try {
        staff.assignToShop(employeeId, shopId, active, currentActorId())
        selectEmployee(employeeId)
        message(R.string.msg_employee_branch_updated)
    } catch (error: Exception) {
        dynamicError(error)
    }
}

internal fun MainViewModel.openEmployeeShift(employeeId: Long, label: String, openingCash: String) = viewModelScope.launch {
    val shop = state.value.shop ?: return@launch
    val amount = Money.fromMajor(openingCash, shop.fractionDigits)?.minor ?: return@launch message(R.string.msg_invalid_amount)
    try {
        staff.openShift(shop.id, employeeId, label, amount, currentActorId())
        message(R.string.msg_shift_opened)
        loadEmployeeReport()
    } catch (error: Exception) {
        dynamicError(error)
    }
}

internal fun MainViewModel.closeEmployeeShift(shiftId: Long, actualCash: String, notes: String) = viewModelScope.launch {
    val shop = state.value.shop
    val amount = Money.fromMajor(actualCash, shop?.fractionDigits ?: 2)?.minor ?: return@launch message(R.string.msg_invalid_amount)
    try {
        staff.closeShift(shiftId, amount, notes, currentActorId())
        message(R.string.msg_shift_closed)
        loadEmployeeReport()
    } catch (error: Exception) {
        dynamicError(error)
    }
}

internal fun MainViewModel.currentActorId(): Long? = state.value.employees.currentEmployee?.id

private fun EmployeeDraft.toInput(): EmployeeInput {
    val commission = runCatching { BigDecimal(commissionPercent.ifBlank { "0" }).movePointRight(2).intValueExact() }.getOrDefault(0)
    return EmployeeInput(
        name = name,
        phone = phone,
        jobTitle = jobTitle,
        role = role,
        permissions = permissions,
        baseSalaryMinor = Money.fromMajor(baseSalary)?.minor ?: 0,
        commissionBasisPoints = commission,
        monthlyTargetMinor = Money.fromMajor(monthlyTarget)?.minor ?: 0,
        startDate = startDate,
        notes = notes,
        username = username,
        pin = pin
    )
}

private fun employeeRange(range: SalesBookRange): Pair<Long, Long> {
    val today = LocalDate.now()
    val week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    val dates = when (range) {
        SalesBookRange.TODAY -> today to today
        SalesBookRange.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
        SalesBookRange.THIS_WEEK -> week to week.plusDays(6)
        SalesBookRange.LAST_WEEK -> week.minusWeeks(1) to week.minusDays(1)
        SalesBookRange.THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        SalesBookRange.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1) to today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())
        SalesBookRange.CUSTOM -> today to today
    }
    val zone = ZoneId.systemDefault()
    return dates.first.atStartOfDay(zone).toInstant().toEpochMilli() to dates.second.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
}
