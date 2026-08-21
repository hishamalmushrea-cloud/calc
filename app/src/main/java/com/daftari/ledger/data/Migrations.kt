package com.daftari.ledger.data

import androidx.room.migration.Migration

/**
 * مهاجرات قاعدة البيانات — المسار الآمن الوحيد لتغيير المخطط.
 *
 * عند أي تغيير مستقبلي في المخطط:
 * 1) ارفع `version` في AppDb.
 * 2) أضف Migration هنا وأدرجه في `ALL`.
 * 3) لا تستخدم `fallbackToDestructiveMigration` أبدًا (تمسح بيانات المستخدم).
 *
 * مثال لبنية المهاجرة القادمة:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE app_settings ADD COLUMN x TEXT NOT NULL DEFAULT ''")
 *     }
 * }
 * // ثم: val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
 * ```
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
