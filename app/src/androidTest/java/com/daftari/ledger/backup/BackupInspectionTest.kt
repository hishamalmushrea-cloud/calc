package com.daftari.ledger.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daftari.ledger.data.AccountsBookRepository
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.Migrations
import com.daftari.ledger.domain.BookEntryKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * فحص النسخة الاحتياطية على قاعدة بيانات حقيقية في ملف.
 *
 * يغطي ما لا تغطيه اختبارات JVM: أن الفحص يقرأ قاعدة فعلية بكل جداولها، وأنه
 * **يرفض** نسخة فقدت جداول دفتر الحسابات بدل أن يمرّرها بصمت فيمحو الاستبدال
 * بيانات الدفتر. قاعدة الاختبار ملف مستقل تمامًا ولا تلمس `daftari.db`.
 */
@RunWith(AndroidJUnit4::class)
class BackupInspectionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "backup-inspection-${System.nanoTime()}.db"
    private val safetyDir = File(context.filesDir, "backup-inspection-safety")
    private var db: AppDb? = null

    @After
    fun tearDown() {
        runCatching { db?.takeIf { it.isOpen }?.close() }
        context.deleteDatabase(dbName)
        safetyDir.deleteRecursively()
    }

    /** قاعدة ملفية فيها محل وعملات وبضع عمليات دفتر، ثم تُغلق وتُعاد كملف. */
    private fun seededDatabaseFile(): File = runBlocking {
        val database = Room.databaseBuilder(context, AppDb::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
        db = database
        val ledger = LedgerRepository(database)
        val book = AccountsBookRepository(database)
        ledger.ensureSettings()
        val shopId = ledger.createShop("محل الفحص")
        book.ensureSeeded()
        val personId = book.addPerson(shopId, "محمد")
        val currencyId = book.defaultCurrency("SAR")!!.id
        book.addEntry(personId, currencyId, BookEntryKind.DEBT, 5_000L)
        book.addEntry(personId, currencyId, BookEntryKind.SETTLEMENT, 2_000L)
        database.close()
        context.getDatabasePath(dbName)
    }

    @Test
    fun inspectionCoversAccountsBookTables() {
        val inspection = BackupManager.inspectDatabase(seededDatabaseFile())

        assertEquals(AppDb.VERSION, inspection.version)
        assertTrue(inspection.integrityOk)
        assertTrue(inspection.foreignKeysOk)
        assertEquals(1L, inspection.rowCounts["book_persons"])
        assertEquals(2L, inspection.rowCounts["book_entries"])
        assertTrue(inspection.rowCounts.getValue("currencies") >= 4L)
        assertTrue(inspection.latestDataChangeAt > 0L)
    }

    @Test
    fun missingAccountsBookTableIsRejected() {
        val file = seededDatabaseFile()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            sqlite.execSQL("DROP TABLE book_entries")
        }

        val error = runCatching { BackupManager.inspectDatabase(file) }.exceptionOrNull()

        assertNotNull("نسخة فقدت جدول عمليات الدفتر يجب أن تُرفض", error)
        assertTrue(error!!.message.orEmpty(), error.message.orEmpty().contains("missing required data tables"))
    }

    @Test
    fun safetyCopiesBeyondTheLimitArePruned() {
        safetyDir.mkdirs()
        repeat(5) { index ->
            File(safetyDir, "pre-restore-$index.db").apply {
                writeText("snapshot")
                setLastModified(1_700_000_000_000L + index * 1_000L)
            }
        }

        val deleted = BackupManager.pruneSafetyCopiesIn(safetyDir, keep = 2)

        assertEquals(3, deleted.size)
        assertEquals(listOf("pre-restore-3.db", "pre-restore-4.db"), safetyDir.list()?.sorted())
    }
}
