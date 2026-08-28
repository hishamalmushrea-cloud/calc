package com.daftari.ledger.domain

/**
 * منطق «دفتر الحسابات» الصرفي — دوال نقية بلا Android ولا قاعدة بيانات.
 *
 * المصطلحات:
 * - **له** ([BookSide.LE]): مبلغ في مصلحة الشخص؛ يزيد ما له عندك.
 * - **عليه** ([BookSide.DEBT]): مبلغ على الشخص؛ يزيد دينه لديك.
 * - **تسديد** ([BookEntryKind.SETTLEMENT]): عملية تسديد تُقلّص الجانب الأكبر حاليًا،
 *   فتُحفظ بجانبها الفعلي ([BookSide]) حتى يبقى السجل ثابتًا لو حُرّرت عمليات أقدم.
 *
 * كل المبالغ بالوحدة الصغرى (Long) لكل عملة على حدة؛ لا تُجمع عملات مختلفة أبدًا.
 */

/** جانب الأثر على الرصيد: له أم عليه. */
enum class BookSide { LE, DEBT }

/** نوع العملية كما يظهر في السجل: له / عليه / تسديد. */
enum class BookEntryKind { LE, DEBT, SETTLEMENT }

/** عملية واحدة بمعلومات كافية لحساب الرصيد الجاري دون الرجوع لقاعدة البيانات. */
data class BookEntryCore(
    val kind: BookEntryKind,
    val side: BookSide,
    val amountMinor: Long,
    val occurredAt: Long = 0L,
    val sequence: Long = 0L
)

/** سطر كشف حساب: العملية ورصيد الشخص الجاري بعدها (موجب = عليه، سالب = له). */
data class BookStatementLine(
    val entry: BookEntryCore,
    val runningNetMinor: Long
)

/** إجماليات شخص في عملة واحدة. */
data class BookTotals(
    val creditMinor: Long = 0,
    val debtMinor: Long = 0,
    val settledMinor: Long = 0
) {
    /** الصافي = عليه − له. موجب يعني «عليه»، سالب يعني «له». */
    val netMinor: Long get() = AccountsBookMath.netMinor(debtMinor, creditMinor)
}

object AccountsBookMath {

    /**
     * اتجاه أثر التسديد: يقلّص الجانب الأكبر حاليًا.
     * إذا كان الصافي صفرًا يُعتبر تسديدًا من الشخص (له).
     */
    fun settlementSide(netBeforeMinor: Long): BookSide =
        if (netBeforeMinor < 0L) BookSide.DEBT else BookSide.LE

    /** الجانب المحفوظ للعملية؛ يُحسب مرة واحدة عند التسجيل. */
    fun sideOf(kind: BookEntryKind, netBeforeMinor: Long): BookSide = when (kind) {
        BookEntryKind.LE -> BookSide.LE
        BookEntryKind.DEBT -> BookSide.DEBT
        BookEntryKind.SETTLEMENT -> settlementSide(netBeforeMinor)
    }

    /**
     * الأثر الموجّه على صافي الدين: «عليه» يزيد و«له» ينقص.
     * @throws IllegalArgumentException إذا كان المبلغ صفرًا أو سالبًا.
     */
    fun deltaMinor(side: BookSide, amountMinor: Long): Long {
        require(amountMinor > 0L) { "المبلغ يجب أن يكون أكبر من صفر" }
        return if (side == BookSide.DEBT) amountMinor else -amountMinor
    }

    /** الصافي = عليه − له (بأمان ضد الفيضان). */
    fun netMinor(debtMinor: Long, creditMinor: Long): Long =
        Math.subtractExact(debtMinor, creditMinor)

    /**
     * إجماليات قائمة عمليات لعملة واحدة.
     * «له» و«عليه» تُجمع حسب الجانب الفعلي، و«التسديدات» تُجمع حسب النوع للعرض فقط.
     */
    fun totals(entries: List<BookEntryCore>): BookTotals {
        var credit = 0L
        var debt = 0L
        var settled = 0L
        entries.forEach { entry ->
            require(entry.amountMinor > 0L) { "المبلغ يجب أن يكون أكبر من صفر" }
            when (entry.side) {
                BookSide.LE -> credit = Math.addExact(credit, entry.amountMinor)
                BookSide.DEBT -> debt = Math.addExact(debt, entry.amountMinor)
            }
            if (entry.kind == BookEntryKind.SETTLEMENT) settled = Math.addExact(settled, entry.amountMinor)
        }
        return BookTotals(creditMinor = credit, debtMinor = debt, settledMinor = settled)
    }

    /**
     * كشف حساب برصيد جارٍ: تُرتَّب العمليات بالتاريخ ثم بمعرّفها حتى يبقى الترتيب
     * ثابتًا عند تساوي التواريخ، ويُحسب الرصيد تراكميًا من أول عملية.
     */
    fun statement(entries: List<BookEntryCore>): List<BookStatementLine> {
        var running = 0L
        return entries.sortedWith(compareBy({ it.occurredAt }, { it.sequence })).map { entry ->
            running = Math.addExact(running, deltaMinor(entry.side, entry.amountMinor))
            BookStatementLine(entry, running)
        }
    }

    /** صافي شخص في عملة بعد عملية جديدة — يُستخدم لعرض الأثر قبل الحفظ. */
    fun netAfter(netBeforeMinor: Long, kind: BookEntryKind, amountMinor: Long): Long =
        Math.addExact(
            netBeforeMinor,
            deltaMinor(sideOf(kind, netBeforeMinor), amountMinor)
        )
}
