package com.daftari.ledger

import android.app.Application
import com.daftari.ledger.backup.AutoBackupWorker
import com.daftari.ledger.backup.BackupManager
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DaftariApp : Application() {
    // نطاق تطبيق منظّم بدل Coroutine عشوائي لا يُلغى.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repo: LedgerRepository
        private set
    lateinit var backup: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDb.get(this)
        repo = LedgerRepository(db)
        backup = BackupManager(this, db)
        appScope.launch {
            val on = runCatching { repo.settings.get()?.autoBackupEnabled == true }
                .getOrDefault(false)
            AutoBackupWorker.schedule(this@DaftariApp, on)
        }
    }
}
