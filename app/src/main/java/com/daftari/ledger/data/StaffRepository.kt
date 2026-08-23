package com.daftari.ledger.data

import androidx.room.withTransaction
import com.daftari.ledger.domain.Money
import com.daftari.ledger.domain.StaffPermission
import com.daftari.ledger.domain.StaffRoles
import com.daftari.ledger.domain.hasPermission
import com.daftari.ledger.security.PinHasher
import kotlinx.coroutines.flow.Flow

data class EmployeeInput(
    val name: String,
    val phone: String,
    val jobTitle: String,
    val role: String,
    val permissions: Long,
    val baseSalaryMinor: Long,
    val commissionBasisPoints: Int,
    val monthlyTargetMinor: Long,
    val startDate: Long,
    val notes: String,
    val username: String,
    val pin: String
)

class StaffRepository(private val db: AppDb) {
    val employees = db.employees()
    val employeeShops = db.employeeShops()
    val shifts = db.employeeShifts()
    private val settings = db.settings()
    private val audit = db.audit()
    private val documents = db.documents()

    fun observeForShop(shopId: Long): Flow<List<EmployeeEntity>> = employees.observeForShop(shopId)

    suspend fun create(shopId: Long, input: EmployeeInput, actorId: Long?): Long = db.withTransaction {
        requirePermission(actorId, StaffPermission.MANAGE_EMPLOYEES)
        validate(input, -1)
        val id = employees.insert(
            EmployeeEntity(
                name = input.name.trim(), phone = input.phone.trim(), jobTitle = input.jobTitle.trim(),
                role = input.role, permissions = input.permissions,
                baseSalaryMinor = input.baseSalaryMinor.coerceAtLeast(0),
                commissionBasisPoints = input.commissionBasisPoints.coerceIn(0, 10_000),
                monthlyTargetMinor = input.monthlyTargetMinor.coerceAtLeast(0),
                startDate = input.startDate, notes = input.notes.trim(), username = input.username.trim(),
                pinHash = input.pin.takeIf { it.isNotBlank() }?.let(PinHasher::hash)
            )
        )
        employeeShops.insert(EmployeeShopEntity(employeeId = id, shopId = shopId))
        audit.insert(AuditLogEntity(action = "CREATE", entity = "employee", entityId = id, detail = input.name, actorEmployeeId = actorId, afterValue = employeeSnapshot(requireNotNull(employees.get(id)))))
        setEmployeesEnabled(true)
        id
    }

    suspend fun update(id: Long, input: EmployeeInput, actorId: Long?) = db.withTransaction {
        requirePermission(actorId, StaffPermission.MANAGE_EMPLOYEES)
        validate(input, id)
        val old = employees.get(id) ?: throw LedgerException("الموظف غير موجود")
        val updated = old.copy(
            name = input.name.trim(), phone = input.phone.trim(), jobTitle = input.jobTitle.trim(),
            role = input.role, permissions = input.permissions,
            baseSalaryMinor = input.baseSalaryMinor.coerceAtLeast(0),
            commissionBasisPoints = input.commissionBasisPoints.coerceIn(0, 10_000),
            monthlyTargetMinor = input.monthlyTargetMinor.coerceAtLeast(0),
            startDate = input.startDate, notes = input.notes.trim(), username = input.username.trim(),
            pinHash = if (input.pin.isBlank()) old.pinHash else PinHasher.hash(input.pin),
            updatedAt = System.currentTimeMillis()
        )
        employees.update(updated)
        audit.insert(AuditLogEntity(action = "UPDATE", entity = "employee", entityId = id, detail = updated.name, actorEmployeeId = actorId, beforeValue = employeeSnapshot(old), afterValue = employeeSnapshot(updated)))
    }

    suspend fun changeStatus(id: Long, status: String, actorId: Long?) {
        requirePermission(actorId, StaffPermission.MANAGE_EMPLOYEES)
        if (status !in EMPLOYEE_STATUSES) throw LedgerException("حالة الموظف غير صالحة")
        val old = employees.get(id) ?: throw LedgerException("الموظف غير موجود")
        if (actorId == id && status != "ACTIVE") throw LedgerException("لا يمكنك تعطيل حسابك أثناء تسجيل الدخول")
        val now = System.currentTimeMillis()
        val updated = old.copy(status = status, inactiveAt = now.takeIf { status == "LEFT" }, updatedAt = now)
        employees.update(updated)
        if (status == "LEFT") {
            employeeShops.listForEmployee(id).filter { it.active }.forEach {
                employeeShops.update(it.copy(active = false, endedAt = now))
            }
            if (settings.get()?.currentEmployeeId == id) forceOwnerSession()
        }
        audit.insert(AuditLogEntity(action = "STATUS", entity = "employee", entityId = id, detail = status, actorEmployeeId = actorId, beforeValue = old.status, afterValue = status))
    }

    suspend fun assignToShop(employeeId: Long, shopId: Long, active: Boolean, actorId: Long?) {
        requirePermission(actorId, StaffPermission.MANAGE_EMPLOYEES)
        val old = employeeShops.getActive(employeeId, shopId)
        if (!active && settings.get()?.currentEmployeeId == employeeId) {
            throw LedgerException("لا يمكن إزالة المستخدم الحالي من الفرع أثناء تسجيل دخوله")
        }
        when {
            active && old == null -> employeeShops.insert(EmployeeShopEntity(employeeId = employeeId, shopId = shopId))
            !active && old != null -> employeeShops.update(old.copy(active = false, endedAt = System.currentTimeMillis()))
        }
        audit.insert(AuditLogEntity(action = if (active) "ASSIGN_SHOP" else "REMOVE_SHOP", entity = "employee", entityId = employeeId, detail = shopId.toString(), actorEmployeeId = actorId))
    }

    suspend fun login(employeeId: Long, pin: String): Boolean {
        val current = settings.get() ?: return false
        if (current.pinLockedUntil > System.currentTimeMillis()) return false
        val employee = employees.get(employeeId)
        if (employee == null || employee.status != "ACTIVE" || employee.pinHash == null || !PinHasher.verify(pin, employee.pinHash)) {
            recordLoginFailure(current)
            return false
        }
        settings.update(current.copy(employeesEnabled = true, currentEmployeeId = employeeId, failedPinAttempts = 0, pinLockedUntil = 0))
        audit.insert(AuditLogEntity(action = "LOGIN", entity = "employee_session", entityId = employeeId, actorEmployeeId = employeeId))
        return true
    }

    suspend fun switchToOwner(pin: String): Boolean {
        val current = settings.get() ?: return false
        if (current.pinLockedUntil > System.currentTimeMillis()) return false
        val ownerHash = current.pinHash
        if (ownerHash == null || !PinHasher.verify(pin, ownerHash)) {
            recordLoginFailure(current)
            return false
        }
        settings.update(current.copy(failedPinAttempts = 0, pinLockedUntil = 0))
        forceOwnerSession()
        return true
    }

    private suspend fun forceOwnerSession() {
        val current = settings.get() ?: return
        val old = current.currentEmployeeId
        settings.update(current.copy(currentEmployeeId = null))
        audit.insert(AuditLogEntity(action = "LOGOUT", entity = "employee_session", entityId = old, actorEmployeeId = old))
    }

    suspend fun currentEmployee(): EmployeeEntity? = settings.get()?.currentEmployeeId?.let { employees.get(it) }
    suspend fun enabled(): Boolean = settings.get()?.employeesEnabled == true

    suspend fun setEmployeesEnabled(enabled: Boolean) {
        val current = settings.get() ?: return
        if (enabled && current.pinHash == null) throw LedgerException("عيّن PIN لصاحب المحل قبل تفعيل دخول الموظفين")
        settings.update(current.copy(employeesEnabled = enabled, currentEmployeeId = current.currentEmployeeId.takeIf { enabled }))
    }

    suspend fun performance(shopId: Long, from: Long, to: Long): List<EmployeePerformanceRow> =
        employees.performance(shopId, from, to)

    suspend fun employeeSales(employeeId: Long, from: Long, to: Long): List<DocumentEntity> =
        employees.sales(employeeId, from, to)

    suspend fun activity(employeeId: Long): List<AuditLogEntity> = audit.byActor(employeeId)

    suspend fun openShift(
        shopId: Long,
        employeeId: Long,
        label: String,
        openingCashMinor: Long,
        actorId: Long?
    ): Long {
        requirePermission(actorId, StaffPermission.MANAGE_SHIFTS)
        if (shifts.openShift(shopId, employeeId) != null) throw LedgerException("لدى الموظف وردية مفتوحة")
        val id = shifts.insert(
            EmployeeShiftEntity(
                shopId = shopId, employeeId = employeeId, label = label.trim(),
                openedAt = System.currentTimeMillis(), openingCashMinor = openingCashMinor.coerceAtLeast(0),
                openedByEmployeeId = actorId
            )
        )
        audit.insert(AuditLogEntity(action = "OPEN_SHIFT", entity = "employee_shift", entityId = id, actorEmployeeId = actorId, afterValue = openingCashMinor.toString()))
        return id
    }

    suspend fun closeShift(shiftId: Long, actualCashMinor: Long, notes: String, actorId: Long?) {
        requirePermission(actorId, StaffPermission.MANAGE_SHIFTS)
        val shift = shifts.get(shiftId) ?: throw LedgerException("الوردية غير موجودة")
        if (shift.status != "OPEN") throw LedgerException("الوردية مغلقة")
        val cash = documents.shiftCashSummary(shiftId)
        val expected = shift.openingCashMinor + cash.cashSalesMinor - cash.cashOutflowsMinor
        val updated = shift.copy(
            closedAt = System.currentTimeMillis(), expectedCashMinor = expected,
            actualCashMinor = actualCashMinor, differenceMinor = actualCashMinor - expected,
            status = "CLOSED", notes = notes, closedByEmployeeId = actorId
        )
        shifts.update(updated)
        audit.insert(AuditLogEntity(action = "CLOSE_SHIFT", entity = "employee_shift", entityId = shiftId, actorEmployeeId = actorId, beforeValue = shift.openingCashMinor.toString(), afterValue = "expected=$expected,actual=$actualCashMinor,diff=${actualCashMinor - expected}"))
    }

    suspend fun openShiftFor(shopId: Long, employeeId: Long): EmployeeShiftEntity? = shifts.openShift(shopId, employeeId)
    suspend fun shiftsPeriod(shopId: Long, from: Long, to: Long): List<EmployeeShiftEntity> = shifts.listPeriod(shopId, from, to)

    suspend fun requirePermission(actorId: Long?, permission: StaffPermission) {
        if (actorId == null) return
        val actor = employees.get(actorId) ?: throw LedgerException("المستخدم الحالي غير موجود")
        if (actor.status != "ACTIVE") throw LedgerException("حساب الموظف غير نشط")
        if (!actor.permissions.hasPermission(permission)) throw LedgerException("ليست لديك صلاحية لهذه العملية")
    }

    suspend fun can(actorId: Long?, permission: StaffPermission): Boolean = runCatching {
        requirePermission(actorId, permission); true
    }.getOrDefault(false)

    private suspend fun recordLoginFailure(current: SettingsEntity) {
        val attempts = current.failedPinAttempts + 1
        settings.update(
            current.copy(
                failedPinAttempts = if (attempts >= MAX_LOGIN_ATTEMPTS) 0 else attempts,
                pinLockedUntil = if (attempts >= MAX_LOGIN_ATTEMPTS) System.currentTimeMillis() + LOGIN_LOCK_MILLIS else 0
            )
        )
    }

    private suspend fun validate(input: EmployeeInput, id: Long) {
        if (input.name.isBlank()) throw LedgerException("أدخل اسم الموظف")
        if (input.role !in EMPLOYEE_ROLES) throw LedgerException("دور الموظف غير صالح")
        val knownPermissions = StaffPermission.entries.fold(0L) { mask, permission -> mask or permission.mask }
        if (input.permissions and knownPermissions.inv() != 0L) throw LedgerException("الصلاحيات غير صالحة")
        if (input.startDate <= 0) throw LedgerException("تاريخ بداية العمل غير صالح")
        if (input.username.isNotBlank() && employees.countUsername(input.username.trim(), id) > 0) {
            throw LedgerException("اسم الدخول مستخدم")
        }
        if (input.pin.isNotBlank() && (input.pin.length < 4 || input.pin.any { !it.isDigit() })) {
            throw LedgerException("PIN الموظف يجب أن يكون أربعة أرقام على الأقل")
        }
    }

    private fun employeeSnapshot(employee: EmployeeEntity): String =
        "name=${employee.name};role=${employee.role};status=${employee.status};salary=${employee.baseSalaryMinor};commission=${employee.commissionBasisPoints};target=${employee.monthlyTargetMinor};permissions=${employee.permissions}"

    private companion object {
        const val MAX_LOGIN_ATTEMPTS = 5
        const val LOGIN_LOCK_MILLIS = 30_000L
        val EMPLOYEE_ROLES = setOf(StaffRoles.MANAGER, StaffRoles.SELLER, StaffRoles.ACCOUNTANT)
        val EMPLOYEE_STATUSES = setOf("ACTIVE", "LEAVE", "SUSPENDED", "LEFT")
    }
}
