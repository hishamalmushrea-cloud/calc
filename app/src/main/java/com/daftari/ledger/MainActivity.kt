package com.daftari.ledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import android.accounts.Account
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.daftari.ledger.backup.GOOGLE_DRIVE_APPDATA_SCOPE
import com.daftari.ledger.backup.GOOGLE_EMAIL_SCOPE
import com.daftari.ledger.backup.GOOGLE_OPENID_SCOPE
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
    private var pendingGoogleAction: String = ""
    private var pendingGoogleEmail: String = ""
    private var pendingGoogleSubject: String = ""
    private val googleAuthorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data) }
            .onSuccess { authorization -> completeGoogleAuthorization(authorization.accessToken) }
            .onFailure { vm.onEvent(UiEvent.GoogleBackupAuthorizationFailed(it.message.orEmpty())) }
    }
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
                            is UiEffect.LinkGoogleBackup -> linkGoogleAccount(effect.action)
                            is UiEffect.AuthorizeGoogleBackup -> authorizeGoogleDrive(effect.action)
                            UiEffect.UnlinkGoogleBackup -> unlinkGoogleAccount()
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

    private suspend fun linkGoogleAccount(action: String) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) {
            vm.onEvent(UiEvent.GoogleBackupAuthorizationFailed(getString(R.string.google_backup_not_configured)))
            return
        }
        runCatching {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(this).getCredential(this, request).credential
            check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            GoogleIdTokenCredential.createFrom(credential.data)
        }.onSuccess { google ->
            pendingGoogleEmail = google.id
            pendingGoogleSubject = google.idToken.split('.').getOrNull(1)?.let(::decodeGoogleSubject).orEmpty()
            pendingGoogleAction = action
            startGoogleAuthorization()
        }.onFailure {
            vm.onEvent(UiEvent.GoogleBackupAuthorizationFailed(it.message ?: getString(R.string.google_sign_in_failed)))
        }
    }

    private fun decodeGoogleSubject(encodedPayload: String): String = runCatching {
        val json = String(android.util.Base64.decode(encodedPayload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
        com.google.gson.JsonParser.parseString(json).asJsonObject.get("sub")?.asString.orEmpty()
    }.getOrDefault("")

    private fun authorizeGoogleDrive(action: String) {
        pendingGoogleAction = action
        pendingGoogleEmail = ""
        pendingGoogleSubject = ""
        startGoogleAuthorization()
    }

    private fun startGoogleAuthorization() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GOOGLE_DRIVE_APPDATA_SCOPE), Scope(GOOGLE_OPENID_SCOPE), Scope(GOOGLE_EMAIL_SCOPE)))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val intent = result.pendingIntent?.intentSender
                    if (intent != null) googleAuthorization.launch(IntentSenderRequest.Builder(intent).build())
                    else vm.onEvent(UiEvent.GoogleBackupAuthorizationFailed(getString(R.string.google_authorization_failed)))
                } else completeGoogleAuthorization(result.accessToken)
            }
            .addOnFailureListener { vm.onEvent(UiEvent.GoogleBackupAuthorizationFailed(it.message.orEmpty())) }
    }

    private fun completeGoogleAuthorization(token: String?) {
        if (token.isNullOrBlank()) {
            vm.onEvent(UiEvent.GoogleBackupAuthorizationFailed(getString(R.string.google_authorization_failed)))
            return
        }
        vm.onEvent(
            UiEvent.GoogleBackupAuthorized(
                pendingGoogleEmail, pendingGoogleSubject, token,
                pendingGoogleAction.ifBlank { "LIST" }
            )
        )
        pendingGoogleAction = ""
        pendingGoogleEmail = ""
        pendingGoogleSubject = ""
    }

    private suspend fun unlinkGoogleAccount() {
        val email = vm.state.value.googleBackup.settings.accountEmail
        if (email.isNotBlank()) {
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(email, "com.google"))
                .setScopes(listOf(Scope(GOOGLE_DRIVE_APPDATA_SCOPE), Scope(GOOGLE_OPENID_SCOPE), Scope(GOOGLE_EMAIL_SCOPE)))
                .build()
            Identity.getAuthorizationClient(this).revokeAccess(request)
        }
        runCatching { CredentialManager.create(this).clearCredentialState(ClearCredentialStateRequest()) }
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
