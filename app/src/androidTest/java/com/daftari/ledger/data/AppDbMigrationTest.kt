package com.daftari.ledger.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun migrate1To5PreservesPartiesAndBuildsFtsIndex() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO shops (id,name,phone,address,currencyCode,fractionDigits,archived,createdAt) VALUES (1,'متجر','','','SAR',2,0,1)")
            execSQL(
                """
                INSERT INTO parties
                    (id,shopId,kind,name,phone,address,notes,category,openingMinor,cachedBalanceMinor,deletedAt,createdAt)
                VALUES (7,1,'CUSTOMER','أحمد للتجارة','777','','عميل مهم','عادي',0,0,NULL,1)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, *Migrations.ALL).apply {
            query("SELECT creditLimitMinor FROM parties WHERE id = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0L, cursor.getLong(0))
            }
            query("SELECT name FROM parties_fts WHERE parties_fts MATCH 'أحمد*'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("أحمد للتجارة", cursor.getString(0))
            }
            close()
        }
    }

    @Test
    fun migrationFtsTriggersTrackUpdates() {
        helper.createDatabase(TEST_DB, 2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3).apply {
            execSQL("INSERT INTO shops (id,name,phone,address,currencyCode,fractionDigits,archived,createdAt) VALUES (1,'متجر','','','SAR',2,0,1)")
            execSQL(
                """
                INSERT INTO parties
                    (id,shopId,kind,name,phone,address,notes,category,openingMinor,cachedBalanceMinor,deletedAt,createdAt,creditLimitMinor)
                VALUES (8,1,'CUSTOMER','الاسم القديم','','','','عادي',0,0,NULL,1,0)
                """.trimIndent()
            )
            execSQL("UPDATE parties SET name = 'الاسم الجديد' WHERE id = 8")
            query("SELECT COUNT(*) FROM parties_fts WHERE parties_fts MATCH 'الجديد*'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
