package bd.app.aismartreply

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Direct phone-to-Drive transport; immutable snapshots avoid cross-device overwrites. */
internal class DriveBackupApi(private val token: String, private val transport: ((String, String, String?, ByteArray?) -> String)? = null) {
    data class Entry(val id: String, val time: String)
    private fun request(url: String, method: String = "GET", type: String? = null, body: ByteArray? = null): String {
        transport?.let { return it(url, method, type, body) }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method; conn.connectTimeout = 15000; conn.readTimeout = 30000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true; conn.setRequestProperty("Content-Type", type)
                conn.setFixedLengthStreamingMode(body.size); conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching { conn.errorStream?.use { String(it.readBytesBounded(), Charsets.UTF_8) } }.getOrNull().orEmpty()
                error(failureMessage(code, errorBody))
            }
            return conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    require(output.size() + n <= BackupData.MAX_BYTES) { "ব্যাকআপের আকার বেশি" }; output.write(buffer, 0, n) }
                output.toString("UTF-8")
            }
        } finally { conn.disconnect() }
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(1024)
        while (output.size() < 16384) {
            val n = read(buffer, 0, minOf(buffer.size, 16384 - output.size()))
            if (n < 0) break
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
    companion object {
        // Map only known provider reasons. Never expose Google's raw body, tokens or account details.
        internal fun failureMessage(code: Int, body: String): String {
            val reasons = runCatching {
                val e = JSONObject(body).getJSONObject("error")
                val values = mutableListOf(e.optString("status"))
                for (key in listOf("errors", "details")) {
                    val rows = e.optJSONArray(key) ?: continue
                    for (i in 0 until rows.length()) values += rows.optJSONObject(i)?.optString("reason").orEmpty()
                }
                values
            }.getOrDefault(emptyList())
            return when {
                reasons.any { it in listOf("accessNotConfigured", "SERVICE_DISABLED") } -> "এই অ্যাপের Google Cloud প্রকল্পে Drive API চালু নেই। অ্যাপের মালিককে Drive API চালু করতে হবে।"
                reasons.any { it in listOf("storageQuotaExceeded") } -> "আপনার Google Drive-এর জায়গা শেষ। জায়গা খালি করে আবার ব্যাকআপ করুন।"
                reasons.any { it in listOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "RATE_LIMIT_EXCEEDED") } || code == 429 -> "Google Drive-এর ব্যবহারের সীমা এসেছে; পরে আবার চেষ্টা করুন।"
                code == 401 || reasons.any { it in listOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT") } -> "Drive-এর অনুমতি সম্পূর্ণ হয়নি। ব্যাকআপ চালু / অনুমতি দিন চাপুন এবং অনুমতি দিন।"
                code == 403 -> "Google Drive প্রবেশের অনুমতি দেয়নি (403)। Drive API, Google অনুমতি ও অ্যাকাউন্টের বিধিনিষেধ পরীক্ষা করুন।"
                code == 404 -> "ব্যাকআপটি Drive-এ পাওয়া যায়নি।"
                else -> "Google Drive-এ সংযোগ হয়নি (HTTP $code)। ইন্টারনেট দেখে আবার চেষ্টা করুন।"
            }
        }
    }
    fun list(): List<Entry> {
        val query = URLEncoder.encode("name contains 'ai-smart-reply-backup-' and trashed = false", "UTF-8")
        val root = JSONObject(request("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=30&orderBy=modifiedTime%20desc&fields=files(id,modifiedTime)&q=$query"))
        val files = root.optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).map { val f = files.getJSONObject(it); Entry(f.getString("id"), f.getString("modifiedTime")) }
    }
    fun download(id: String): String {
        require(Regex("[A-Za-z0-9_-]{1,200}").matches(id))
        return request("https://www.googleapis.com/drive/v3/files/$id?alt=media")
    }
    fun upload(raw: String) {
        require(raw.toByteArray(Charsets.UTF_8).size <= BackupData.MAX_BYTES)
        val boundary = "backup-" + UUID.randomUUID()
        val metadata = JSONObject().put("name", "ai-smart-reply-backup-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
            .put("parents", JSONArray().put("appDataFolder")).put("mimeType", "application/json")
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$raw\r\n--$boundary--\r\n"
        request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", "POST", "multipart/related; boundary=$boundary", body.toByteArray(Charsets.UTF_8))
    }
}
