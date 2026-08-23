package com.daftari.ledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.daftari.ledger.ui.DaftariRoot
import com.daftari.ledger.ui.MainViewModel
import com.daftari.ledger.ui.UiEffect
import com.daftari.ledger.ui.UiEvent
import com.daftari.ledger.ui.theme.DaftariTheme
import kotlinx.coroutines.flow.collect

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private val cloudFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // بعض مزودي الملفات يمنحون إذن الجلسة فقط ولا يدعمون الإذن الدائم.
            }
            vm.onEvent(UiEvent.CloudFolderSelected(it.toString()))
        }
    }
    private val backupFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.onEvent(UiEvent.RestoreCloudFile(it.toString())) }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            DaftariTheme {
                val s by vm.state.collectAsState()

                LaunchedEffect(Unit) {
                    vm.effects.collect { effect ->
                        when (effect) {
                            UiEffect.PickCloudFolder -> cloudFolderPicker.launch(null)
                            UiEffect.PickBackupFile -> backupFilePicker.launch(
                                arrayOf("application/vnd.sqlite3", "application/octet-stream", "*/*")
                            )
                            is UiEffect.OpenUri -> runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.uri)))
                            }
                        }
                    }
                }

                // مشاركة ملف (PDF/Excel/CSV/نسخة احتياطية)
                LaunchedEffect(s.shareFile) {
                    val f = s.shareFile ?: return@LaunchedEffect
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = when {
                            f.name.endsWith(".pdf") -> "application/pdf"
                            f.name.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            f.name.endsWith(".csv") -> "text/csv"
                            else -> "*/*"
                        }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, getString(R.string.action_share)))
                    vm.onEvent(UiEvent.ConsumeShareFile)
                }

                // مشاركة نص (كشف حساب)
                LaunchedEffect(s.shareText) {
                    val text = s.shareText ?: return@LaunchedEffect
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(send, getString(R.string.action_share_statement)))
                    vm.onEvent(UiEvent.ConsumeShareText)
                }

                // بعد استعادة نسخة احتياطية: إعادة تشغيل التطبيق نظيفة لتُبنى على القاعدة الجديدة.
                LaunchedEffect(s.restartRequested) {
                    if (s.restartRequested) {
                        vm.onEvent(UiEvent.ConsumeRestart)
                        restartApp()
                    }
                }

                DaftariRoot(
                    s,
                    vm,
                    this@MainActivity,
                    initialTab = if (intent.getBooleanExtra(EXTRA_OPEN_REPORTS, false)) 4 else 0,
                    initialQuickSale = intent.getBooleanExtra(EXTRA_QUICK_SALE, false)
                )
            }
        }
    }

    /**
     * يعيد تشغيل العملية بالكامل؛ يضمن إعادة بناء [DaftariApp] و[AppDb]
     * بعد استبدال ملف القاعدة بدل الاعتماد على مرجع قديم مغلق.
     */
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) {
            startActivity(intent)
        }
        finishAffinity()
        // نُهي العملية كي لا تبقى مراجع قديمة للقاعدة أو الـ Repository في الذاكرة.
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_OPEN_REPORTS = "open_reports"
        const val EXTRA_QUICK_SALE = "quick_sale"
    }
}
