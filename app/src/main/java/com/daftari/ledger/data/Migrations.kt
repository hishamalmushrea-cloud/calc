package com.daftari.ledger.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * مهاجرات قاعدة البيانات — المسار الآمن الوحيد لتغيير المخطط.
 *
 * عند أي تغيير مستقبلي في المخطط:
 * 1) ارفع `version` في AppDb.
 * 2) أضف Migration هنا وأدرجه في `ALL`.
 * 3) لا تستخدم `fallbackToDestructiveMigration` أبدًا (تمسح بيانات المستخدم).
 */
object Migrations {

    /** v1 → v2: إضافة حد ائتمان لكل حساب (عميل/مورد). */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE parties ADD COLUMN creditLimitMinor INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
