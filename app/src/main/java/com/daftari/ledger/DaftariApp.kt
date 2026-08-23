package com.daftari.ledger

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.daftari.ledger.backup.AutoBackupWorker
import com.daftari.ledger.backup.BackupManager
import com.daftari.ledger.backup.CloudBackupManager
import com.daftari.ledger.backup.GoogleBackupManager
import com.daftari.ledger.backup.GoogleBackupPreferences
import com.daftari.ledger.data.AppDb
import com.daftari.ledger.data.LedgerRepository
import com.daftari.ledger.data.StaffRepository
import com.daftari.ledger.reminder.DueReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DaftariApp : Application() {
    lateinit var repo: LedgerRepository
        private set
    lateinit var backup: BackupManager
        private set
    lateinit var cloudBackup: CloudBackupManager
        private set
    lateinit var googleBackup: GoogleBackupManager
        private set
    lateinit var staff: StaffRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDb.get(this)
        repo = LedgerRepository(db)
        staff = StaffRepository(db)
        backup = BackupManager(this, db)
        cloudBackup = CloudBackupManager(this, backup)
        googleBackup = GoogleBackupManager(this, backup)
        DueReminderWorker.schedule(this)

        // نطاق مرتبط بدورة حياة عملية التطبيق بدل CoroutineScope دائم غير مُدار.
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            val legacyEnabled = runCatching { repo.settings.get()?.autoBackupEnabled == true }.getOrDefault(false)
            val googleSettings = GoogleBackupPreferences(this@DaftariApp).load()
            AutoBackupWorker.schedule(
                this@DaftariApp,
                legacyEnabled || (googleSettings.linked && googleSettings.automaticEnabled),
                googleSettings.wifiOnly
            )
        }
    }
}
