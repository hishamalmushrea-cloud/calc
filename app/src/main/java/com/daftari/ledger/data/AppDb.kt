package com.daftari.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShopEntity::class, PartyEntity::class, AccountEntity::class,
        DocumentEntity::class, JournalLineEntity::class, AuditLogEntity::class,
        SettingsEntity::class, DailyClosingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun shops(): ShopDao
    abstract fun parties(): PartyDao
    abstract fun accounts(): AccountDao
    abstract fun documents(): DocumentDao
    abstract fun journal(): JournalDao
    abstract fun audit(): AuditDao
    abstract fun settings(): SettingsDao
    abstract fun closings(): ClosingDao

    companion object {
        @Volatile private var I: AppDb? = null
        fun get(ctx: Context): AppDb = I ?: synchronized(this) {
            I ?: Room.databaseBuilder(ctx, AppDb::class.java, "daftari.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { I = it }
        }
    }
}
