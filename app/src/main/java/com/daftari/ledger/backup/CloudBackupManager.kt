package com.daftari.ledger.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** نسخ سحابي اختياري: مجلد SAF (ومن ضمنه Google Drive) وWebDAV. */
class CloudBackupManager(private val context: Context, private val backup: BackupManager) {
    data class Settings(
        val treeUri: String = "",
        val webDavUrl: String = "",
        val webDavUser: String = "",
        val hasWebDavPassword: Boolean = false
    )

    data class UploadResult(val driveUploaded: Boolean, val webDavUploaded: Boolean) {
        val anyUploaded get() = driveUploaded || webDavUploaded
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "cloud_backup",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val http = OkHttpClient.Builder().build()

    fun settings(): Settings = Settings(
        treeUri = prefs.getString(KEY_TREE, "").orEmpty(),
        webDavUrl = prefs.getString(KEY_URL, "").orEmpty(),
        webDavUser = prefs.getString(KEY_USER, "").orEmpty(),
        hasWebDavPassword = !prefs.getString(KEY_PASSWORD, "").isNullOrBlank()
    )

    fun saveTreeUri(uri: Uri) {
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun clearTreeUri() {
        prefs.edit().remove(KEY_TREE).apply()
    }

    /**
     * هل مجلد النسخ الاختياري ما زال قابلًا للوصول؟ عدم ضبط مجلد ليس مشكلة (يعيد
     * `true`)، أما مجلد مضبوط لكنه محذوف/فاقِد الصلاحية فيعيد `false` لينبّه العامل.
     */
    fun isTreeAccessible(): Boolean {
        val uri = settings().treeUri.takeIf { it.isNotBlank() } ?: return true
        return runCatching {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(uri))
            dir != null && dir.exists()
        }.getOrDefault(false)
    }

    fun saveWebDav(url: String, user: String, password: String) {
        val edit = prefs.edit()
            .putString(KEY_URL, url.trim().trimEnd('/'))
            .putString(KEY_USER, user.trim())
        if (password.isNotBlank()) edit.putString(KEY_PASSWORD, password)
        edit.apply()
    }

    fun clearWebDav() {
        prefs.edit().remove(KEY_URL).remove(KEY_USER).remove(KEY_PASSWORD).apply()
    }

    suspend fun backupNow(): Pair<File, UploadResult> {
        val file = backup.exportDatabase()
        return file to upload(file)
    }

    suspend fun upload(file: File): UploadResult = withContext(Dispatchers.IO) {
        val settings = settings()
        val drive = settings.treeUri.takeIf { it.isNotBlank() }?.let {
            runCatching { uploadToTree(Uri.parse(it), file) }.getOrDefault(false)
        } ?: false
        val webDav = settings.webDavUrl.takeIf { it.isNotBlank() }?.let {
            runCatching { uploadWebDav(settings, file) }.getOrDefault(false)
        } ?: false
        UploadResult(drive, webDav)
    }

    suspend fun restoreFromUri(uri: Uri) {
        backup.restoreFromUri(uri)
    }

    suspend fun restoreLatestWebDav() = withContext(Dispatchers.IO) {
        val settings = settings()
        check(settings.webDavUrl.isNotBlank()) { "WebDAV is not configured" }
        val latest = latestRestorableWebDavBackup(listWebDav(settings)) ?: error("No WebDAV backups")
        val request = requestBuilder(settings, settings.webDavUrl + "/" + encode(latest)).get().build()
        val temporary = File.createTempFile("webdav-restore-", ".db", context.cacheDir)
        try {
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "WebDAV download failed: ${response.code}" }
                response.body?.byteStream()?.use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Empty WebDAV backup")
            }
            backup.restoreFrom(temporary)
        } finally {
            temporary.delete()
        }
    }

    private fun uploadToTree(treeUri: Uri, file: File): Boolean {
        val directory = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        directory.findFile(file.name)?.delete()
        val target = directory.createFile("application/vnd.sqlite3", file.name) ?: return false
        context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        } ?: return false
        return true
    }

    private fun uploadWebDav(settings: Settings, file: File): Boolean {
        val target = settings.webDavUrl + "/" + encode(file.name)
        val request = requestBuilder(settings, target)
            .put(file.asRequestBody("application/vnd.sqlite3".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "WebDAV upload failed: ${response.code}" }
        }
        return true
    }

    private fun listWebDav(settings: Settings): List<String> {
        val body = "".toRequestBody(null)
        val request = requestBuilder(settings, settings.webDavUrl)
            .header("Depth", "1")
            .method("PROPFIND", body)
            .build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "WebDAV listing failed: ${response.code}" }
            val xml = response.body?.string().orEmpty()
            HREF.findAll(xml).mapNotNull { match ->
                URLDecoder.decode(match.groupValues[1].substringAfterLast('/'), StandardCharsets.UTF_8.name())
                    .takeIf { it.startsWith("daftari-backup-") && (it.endsWith(".db") || it.endsWith(".enc")) }
            }.toList()
        }
    }

    private fun requestBuilder(settings: Settings, url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (settings.webDavUser.isNotBlank()) {
            builder.header(
                "Authorization",
                Credentials.basic(settings.webDavUser, prefs.getString(KEY_PASSWORD, "").orEmpty(), StandardCharsets.UTF_8)
            )
        }
        return builder
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_URL = "webdav_url"
        const val KEY_USER = "webdav_user"
        const val KEY_PASSWORD = "webdav_password"
        val HREF = Regex("<(?:[^:>]+:)?href[^>]*>(.*?)</(?:[^:>]+:)?href>", RegexOption.IGNORE_CASE)
    }
}

/**
 * يختار أحدث نسخة WebDAV قابلة للاستعادة.
 *
 * يُستبعد المشفّر (`.enc`) لأنه يحتاج كلمة مرور ولا يمرّ بهذا المسار، والترتيب بالطابع
 * الزمني في الاسم لا بالترتيب الأبجدي (الذي يقدّم `999999999` على `1700000000000`).
 * عامة حتى تختبرها `CloudBackupManagerTest` بلا شبكة.
 */
fun latestRestorableWebDavBackup(names: List<String>): String? = names
    .filter { it.startsWith("daftari-backup-") && it.endsWith(".db") }
    .maxByOrNull { name ->
        name.substringAfter("daftari-backup-").substringBefore(".").toLongOrNull() ?: 0L
    }
