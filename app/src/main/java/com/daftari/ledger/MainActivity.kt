package com.daftari.ledger

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.daftari.ledger.ui.DaftariRoot
import com.daftari.ledger.ui.MainViewModel
import com.daftari.ledger.ui.theme.DaftariTheme

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            DaftariTheme {
                val s by vm.state.collectAsState()

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
                    startActivity(Intent.createChooser(send, "مشاركة"))
                    vm.consumeShare()
                }

                // مشاركة نص (كشف حساب)
                LaunchedEffect(s.shareText) {
                    val text = s.shareText ?: return@LaunchedEffect
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(send, "مشاركة الكشف"))
                    vm.consumeShareText()
                }

                // بعد استعادة نسخة احتياطية: إعادة تشغيل التطبيق نظيفة لتُبنى على القاعدة الجديدة.
                LaunchedEffect(s.restartRequested) {
                    if (s.restartRequested) {
                        vm.consumeRestart()
                        restartApp()
                    }
                }

                DaftariRoot(s, vm, this@MainActivity)
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
}
