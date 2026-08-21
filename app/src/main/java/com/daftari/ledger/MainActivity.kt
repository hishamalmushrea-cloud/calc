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
                LaunchedEffect(s.shareFile) {
                    val f = s.shareFile ?: return@LaunchedEffect
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = if (f.name.endsWith(".pdf")) "application/pdf" else "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "مشاركة"))
                    vm.consumeShare()
                }
                LaunchedEffect(s.shareText) {
                    val text = s.shareText ?: return@LaunchedEffect
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(send, "مشاركة الكشف"))
                    vm.consumeShareText()
                }
                DaftariRoot(s, vm, this@MainActivity)
            }
        }
    }
}
