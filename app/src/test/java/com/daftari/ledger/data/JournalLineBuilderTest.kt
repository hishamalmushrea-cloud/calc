package com.daftari.ledger.data

import com.daftari.ledger.domain.DocType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalLineBuilderTest {

    // معرفات حسابات ثابتة للاختبار.
    private val refs = JournalLineBuilder.AccountRefs(
        cashId = 1000,
        salesId = 4000,
        purchasesId = 5000,
        expensesId = 5100,
        incomeId = 4100,
        arId = 1100,
        apId = 2000,
        destId = 1010
    )

    private fun List<JournalLineEntity>.totals(): Pair<Long, Long> =
        sumOf { it.debitMinor } to sumOf { it.creditMinor }

    private fun assertBalanced(lines: List<JournalLineEntity>) {
        val (dr, cr) = lines.totals()
        assertEquals("مدين $dr لا يساوي دائن $cr", dr, cr)
        assertTrue("يجب ألا يكون القيد صفريًا", dr > 0)
    }

    @Test fun cashSaleIsBalanced() {
        val lines = JournalLineBuilder.build(1, DocType.SALE, 1000, partyId = null, credit = false, notes = "", refs = refs)
        assertBalanced(lines)
        assertEquals(1000, lines.first { it.accountId == refs.cashId }.debitMinor)
        assertEquals(1000, lines.first { it.accountId == refs.salesId }.creditMinor)
    }

    @Test fun creditSalePostsToArAndParty() {
        val lines = JournalLineBuilder.build(1, DocType.SALE, 500, partyId = 42, credit = true, notes = "", refs = refs)
        assertBalanced(lines)
        val ar = lines.first { it.accountId == refs.arId }
        assertEquals(42L, ar.partyId)
        assertEquals(500, ar.debitMinor)
    }

    @Test fun creditSaleWithoutPartyIsRejected() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.SALE, 300, partyId = null, credit = true, notes = "", refs = refs)
        }
    }

    @Test fun creditPurchaseWithoutPartyIsRejected() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.PURCHASE, 300, partyId = null, credit = true, notes = "", refs = refs)
        }
    }

    @Test fun collectRequiresParty() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.COLLECT, 100, partyId = null, credit = false, notes = "", refs = refs)
        }
    }

    @Test fun collectIsBalanced() {
        val lines = JournalLineBuilder.build(1, DocType.COLLECT, 400, partyId = 7, credit = false, notes = "", refs = refs)
        assertBalanced(lines)
        assertEquals(400, lines.first { it.accountId == refs.cashId }.debitMinor)
        assertEquals(400, lines.first { it.accountId == refs.arId }.creditMinor)
    }

    @Test fun expenseIsBalanced() {
        val lines = JournalLineBuilder.build(1, DocType.EXPENSE, 80, partyId = null, credit = false, notes = "إيجار", refs = refs)
        assertBalanced(lines)
        assertEquals(80, lines.first { it.accountId == refs.expensesId }.debitMinor)
        assertEquals(80, lines.first { it.accountId == refs.cashId }.creditMinor)
        assertEquals("إيجار", lines.first { it.accountId == refs.expensesId }.memo)
    }

    @Test fun creditExpenseIsRejected() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.EXPENSE, 80, partyId = null, credit = true, notes = "إيجار", refs = refs)
        }
    }

    @Test fun transferRejectsSameAccount() {
        val bad = refs.copy(destId = refs.cashId)
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.TRANSFER, 100, partyId = null, credit = false, notes = "", refs = bad)
        }
    }

    @Test fun transferIsBalanced() {
        val lines = JournalLineBuilder.build(1, DocType.TRANSFER, 250, partyId = null, credit = false, notes = "", refs = refs)
        assertBalanced(lines)
        assertEquals(250, lines.first { it.accountId == refs.destId }.debitMinor)
        assertEquals(250, lines.first { it.accountId == refs.cashId }.creditMinor)
    }

    @Test fun creditPurchasePostsToAp() {
        val lines = JournalLineBuilder.build(1, DocType.PURCHASE, 2000, partyId = 9, credit = true, notes = "", refs = refs)
        assertBalanced(lines)
        assertEquals(2000, lines.first { it.accountId == refs.apId }.creditMinor)
        assertEquals(9L, lines.first { it.accountId == refs.apId }.partyId)
    }

    @Test fun payRequiresParty() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.PAY, 100, partyId = null, credit = false, notes = "", refs = refs)
        }
    }

    @Test fun zeroAmountRejected() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.SALE, 0, partyId = null, credit = false, notes = "", refs = refs)
        }
    }

    @Test fun cashPurchaseAndIncomeAreBalanced() {
        val purchase = JournalLineBuilder.build(1, DocType.PURCHASE, 600, partyId = null, credit = false, notes = "", refs = refs)
        val income = JournalLineBuilder.build(2, DocType.INCOME, 700, partyId = null, credit = false, notes = "", refs = refs)

        assertBalanced(purchase)
        assertBalanced(income)
        assertEquals(600, purchase.first { it.accountId == refs.purchasesId }.debitMinor)
        assertEquals(600, purchase.first { it.accountId == refs.cashId }.creditMinor)
        assertEquals(700, income.first { it.accountId == refs.cashId }.debitMinor)
        assertEquals(700, income.first { it.accountId == refs.incomeId }.creditMinor)
    }

    @Test fun payIsBalancedAndPostsToAccountsPayable() {
        val lines = JournalLineBuilder.build(1, DocType.PAY, 750, partyId = 18, credit = false, notes = "", refs = refs)

        assertBalanced(lines)
        val payable = lines.first { it.accountId == refs.apId }
        assertEquals(18L, payable.partyId)
        assertEquals(750, payable.debitMinor)
        assertEquals(750, lines.first { it.accountId == refs.cashId }.creditMinor)
    }

    @Test fun transferRequiresDestinationAccount() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(
                1, DocType.TRANSFER, 100, partyId = null, credit = false, notes = "",
                refs = refs.copy(destId = null)
            )
        }
    }

    @Test fun unsupportedDocumentTypeIsRejected() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.OPENING, 100, partyId = null, credit = false, notes = "", refs = refs)
        }
    }

    @Test fun negativeAmountRejected() {
        assertThrows(LedgerException::class.java) {
            JournalLineBuilder.build(1, DocType.SALE, -5, partyId = null, credit = false, notes = "", refs = refs)
        }
    }
}
