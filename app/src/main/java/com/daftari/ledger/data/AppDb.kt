package com.daftari.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShopEntity::class, PartyEntity::class, AccountEntity::class,
        DocumentEntity::class, JournalLineEntity::class, CategoryEntity::class,
        AuditLogEntity::class, SettingsEntity::class, DailyClosingEntity::class,
        DailyBookEntity::class, EmployeeEntity::class, EmployeeShopEntity::class,
        EmployeeShiftEntity::class, ItemEntity::class, DocumentLineEntity::class,
        CurrencyEntity::class, BookPersonEntity::class, BookEntryEntity::class
    ],
    version = AppDb.VERSION,
    exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun shops(): ShopDao
    abstract fun parties(): PartyDao
    abstract fun accounts(): AccountDao
    abstract fun documents(): DocumentDao
    abstract fun journal(): JournalDao
    abstract fun categories(): CategoryDao
    abstract fun audit(): AuditDao
    abstract fun settings(): SettingsDao
    abstract fun closings(): ClosingDao
    abstract fun dailyBooks(): DailyBookDao
    abstract fun employees(): EmployeeDao
    abstract fun employeeShops(): EmployeeShopDao
    abstract fun employeeShifts(): EmployeeShiftDao
    abstract fun items(): ItemDao
    abstract fun documentLines(): DocumentLineDao
    abstract fun currencies(): CurrencyDao
    abstract fun bookPersons(): BookPersonDao
    abstract fun bookEntries(): BookEntryDao

    companion object {
        const val VERSION = 9
        @Volatile private var I: AppDb? = null

        fun get(ctx: Context): AppDb = I ?: synchronized(this) {
            I ?: build(ctx.applicationContext).also { I = it }
        }

        private val searchIndexCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                PartySearchIndex.ensure(db)
                CurrencySeeds.ensure(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // يصلح تلقائيًا أي تثبيت قديم فُقد منه جدول FTS دون لمس البيانات.
                PartySearchIndex.ensure(db)
                // يضمن وجود عملات دفتر الحسابات حتى بعد استعادة نسخة بلا بذور.
                CurrencySeeds.ensure(db)
            }
        }

        private fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "daftari.db")
                // لا fallbackToDestructiveMigration — أي ترقية تحتاج Migration صريحة
                .addMigrations(*Migrations.ALL)
                .addCallback(searchIndexCallback)
                .build()

        /**
         * بعد استبدال ملف قاعدة البيانات على القرص (استعادة نسخة احتياطية)،
         * يجب إغلاق الاتصال الحالي وإسقاط الـ singleton حتى يُعاد فتح القاعدة
         * الجديدة عند أول استخدام بدل الانهطار بقاعدة مغلقة/قديمة.
         */
        fun invalidate(ctx: Context) {
            synchronized(this) {
                I?.close()
                I = null
                // نعيد بناء الاتصال فورًا ليكون جاهزًا قبل الاستخدام التالي.
                I = build(ctx.applicationContext)
            }
        }
    }
}
