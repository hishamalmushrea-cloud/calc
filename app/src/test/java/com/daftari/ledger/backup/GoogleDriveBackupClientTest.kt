package com.daftari.ledger.backup

import java.nio.file.Files
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleDriveBackupClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GoogleDriveBackupClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = GoogleDriveBackupClient(
            OkHttpClient(),
            server.url("/drive/v3").toString().trimEnd('/'),
            server.url("/upload/drive/v3").toString().trimEnd('/'),
            server.url("/userinfo").toString()
        )
    }

    @After fun tearDown() = server.shutdown()

    @Test fun listParsesBackupMetadataAndUsesAppDataFolder() {
        server.enqueue(MockResponse().setBody("""{
          "files":[{
            "id":"file-1","name":"daftari-cloud-1.dfb","size":"4096",
            "createdTime":"2026-08-23T02:15:00Z","modifiedTime":"2026-08-23T02:15:01Z",
            "appProperties":{"formatVersion":"1","databaseVersion":"7","appVersion":"1.3.0",
            "createdAt":"1787451300000","deviceId":"device","deviceName":"Phone","datasetId":"dataset",
            "contentSha256":"abc","databaseSha256":"def","conflict":"false"}
          }]}
        """))
        val result = client.list("token")
        assertEquals(1, result.size)
        assertEquals("file-1", result.single().id)
        assertEquals(7, result.single().databaseVersion)
        val request = server.takeRequest()
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertTrue(request.requestUrl!!.queryParameter("spaces") == "appDataFolder")
    }

    @Test fun resumableUploadSendsMetadataThenVerifiesUploadedFile() {
        val root = Files.createTempDirectory("drive-upload-test").toFile()
        try {
            val db = root.resolve("db").apply { writeBytes(ByteArray(20_000) { (it % 199).toByte() }) }
            val created = BackupArchive.create(db, root.resolve("backup.dfb")) { hash, size -> manifest(hash, size) }
            server.enqueue(MockResponse().setResponseCode(200).addHeader("Location", server.url("/session/1")))
            server.enqueue(MockResponse().setResponseCode(200).setBody(remoteJson(created)))
            val remote = client.upload("token", created)
            assertEquals("uploaded", remote.id)
            assertEquals(created.file.length(), remote.size)
            val start = server.takeRequest()
            assertEquals("POST", start.method)
            assertTrue(start.body.readUtf8().contains("appDataFolder"))
            val upload = server.takeRequest()
            assertEquals("PUT", upload.method)
            assertEquals(created.file.length(), upload.bodySize)
        } finally { root.deleteRecursively() }
    }

    @Test fun downloadRejectsWrongChecksumWithoutReplacingDestination() {
        val root = Files.createTempDirectory("drive-download-test").toFile()
        try {
            val bytes = "not-the-expected-backup".toByteArray()
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
            val destination = root.resolve("backup.dfb").apply { writeText("keep") }
            val remote = remote(size = bytes.size.toLong(), hash = "00".repeat(32))
            val result = runCatching { client.download("token", remote, destination) }
            assertTrue(result.isFailure)
            assertEquals("keep", destination.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun rateLimitIsRetryable() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate limit"}}"""))
        val error = runCatching { client.list("token") }.exceptionOrNull() as DriveBackupException
        assertTrue(error.retryable)
        assertEquals(429, error.statusCode)
    }

    private fun manifest(hash: String, size: Long) = BackupManifest(
        appVersion = "1.3.0", databaseVersion = 7, createdAt = 1000, dataModifiedAt = 900,
        deviceId = "device", deviceName = "Phone", datasetId = "dataset",
        databaseSha256 = hash, databaseBytes = size, rowCounts = emptyMap()
    )

    private fun remoteJson(created: BackupArchive.Created) = """{
      "id":"uploaded","name":"backup.dfb","size":"${created.file.length()}",
      "createdTime":"2026-08-23T02:15:00Z","modifiedTime":"2026-08-23T02:15:01Z",
      "sha256Checksum":"${created.contentSha256}",
      "appProperties":{"formatVersion":"1","databaseVersion":"7","appVersion":"1.3.0","createdAt":"1000",
      "deviceId":"device","deviceName":"Phone","datasetId":"dataset","databaseSha256":"${created.manifest.databaseSha256}",
      "contentSha256":"${created.contentSha256}","conflict":"false"}
    }"""

    private fun remote(size: Long, hash: String) = RemoteBackup(
        "id", "backup.dfb", 1, "", size, "device", "Phone", "dataset", null,
        7, "1.3.0", hash, "", false
    )
}
