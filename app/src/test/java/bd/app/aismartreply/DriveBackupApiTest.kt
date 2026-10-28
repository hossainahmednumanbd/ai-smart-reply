package bd.app.aismartreply

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DriveBackupApiTest {
    @Test fun listsOnlyThisAppsPrivateSpace() {
        var requested = ""
        val api = DriveBackupApi("test-token") { url, method, _, _ ->
            requested = url; assertEquals("GET", method)
            """{"files":[{"id":"abc_123","modifiedTime":"2026-10-28T00:00:00Z"}]}"""
        }
        assertEquals("abc_123", api.list().single().id)
        assertTrue(requested.startsWith("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder"))
        assertTrue(requested.contains("ai-smart-reply-backup-"))
        assertFalse(requested.contains("test-token"))
    }
    @Test fun uploadsImmutablePrivateSnapshotsInsteadOfOverwritingOtherPhones() {
        val bodies = mutableListOf<String>()
        val api = DriveBackupApi("test-token") { url, method, type, body ->
            assertTrue(url.startsWith("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"))
            assertEquals("POST", method); assertTrue(type!!.startsWith("multipart/related"))
            bodies += String(body!!, Charsets.UTF_8); "{}"
        }
        api.upload("{\"facts\":\"বাংলা\"}"); api.upload("{\"facts\":\"বাংলা\"}")
        assertNotEquals(bodies[0], bodies[1])
        assertTrue(bodies.all { it.contains("\"parents\":[\"appDataFolder\"]") && !it.contains("test-token") })
    }
    @Test fun badFileIdsAndOversizedSnapshotsNeverReachNetwork() {
        var calls = 0
        val api = DriveBackupApi("test-token") { _, _, _, _ -> calls++; "{}" }
        assertTrue(runCatching { api.download("../other") }.isFailure)
        assertTrue(runCatching { api.upload("x".repeat(BackupData.MAX_BYTES + 1)) }.isFailure)
        assertEquals(0, calls)
    }
    @Test fun providerErrorsAreActionableWithoutExposingRawDetails() {
        val disabled = """{"error":{"details":[{"reason":"SERVICE_DISABLED"}],"message":"private-token"}}"""
        assertTrue(DriveBackupApi.failureMessage(403, disabled).contains("Drive API চালু নেই"))
        assertFalse(DriveBackupApi.failureMessage(403, disabled).contains("private-token"))
        assertTrue(DriveBackupApi.failureMessage(403, """{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""").contains("জায়গা শেষ"))
        assertTrue(DriveBackupApi.failureMessage(403, """{"error":{"details":[{"reason":"ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}""").contains("অনুমতি সম্পূর্ণ হয়নি"))
        assertTrue(DriveBackupApi.failureMessage(403, "not json secret").contains("403"))
        assertFalse(DriveBackupApi.failureMessage(403, "not json secret").contains("secret"))
    }
}
