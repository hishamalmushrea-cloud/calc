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

    /** v4 → v5: تصنيفات الدخل/المصروف وإعدادات الخصوصية وحماية PIN. */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    shopId INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    name TEXT NOT NULL,
                    archived INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_shopId ON categories(shopId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_shopId_kind_name ON categories(shopId, kind, name)")
            db.execSQL("ALTER TABLE documents ADD COLUMN categoryId INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_categoryId ON documents(categoryId)")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN latinDigits INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN failedPinAttempts INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN pinLockedUntil INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v5 → v6: بيانات وصفية لدفتر البيع اليومي دون تكرار العمليات المالية. */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_books (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    shopId INTEGER NOT NULL,
                    dayStart INTEGER NOT NULL,
                    notes TEXT NOT NULL,
                    status TEXT NOT NULL,
                    closedAt INTEGER,
                    reopenedAt INTEGER,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_books_shopId_dayStart ON daily_books(shopId, dayStart)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_books_dayStart ON daily_books(dayStart)")
        }
    }

    /** v6 → v7: الموظفون والصلاحيات وإسناد العمليات والورديات والتدقيق. */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS employees (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL, phone TEXT NOT NULL, jobTitle TEXT NOT NULL,
                    role TEXT NOT NULL, permissions INTEGER NOT NULL,
                    baseSalaryMinor INTEGER NOT NULL, commissionBasisPoints INTEGER NOT NULL,
                    monthlyTargetMinor INTEGER NOT NULL, startDate INTEGER NOT NULL,
                    notes TEXT NOT NULL, status TEXT NOT NULL, username TEXT NOT NULL,
                    pinHash TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    inactiveAt INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_name ON employees(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_phone ON employees(phone)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_status ON employees(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_role ON employees(role)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS employee_shops (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    employeeId INTEGER NOT NULL, shopId INTEGER NOT NULL,
                    active INTEGER NOT NULL, assignedAt INTEGER NOT NULL, endedAt INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_shops_shopId ON employee_shops(shopId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_shops_employeeId ON employee_shops(employeeId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS employee_shifts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    shopId INTEGER NOT NULL, employeeId INTEGER NOT NULL, label TEXT NOT NULL,
                    openedAt INTEGER NOT NULL, closedAt INTEGER,
                    openingCashMinor INTEGER NOT NULL, expectedCashMinor INTEGER NOT NULL,
                    actualCashMinor INTEGER, differenceMinor INTEGER, status TEXT NOT NULL,
                    notes TEXT NOT NULL, openedByEmployeeId INTEGER, closedByEmployeeId INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_shifts_shopId ON employee_shifts(shopId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_shifts_employeeId ON employee_shifts(employeeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_shifts_status ON employee_shifts(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_shifts_openedAt ON employee_shifts(openedAt)")

            listOf("employeeId", "shiftId", "createdByEmployeeId", "updatedByEmployeeId", "deletedByEmployeeId").forEach {
                db.execSQL("ALTER TABLE documents ADD COLUMN $it INTEGER")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_employeeId ON documents(employeeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_shiftId ON documents(shiftId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_createdByEmployeeId ON documents(createdByEmployeeId)")

            db.execSQL("ALTER TABLE audit_logs ADD COLUMN actorEmployeeId INTEGER")
            db.execSQL("ALTER TABLE audit_logs ADD COLUMN beforeValue TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE audit_logs ADD COLUMN afterValue TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN employeesEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN currentEmployeeId INTEGER")
        }
    }

    /** v7 → v8: أصناف المخزون وبنود الفاتورة دون تكرار القيود المالية. */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    shopId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    sku TEXT NOT NULL,
                    unit TEXT NOT NULL,
                    sellPriceMinor INTEGER NOT NULL,
                    costPriceMinor INTEGER NOT NULL,
                    qtyMilli INTEGER NOT NULL,
                    reorderQtyMilli INTEGER NOT NULL,
                    trackStock INTEGER NOT NULL,
                    archived INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(shopId) REFERENCES shops(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_items_shopId ON items(shopId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_items_shopId_sku ON items(shopId, sku)")
            // ملاحظة: تفرد رمز الصنف (sku) غير الفارغ يُفرض في طبقة المستودع (countSku)؛
            // لا ننشئ فهرسًا جزئيًا إضافيًا هنا حتى يطابق مخطط الكيان مخططَ التثبيت الجديد.
            db.execSQL("CREATE INDEX IF NOT EXISTS index_items_name ON items(name)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS document_lines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    documentId INTEGER NOT NULL,
                    itemId INTEGER NOT NULL,
                    itemName TEXT NOT NULL,
                    qtyMilli INTEGER NOT NULL,
                    unitPriceMinor INTEGER NOT NULL,
                    lineTotalMinor INTEGER NOT NULL,
                    trackStock INTEGER NOT NULL,
                    FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_document_lines_documentId ON document_lines(documentId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_document_lines_itemId ON document_lines(itemId)")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        MIGRATION_6_7, MIGRATION_7_8
    )
}
