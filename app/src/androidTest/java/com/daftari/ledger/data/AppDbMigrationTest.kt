package com.daftari.ledger.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبار مسار الترحيل الكامل 1→8.
 *
 * ملاحظة: لا نستخدم runMigrationsAndValidate للمسارات التي تنشئ جدول FTS خارجي
 * (`parties_fts`) لأن أداة التحقق تعتبره جدولًا «غير متوقع». لذلك نُشغّل كائنات
 * Migration يدويًا على قاعدة أنشأها helper.createDatabase ونتحقق من بقاء البيانات
 * وسلامة FTS ومحفزاته، مع إبقاء اختبار تحقق مخطط مستقل للمسار 1→2 (بلا FTS).
 */
@RunWith(AndroidJUnit4::class)
class AppDbMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDb::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun cleanUp() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate1To8PreservesPartiesAndBuildsFtsIndex() {
        val db = helper.createDatabase(TEST_DB, 1)
        try {
            db.execSQL(
                "INSERT INTO shops (id,name,phone,address,currencyCode,fractionDigits,archived,createdAt) " +
                    "VALUES (1,'متجر','','','SAR',2,0,1)"
            )
            db.execSQL(
                """
                INSERT INTO parties
                    (id,shopId,kind,name,phone,address,notes,category,openingMinor,cachedBalanceMinor,deletedAt,createdAt)
                VALUES (7,1,'CUSTOMER','أحمد للتجارة','777','','عميل مهم','عادي',0,0,NULL,1)
                """.trimIndent()
            )

            Migrations.ALL.forEach { it.migrate(db) }

            db.query("SELECT creditLimitMinor FROM parties WHERE id = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0L, cursor.getLong(0))
            }
            db.query("SELECT name FROM parties_fts WHERE parties_fts MATCH 'أحمد*'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("أحمد للتجارة", cursor.getString(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationFtsTriggersTrackUpdates() {
        val db = helper.createDatabase(TEST_DB, 2)
        try {
            Migrations.MIGRATION_2_3.migrate(db)

            db.execSQL(
                "INSERT INTO shops (id,name,phone,address,currencyCode,fractionDigits,archived,createdAt) " +
                    "VALUES (1,'متجر','','','SAR',2,0,1)"
            )
            db.execSQL(
                """
                INSERT INTO parties
                    (id,shopId,kind,name,phone,address,notes,category,openingMinor,cachedBalanceMinor,deletedAt,createdAt,creditLimitMinor)
                VALUES (8,1,'CUSTOMER','الاسم القديم','','','','عادي',0,0,NULL,1,0)
                """.trimIndent()
            )
            db.execSQL("UPDATE parties SET name = 'الاسم الجديد' WHERE id = 8")
            db.query("SELECT COUNT(*) FROM parties_fts WHERE parties_fts MATCH 'الجديد*'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate1To2AddsCreditLimitColumn() {
        val db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT INTO shops (id,name,phone,address,currencyCode,fractionDigits,archived,createdAt) " +
                "VALUES (1,'متجر','','','SAR',2,0,1)"
        )
        db.execSQL(
            """
            INSERT INTO parties
                (id,shopId,kind,name,phone,address,notes,category,openingMinor,cachedBalanceMinor,deletedAt,createdAt)
            VALUES (7,1,'CUSTOMER','أحمد للتجارة','','','','عادي',0,0,NULL,1)
            """.trimIndent()
        )
        db.close()

        // هذا المسار لا ينشئ FTS؛ نستخدم تحقق المخطط الآمن من Room.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2).use { migrated ->
            migrated.query("SELECT creditLimitMinor FROM parties WHERE id = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0L, cursor.getLong(0))
            }
        }
    }

    @Test
    fun migrate8To9AddsAccountsBookTablesAndCurrencies() {
        val db = helper.createDatabase(TEST_DB, 8)
        db.execSQL(
            "INSERT INTO shops (id,name,phone,address,currencyCode,fractionDigits,archived,createdAt,nextDocumentNumber) " +
                "VALUES (1,'متجر','','','SAR',2,0,1,1)"
        )
        db.execSQL(
            "INSERT INTO app_settings " +
                "(id,fiscalEnabled,hideBalances,uniqueDocPerParty,autoBackupEnabled,autoBackupKeep," +
                "biometricUnlock,latinDigits,failedPinAttempts,pinLockedUntil,employeesEnabled) " +
                "VALUES (1,0,0,1,0,7,0,1,0,0,0)"
        )
        db.close()

        // هذا المسار لا ينشئ FTS؛ نستخدم تحقق المخطط الآمن من Room مقابل 9.json.
        helper.runMigrationsAndValidate(TEST_DB, 9, true, Migrations.MIGRATION_8_9).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM currencies").use { cursor ->
                cursor.moveToFirst()
                assertTrue(cursor.getInt(0) >= 4)
            }
            migrated.query("SELECT COUNT(*) FROM currencies WHERE code = 'LOCAL' AND symbol = ''").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT defaultCurrencyId FROM app_settings WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertFalse(cursor.isNull(0))
            }
            migrated.query("SELECT COUNT(*) FROM book_persons").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM book_entries").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
