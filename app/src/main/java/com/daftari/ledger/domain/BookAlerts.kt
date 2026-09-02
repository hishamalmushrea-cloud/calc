package com.daftari.ledger.domain

/**
 * تنبيهات ديون «دفتر الحسابات» — منطق صرفي بلا Android ولا قاعدة بيانات.
 *
 * القاعدة: **دين متوقف** = صافيه موجب (عليه) ولم يتحرك منذ
 * [BookAlerts.STALE_AFTER_DAYS] يومًا أو أكثر. الهدف أن يرى المستخدم من يستحق
 * المتابعة أولًا دون أن ينقلب الدفتر إلى قائمة تنبيهات مزعجة:
 * - من ليس عليه شيء (صفر) أو له عندك مال (سالب) لا يُنبَّه عنه.
 * - شخص بلا تاريخ حركة صالح لا يُنبَّه عنه؛ لا نستطيع الحكم عليه.
 * - الأيام تُحسب بقسمة صحيحة على طول اليوم، فلا تُحتسب الساعات الأخيرة يومًا كاملًا.
 *
 * كل الأوقات بالملّي ثانية منذ Epoch، وكل المبالغ بالوحدة الصغرى لكل عملة على حدة؛
 * لا تُجمع عملات مختلفة ولا تُقارن مبالغها.
 */

/** رصيد شخص في عملة واحدة مع آخر حركة له — ما يكفي لتقييم التنبيه. */
data class BookDebtor(
    val personId: Long,
    val currencyId: Long,
    val netMinor: Long,
    val lastActivityAt: Long? = null
)

/** تنبيه واحد: دين [netMinor] متوقف منذ [idleDays] يومًا. */
data class BookDebtAlert(
    val personId: Long,
    val currencyId: Long,
    val netMinor: Long,
    val idleDays: Long
)

/**
 * ملخّص أعداد للودجت: أشخاص عليهم دين، ومنهم من له دين متوقف.
 *
 * **أعداد لا مبالغ** — فلا تُجمع عملات مختلفة ولا يُكشف أي رصيد.
 */
data class BookWidgetSummary(
    val debtors: Int = 0,
    val staleDebtors: Int = 0,
    val staleBalances: Int = 0
) {
    val hasDebts: Boolean get() = debtors > 0
    val hasStaleDebts: Boolean get() = staleDebtors > 0
}

object BookAlerts {

    /** عدد الأيام التي بعدها يُعتبر الدين متوقفًا ويستحق المتابعة. */
    const val STALE_AFTER_DAYS = 30

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * كم يومًا كاملًا مرّ على آخر حركة.
     *
     * يُرجع `null` عندما لا يمكن الحكم: لا حركة إطلاقًا، أو تاريخ حركة سالب
     * (بيانات تالفة)، أو تاريخ في المستقبل (فرق ساعة بين الأجهزة).
     */
    fun idleDays(lastActivityAt: Long?, nowMillis: Long): Long? = when {
        lastActivityAt == null -> null
        lastActivityAt < 0L -> null
        lastActivityAt > nowMillis -> null
        else -> (nowMillis - lastActivityAt) / MILLIS_PER_DAY
    }

    /**
     * هل هذا الرصيد دين متوقف؟
     *
     * @param staleAfterDays مرور هذا العدد من الأيام أو أكثر يُطلق التنبيه،
     *   وقيمته ≤ 0 تُعطّل التنبيهات كلها (يستخدمها المستخدم لإخفاء القسم).
     */
    fun isStale(
        netMinor: Long,
        lastActivityAt: Long?,
        nowMillis: Long,
        staleAfterDays: Int = STALE_AFTER_DAYS
    ): Boolean {
        if (netMinor <= 0L || staleAfterDays <= 0) return false
        val idle = idleDays(lastActivityAt, nowMillis) ?: return false
        return idle >= staleAfterDays
    }

    /**
     * كل الديون المتوقفة مرتّبة بالأهم: الأكثر توقفًا أولًا، ثم الأكبر دينًا عند
     * التساوي، حتى يبقى الترتيب ثابتًا بين تشغيلين.
     */
    fun staleDebts(
        debtors: List<BookDebtor>,
        nowMillis: Long,
        staleAfterDays: Int = STALE_AFTER_DAYS
    ): List<BookDebtAlert> = debtors.mapNotNull { debtor ->
        if (!isStale(debtor.netMinor, debtor.lastActivityAt, nowMillis, staleAfterDays)) return@mapNotNull null
        val idle = idleDays(debtor.lastActivityAt, nowMillis) ?: return@mapNotNull null
        BookDebtAlert(debtor.personId, debtor.currencyId, debtor.netMinor, idle)
    }.sortedWith(
        compareByDescending<BookDebtAlert> { it.idleDays }.thenByDescending { it.netMinor }
    )

    /**
     * ملخّص الودجت.
     *
     * - [BookWidgetSummary.debtors]: عدد الأشخاص الذين عليهم دين في عملة واحدة على الأقل.
     * - [BookWidgetSummary.staleBalances]: عدد أرصدة (شخص، عملة) المتوقفة.
     * - [BookWidgetSummary.staleDebtors]: عدد الأشخاص الذين لديهم رصيد متوقف واحد على الأقل.
     *
     * الشخص الذي عليه دين في عملتين يُحسب **مرة واحدة** في عددي الأشخاص، ومرتين في
     * عدد الأرصدة المتوقفة.
     */
    fun summary(
        debtors: List<BookDebtor>,
        nowMillis: Long,
        staleAfterDays: Int = STALE_AFTER_DAYS
    ): BookWidgetSummary {
        val stale = staleDebts(debtors, nowMillis, staleAfterDays)
        return BookWidgetSummary(
            debtors = debtors.asSequence().filter { it.netMinor > 0L }.map { it.personId }.distinct().count(),
            staleDebtors = stale.asSequence().map { it.personId }.distinct().count(),
            staleBalances = stale.size
        )
    }
}
