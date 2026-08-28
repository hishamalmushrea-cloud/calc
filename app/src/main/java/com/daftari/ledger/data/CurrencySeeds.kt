package com.daftari.ledger.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * بذور عملات دفتر الحسابات.
 *
 * تُدرج عند إنشاء قاعدة البيانات وفي الترحيل إلى v9، وهي بداية فقط: يستطيع المستخدم
 * إضافة أي عملة أخرى أو أرشفة هذه. الإدخال `INSERT OR IGNORE` لأن `code` فريد،
 * فلا تتكرر البذور عند إعادة الفتح أو بعد استعادة نسخة احتياطية.
 */
object CurrencySeeds {

    data class Seed(val code: String, val name: String, val symbol: String, val fractionDigits: Int)

    /** عملة بلا رمز لمن لا يريد عملة رسمية. */
    const val LOCAL_CODE = "LOCAL"

    val DEFAULTS: List<Seed> = listOf(
        Seed(LOCAL_CODE, "محلي", "", 2),
        Seed("YER", "يمني", "﷼", 2),
        Seed("SAR", "سعودي", "ر.س", 2),
        Seed("USD", "دولار", "$", 2)
    )

    const val CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS `currencies` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`code` TEXT NOT NULL, " +
            "`name` TEXT NOT NULL, " +
            "`symbol` TEXT NOT NULL, " +
            "`fractionDigits` INTEGER NOT NULL, " +
            "`archived` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL)"

    const val CREATE_INDEX_SQL =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_currencies_code` ON `currencies` (`code`)"

    fun ensure(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        DEFAULTS.forEach { seed ->
            db.execSQL(
                "INSERT OR IGNORE INTO currencies (code, name, symbol, fractionDigits, archived, createdAt) " +
                    "VALUES (?, ?, ?, ?, 0, ?)",
                arrayOf<Any>(seed.code, seed.name, seed.symbol, seed.fractionDigits, now)
            )
        }
    }
}
