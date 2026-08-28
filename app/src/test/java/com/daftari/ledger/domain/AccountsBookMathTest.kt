package com.daftari.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق دفتر الحسابات الصرفي.
 *
 * تغطي المثال المرجعي: له 2000 وعليه 5000 ⇒ الصافي «عليه 3000»، وسلوك التسديد
 * في الاتجاهين، وثبات ترتيب كشف الحساب ورصيده الجاري.
 */
class AccountsBookMathTest {

    private fun entry(
        kind: BookEntryKind,
        side: BookSide,
        amount: Long,
        at: Long = 0L,
        id: Long = 0L
    ) = BookEntryCore(kind, side, amount, at, id)

    @Test
    fun netIsDebtMinusCredit() {
        val totals = AccountsBookMath.totals(
            listOf(
                entry(BookEntryKind.LE, BookSide.LE, 2_000L, at = 10L, id = 1L),
                entry(BookEntryKind.DEBT, BookSide.DEBT, 5_000L, at = 20L, id = 2L)
            )
        )

        assertEquals(2_000L, totals.creditMinor)
        assertEquals(5_000L, totals.debtMinor)
        assertEquals(0L, totals.settledMinor)
        assertEquals(3_000L, totals.netMinor)
    }

    @Test
    fun deltaSignFollowsSide() {
        assertEquals(5_000L, AccountsBookMath.deltaMinor(BookSide.DEBT, 5_000L))
        assertEquals(-2_000L, AccountsBookMath.deltaMinor(BookSide.LE, 2_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun deltaRejectsZeroAmount() {
        AccountsBookMath.deltaMinor(BookSide.DEBT, 0L)
    }

    @Test
    fun settlementShrinksTheLargerSide() {
        assertEquals(BookSide.LE, AccountsBookMath.settlementSide(3_000L))
        assertEquals(BookSide.DEBT, AccountsBookMath.settlementSide(-3_000L))
        // عند التساوي يُعتبر تسديدًا من الشخص.
        assertEquals(BookSide.LE, AccountsBookMath.settlementSide(0L))
    }

    @Test
    fun settlementIsCountedSeparatelyButAffectsNet() {
        val side = AccountsBookMath.sideOf(BookEntryKind.SETTLEMENT, 3_000L)
        val totals = AccountsBookMath.totals(
            listOf(
                entry(BookEntryKind.DEBT, BookSide.DEBT, 5_000L, at = 10L, id = 1L),
                entry(BookEntryKind.LE, BookSide.LE, 2_000L, at = 20L, id = 2L),
                entry(BookEntryKind.SETTLEMENT, side, 1_000L, at = 30L, id = 3L)
            )
        )

        assertEquals(BookSide.LE, side)
        assertEquals(5_000L, totals.debtMinor)
        assertEquals(3_000L, totals.creditMinor)
        assertEquals(1_000L, totals.settledMinor)
        assertEquals(2_000L, totals.netMinor)
    }

    @Test
    fun settlementLargerThanDebtFlipsToCredit() {
        assertEquals(-2_000L, AccountsBookMath.netAfter(3_000L, BookEntryKind.SETTLEMENT, 5_000L))
    }

    @Test
    fun settlementWhenYouOweHimReducesHisCredit() {
        assertEquals(-2_500L, AccountsBookMath.netAfter(-4_000L, BookEntryKind.SETTLEMENT, 1_500L))
    }

    @Test
    fun statementOrdersByDateThenSequenceAndAccumulatesBalance() {
        val statement = AccountsBookMath.statement(
            listOf(
                entry(BookEntryKind.DEBT, BookSide.DEBT, 5_000L, at = 30L, id = 3L),
                entry(BookEntryKind.LE, BookSide.LE, 2_000L, at = 10L, id = 1L),
                entry(BookEntryKind.SETTLEMENT, BookSide.LE, 1_000L, at = 20L, id = 2L)
            )
        )

        assertEquals(listOf(10L, 20L, 30L), statement.map { it.entry.occurredAt })
        assertEquals(listOf(-2_000L, -3_000L, 2_000L), statement.map { it.runningNetMinor })
    }

    @Test
    fun sameTimestampFallsBackToSequence() {
        val statement = AccountsBookMath.statement(
            listOf(
                entry(BookEntryKind.DEBT, BookSide.DEBT, 700L, at = 100L, id = 9L),
                entry(BookEntryKind.LE, BookSide.LE, 300L, at = 100L, id = 4L)
            )
        )

        assertEquals(listOf(4L, 9L), statement.map { it.entry.sequence })
        assertEquals(listOf(-300L, 400L), statement.map { it.runningNetMinor })
    }

    @Test
    fun sideOfKeepsExplicitKindsStable() {
        assertEquals(BookSide.LE, AccountsBookMath.sideOf(BookEntryKind.LE, 9_999L))
        assertEquals(BookSide.DEBT, AccountsBookMath.sideOf(BookEntryKind.DEBT, -9_999L))
    }

    @Test
    fun totalsOverflowIsRejectedNotSilentlyWrong() {
        val overflow = runCatching {
            AccountsBookMath.totals(
                listOf(
                    entry(BookEntryKind.DEBT, BookSide.DEBT, Long.MAX_VALUE, at = 1L, id = 1L),
                    entry(BookEntryKind.DEBT, BookSide.DEBT, 1L, at = 2L, id = 2L)
                )
            )
        }
        assertTrue(overflow.isFailure)
    }
}
