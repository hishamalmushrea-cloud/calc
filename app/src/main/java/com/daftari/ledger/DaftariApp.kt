package com.daftari.ledger

import android.app.Application
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import kotlinx.coroutines.launch

class DaftariApp : Application() {
    lateinit var repo: LedgerRepository
        private set
    override fun onCreate() {
        super.onCreate()
        repo = LedgerRepository(AppDb.get(this))
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val on = runCatching { repo.settings.get()?.autoBackupEnabled == true }.getOrDefault(false)
            com.daftari.ledger.backup.AutoBackupWorker.schedule(this@DaftariApp, on)
        }
    }
}
