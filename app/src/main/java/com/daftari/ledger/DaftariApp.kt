package com.daftari.ledger

import android.app.Application
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import kotlinx.coroutines.launch

class DaftariApp : Application() {
    lateinit var repo: LedgerRepository
        private set
    lateinit var backup: com.daftari.ledger.backup.BackupManager
        private set
    override fun onCreate() {
        super.onCreate()
        val db = AppDb.get(this)
        repo = LedgerRepository(db)
        backup = com.daftari.ledger.backup.BackupManager(this, db)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val on = runCatching { repo.settings.get()?.autoBackupEnabled == true }.getOrDefault(false)
            com.daftari.ledger.backup.AutoBackupWorker.schedule(this@DaftariApp, on)
        }
    }
}
