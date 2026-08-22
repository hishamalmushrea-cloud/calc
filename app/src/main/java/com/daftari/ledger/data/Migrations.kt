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

    /** v2 → v3: إنشاء فهرس البحث النصي الكامل ومحفزات مزامنته. */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            PartySearchIndex.ensure(db)
        }
    }

    /** v3 → v4: استحقاق المبيعات الآجلة وتسلسل أرقام السندات. */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE shops ADD COLUMN nextDocumentNumber INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE documents ADD COLUMN dueAt INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_dueAt ON documents(dueAt)")
            db.execSQL(
                """
                UPDATE shops
                SET nextDocumentNumber = MAX(
                    1,
                    COALESCE((
                        SELECT MAX(CAST(docNumber AS INTEGER)) + 1
                        FROM documents
                        WHERE documents.shopId = shops.id
                          AND docNumber GLOB '[0-9]*'
                    ), 1)
                )
                """.trimIndent()
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
