package com.daftari.ledger.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * بذور جدول العملات. تُستدعى عند إنشاء قاعدة بيانات جديدة فقط؛
 * الترقية من v8 تُدرج نفس الصفوف داخل `MIGRATION_8_9`.
 *
 * الإدخال يتم بـ `execSQL` مباشرة (بلا معاملة صريحة) لأن `onCreate` يُستدعى
 * أصلًا داخل معاملة إنشاء القاعدة، وقد أثبت الاختبار على المحاكي أن فتح
 * معاملة إضافية هنا يُفشل الإدراج بصمت.
 */
internal object CurrencySeeds {

    private const val INSERT_SQL =
        "INSERT OR IGNORE INTO `currencies` (`id`, `code`, `name`, `symbol`, `fractionDigits`, `archived`, `createdAt`) " +
            "VALUES (?, ?, ?, ?, ?, 0, ?)"

    fun bootstrap(db: SupportSQLiteDatabase, currencies: List<CurrencyEntity>) {
        val now = System.currentTimeMillis()
        currencies.forEachIndexed { index, currency ->
            db.execSQL(
                INSERT_SQL,
                arrayOf<Any>(
                    index + 1L,
                    currency.code,
                    currency.name,
                    currency.symbol,
                    currency.fractionDigits.toLong(),
                    now
                )
            )
        }
    }
}
