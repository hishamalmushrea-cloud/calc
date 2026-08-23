package com.daftari.ledger.backup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class DriveBackupException(
    message: String,
    val statusCode: Int = 0,
    val retryable: Boolean = false,
    val authorizationRequired: Boolean = false
) : Exception(message)

class GoogleDriveBackupClient(private val http: OkHttpClient = OkHttpClient()) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val backupMedia = CLOUD_BACKUP_MIME.toMediaType()

    data class GoogleUser(val subject: String, val email: String)

    fun userInfo(accessToken: String): GoogleUser {
        val request = authorized(Request.Builder().url("https://www.googleapis.com/oauth2/v3/userinfo"), accessToken).get().build()
        return execute(request) { body ->
            val json = JsonParser.parseString(body).asJsonObject
            GoogleUser(json.get("sub")?.asString.orEmpty(), json.get("email")?.asString.orEmpty())
        }
    }

    fun list(accessToken: String): List<RemoteBackup> {
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "mimeType = '$CLOUD_BACKUP_MIME'")
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("pageSize", "100")
            .addQueryParameter("fields", "files(id,name,size,createdTime,modifiedTime,md5Checksum,sha256Checksum,appProperties)")
            .build()
        val request = authorized(Request.Builder().url(url), accessToken).get().build()
        return execute(request) { body ->
            val root = JsonParser.parseString(body).asJsonObject
            root.getAsJsonArray("files")?.mapNotNull { element -> runCatching { parseRemote(element.asJsonObject) }.getOrNull() }.orEmpty()
        }
    }

    fun upload(accessToken: String, archive: BackupArchive.Created): RemoteBackup {
        val manifest = archive.manifest
        val name = "$CLOUD_BACKUP_PREFIX${manifest.createdAt}-${manifest.deviceId.take(8)}.dfb"
        val metadata = JsonObject().apply {
            addProperty("name", name)
            addProperty("mimeType", CLOUD_BACKUP_MIME)
            add("parents", com.google.gson.JsonArray().apply { add("appDataFolder") })
            add("appProperties", JsonObject().apply {
                addProperty("formatVersion", manifest.formatVersion.toString())
                addProperty("appVersion", manifest.appVersion)
                addProperty("databaseVersion", manifest.databaseVersion.toString())
                addProperty("createdAt", manifest.createdAt.toString())
                addProperty("dataModifiedAt", manifest.dataModifiedAt.toString())
                addProperty("deviceId", manifest.deviceId)
                addProperty("deviceName", manifest.deviceName.take(80))
                addProperty("datasetId", manifest.datasetId)
                addProperty("parentBackupId", manifest.parentBackupId.orEmpty())
                addProperty("databaseSha256", manifest.databaseSha256)
                addProperty("contentSha256", archive.contentSha256)
                addProperty("conflict", manifest.conflict.toString())
            })
        }
        val startUrl = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", "id,name,size,createdTime,modifiedTime,md5Checksum,sha256Checksum,appProperties")
            .build()
        val start = authorized(Request.Builder().url(startUrl), accessToken)
            .header("X-Upload-Content-Type", CLOUD_BACKUP_MIME)
            .header("X-Upload-Content-Length", archive.file.length().toString())
            .post(metadata.toString().toRequestBody(jsonMedia)).build()
        val session = http.newCall(start).execute().use { response ->
            if (!response.isSuccessful) throw driveError(response.code, response.body?.string().orEmpty())
            response.header("Location") ?: throw DriveBackupException("Google Drive did not create an upload session", retryable = true)
        }
        val upload = authorized(Request.Builder().url(session), accessToken)
            .put(archive.file.asRequestBody(backupMedia)).build()
        return http.newCall(upload).execute().use { response ->
            if (!response.isSuccessful) throw driveError(response.code, response.body?.string().orEmpty())
            val remote = parseRemote(JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject)
            if (remote.size != archive.file.length()) throw DriveBackupException("Uploaded backup size verification failed", retryable = true)
            if (remote.contentSha256.isNotBlank() && !remote.contentSha256.equals(archive.contentSha256, true)) {
                throw DriveBackupException("Uploaded backup checksum verification failed", retryable = true)
            }
            remote
        }
    }

    fun download(accessToken: String, remote: RemoteBackup, destination: File): File {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".partial")
        val url = "https://www.googleapis.com/drive/v3/files/${remote.id}".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media").build()
        val request = authorized(Request.Builder().url(url), accessToken).get().build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw driveError(response.code, response.body?.string().orEmpty())
                response.body?.byteStream()?.use { input -> temporary.outputStream().buffered().use { input.copyTo(it) } }
                    ?: throw DriveBackupException("Google Drive returned an empty backup", retryable = true)
            }
            if (remote.size > 0 && temporary.length() != remote.size) throw DriveBackupException("Downloaded backup size does not match", retryable = true)
            if (remote.contentSha256.isNotBlank() && !BackupArchive.sha256(temporary).equals(remote.contentSha256, true)) {
                throw DriveBackupException("Downloaded backup checksum does not match")
            }
            if (!temporary.renameTo(destination)) temporary.copyTo(destination, true)
            return destination
        } finally {
            temporary.delete()
        }
    }

    fun delete(accessToken: String, remoteId: String) {
        val request = authorized(Request.Builder().url("https://www.googleapis.com/drive/v3/files/$remoteId"), accessToken)
            .delete().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) throw driveError(response.code, response.body?.string().orEmpty())
        }
    }

    private fun authorized(builder: Request.Builder, token: String): Request.Builder = builder
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")

    private fun <T> execute(request: Request, parser: (String) -> T): T = http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw driveError(response.code, body)
        parser(body)
    }

    private fun parseRemote(file: JsonObject): RemoteBackup {
        val props = file.getAsJsonObject("appProperties") ?: JsonObject()
        fun prop(name: String) = props.get(name)?.asString.orEmpty()
        val created = prop("createdAt").toLongOrNull()
            ?: file.get("createdTime")?.asString?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        return RemoteBackup(
            id = file.get("id")?.asString.orEmpty(),
            name = file.get("name")?.asString.orEmpty(),
            createdAt = created,
            modifiedAt = file.get("modifiedTime")?.asString.orEmpty(),
            size = file.get("size")?.asString?.toLongOrNull() ?: 0,
            deviceId = prop("deviceId"),
            deviceName = prop("deviceName"),
            datasetId = prop("datasetId"),
            parentBackupId = prop("parentBackupId").ifBlank { null },
            databaseVersion = prop("databaseVersion").toIntOrNull() ?: 0,
            appVersion = prop("appVersion"),
            contentSha256 = file.get("sha256Checksum")?.asString.orEmpty().ifBlank { prop("contentSha256") },
            databaseSha256 = prop("databaseSha256"),
            conflict = prop("conflict").toBooleanStrictOrNull() ?: false,
            valid = file.get("id")?.asString?.isNotBlank() == true && prop("formatVersion") == "1"
        )
    }

    private fun driveError(code: Int, body: String): DriveBackupException {
        val reason = runCatching {
            JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")?.get("message")?.asString
        }.getOrNull().orEmpty()
        val message = when (code) {
            401 -> "Google authorization expired"
            403 -> if (reason.contains("quota", true)) "Google Drive storage quota is full" else "Google Drive access was denied"
            404 -> "The selected backup no longer exists"
            429 -> "Google Drive is temporarily busy"
            in 500..599 -> "Google Drive is temporarily unavailable"
            else -> reason.ifBlank { "Google Drive request failed ($code)" }
        }
        return DriveBackupException(message, code, retryable = code == 429 || code in 500..599, authorizationRequired = code == 401)
    }
}
