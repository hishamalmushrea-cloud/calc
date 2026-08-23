package com.daftari.ledger.ui

import com.daftari.ledger.data.AuditLogEntity
import com.daftari.ledger.data.DocumentEntity
import com.daftari.ledger.data.EmployeeEntity
import com.daftari.ledger.data.EmployeePerformanceRow
import com.daftari.ledger.data.EmployeeShiftEntity
import com.daftari.ledger.data.EmployeeShopEntity
import com.daftari.ledger.domain.StaffPermission
import com.daftari.ledger.domain.hasPermission

data class EmployeeDetailStats(
    val today: EmployeePerformanceRow? = null,
    val week: EmployeePerformanceRow? = null,
    val month: EmployeePerformanceRow? = null
)

data class EmployeeUiState(
    val enabled: Boolean = false,
    val screenOpen: Boolean = false,
    val employees: List<EmployeeEntity> = emptyList(),
    val currentEmployee: EmployeeEntity? = null,
    val selectedEmployee: EmployeeEntity? = null,
    val performance: List<EmployeePerformanceRow> = emptyList(),
    val selectedSales: List<DocumentEntity> = emptyList(),
    val selectedActivity: List<AuditLogEntity> = emptyList(),
    val selectedStats: EmployeeDetailStats = EmployeeDetailStats(),
    val selectedShopLinks: List<EmployeeShopEntity> = emptyList(),
    val shifts: List<EmployeeShiftEntity> = emptyList(),
    val range: SalesBookRange = SalesBookRange.THIS_MONTH,
    val from: Long = 0,
    val to: Long = 0,
    val loading: Boolean = false,
    val switcherOpen: Boolean = false
)

fun UiState.can(permission: StaffPermission): Boolean =
    employees.currentEmployee?.permissions?.hasPermission(permission) ?: true

data class EmployeeDraft(
    val name: String,
    val phone: String,
    val jobTitle: String,
    val role: String,
    val permissions: Long,
    val baseSalary: String,
    val commissionPercent: String,
    val monthlyTarget: String,
    val startDate: Long,
    val notes: String,
    val username: String,
    val pin: String
)
