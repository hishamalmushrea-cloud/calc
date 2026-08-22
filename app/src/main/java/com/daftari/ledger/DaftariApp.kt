package com.daftari.ledger

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.daftari.ledger.backup.AutoBackupWorker
import com.daftari.ledger.backup.BackupManager
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DaftariApp : Application() {
    lateinit var repo: LedgerRepository
        private set
    lateinit var backup: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDb.get(this)
        repo = LedgerRepository(db)
        backup = BackupManager(this, db)

        // نطاق مرتبط بدورة حياة عملية التطبيق بدل CoroutineScope دائم غير مُدار.
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            val enabled = runCatching { repo.settings.get()?.autoBackupEnabled == true }
                .getOrDefault(false)
            AutoBackupWorker.schedule(this@DaftariApp, enabled)
        }
    }
}
