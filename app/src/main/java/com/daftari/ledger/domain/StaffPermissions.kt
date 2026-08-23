package com.daftari.ledger.domain

enum class StaffPermission(val bit: Int) {
    RECORD_SALE(0), RECORD_OUTFLOW(1), EDIT_OWN_SALE(2), EDIT_ANY_SALE(3),
    DELETE_OWN_SALE(4), DELETE_ANY_SALE(5), VIEW_OWN_SALES(6), VIEW_ALL_SALES(7),
    VIEW_ACCOUNTS(8), VIEW_REPORTS(9), VIEW_PROFIT(10), MANAGE_EMPLOYEES(11),
    VIEW_PAYROLL(12), MANAGE_SETTINGS(13), MANAGE_SHIFTS(14), VIEW_AUDIT(15),
    ASSIGN_SALESPERSON(16);

    val mask: Long get() = 1L shl bit
}

object StaffRoles {
    const val OWNER = "OWNER"
    const val MANAGER = "MANAGER"
    const val SELLER = "SELLER"
    const val ACCOUNTANT = "ACCOUNTANT"

    val ALL: Long = StaffPermission.entries.fold(0L) { mask, permission -> mask or permission.mask }
    val MANAGER_DEFAULT: Long = ALL and StaffPermission.VIEW_PAYROLL.mask.inv() and StaffPermission.MANAGE_SETTINGS.mask.inv()
    val SELLER_DEFAULT: Long = maskOf(
        StaffPermission.RECORD_SALE,
        StaffPermission.EDIT_OWN_SALE,
        StaffPermission.VIEW_OWN_SALES
    )
    val ACCOUNTANT_DEFAULT: Long = maskOf(
        StaffPermission.RECORD_OUTFLOW,
        StaffPermission.VIEW_ALL_SALES,
        StaffPermission.VIEW_ACCOUNTS,
        StaffPermission.VIEW_REPORTS,
        StaffPermission.VIEW_PROFIT
    )

    fun defaultPermissions(role: String): Long = when (role) {
        OWNER -> ALL
        MANAGER -> MANAGER_DEFAULT
        ACCOUNTANT -> ACCOUNTANT_DEFAULT
        else -> SELLER_DEFAULT
    }

    fun maskOf(vararg permissions: StaffPermission): Long =
        permissions.fold(0L) { mask, permission -> mask or permission.mask }
}

fun Long.hasPermission(permission: StaffPermission): Boolean = this and permission.mask != 0L
