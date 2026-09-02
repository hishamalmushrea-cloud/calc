package com.daftari.ledger.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffPermissionsTest {
    @Test
    fun ownerHasEveryPermission() {
        StaffPermission.entries.forEach { permission ->
            assertTrue("Owner must have ${permission.name}", StaffRoles.defaultPermissions(StaffRoles.OWNER).hasPermission(permission))
        }
    }

    @Test
    fun sellerIsRestrictedToOwnSalesAndRecording() {
        val seller = StaffRoles.defaultPermissions(StaffRoles.SELLER)
        assertTrue(seller.hasPermission(StaffPermission.RECORD_SALE))
        assertFalse(seller.hasPermission(StaffPermission.RECORD_OUTFLOW))
        assertTrue(seller.hasPermission(StaffPermission.VIEW_OWN_SALES))
        assertTrue(seller.hasPermission(StaffPermission.EDIT_OWN_SALE))
        assertFalse(seller.hasPermission(StaffPermission.VIEW_ALL_SALES))
        assertFalse(seller.hasPermission(StaffPermission.VIEW_REPORTS))
        assertFalse(seller.hasPermission(StaffPermission.VIEW_PAYROLL))
        assertFalse(seller.hasPermission(StaffPermission.MANAGE_EMPLOYEES))
    }

    @Test
    fun customPermissionCanBeAddedAndRemovedWithoutChangingRole() {
        val base = StaffRoles.defaultPermissions(StaffRoles.ACCOUNTANT)
        val customized = base or StaffPermission.MANAGE_SHIFTS.mask
        assertTrue(customized.hasPermission(StaffPermission.MANAGE_SHIFTS))
        assertFalse((customized and StaffPermission.MANAGE_SHIFTS.mask.inv()).hasPermission(StaffPermission.MANAGE_SHIFTS))
    }

    @Test
    fun writeAccessIsSeparatedFromReadAccess() {
        // الفصل بين «عرض الحسابات» (قراءة) و«إدارة الحسابات» (كتابة العمليات غير البيعية).
        val seller = StaffRoles.defaultPermissions(StaffRoles.SELLER)
        val accountant = StaffRoles.defaultPermissions(StaffRoles.ACCOUNTANT)
        val owner = StaffRoles.defaultPermissions(StaffRoles.OWNER)

        assertTrue(seller.hasPermission(StaffPermission.RECORD_SALE))
        assertFalse("البائع لا يملك إدارة العمليات غير البيعية", seller.hasPermission(StaffPermission.MANAGE_ACCOUNTS))

        assertTrue(accountant.hasPermission(StaffPermission.VIEW_ACCOUNTS))
        assertTrue("المحاسب يملك إدارة العمليات غير البيعية", accountant.hasPermission(StaffPermission.MANAGE_ACCOUNTS))

        assertTrue(owner.hasPermission(StaffPermission.MANAGE_ACCOUNTS))
        assertTrue(owner.hasPermission(StaffPermission.VIEW_ACCOUNTS))
    }

    @Test
    fun permissionBitsDoNotOverlap() {
        // كل صلاحية تملك bit مستقلًا حتى لا تتعارض أقنعة الصلاحيات.
        val bits = StaffPermission.entries.map { it.bit }
        assertTrue("صلاحيات مكررة في البتات", bits.size == bits.toSet().size)
    }
}
