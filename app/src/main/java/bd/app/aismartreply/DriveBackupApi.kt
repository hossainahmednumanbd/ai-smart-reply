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
            check(code in 200..299) { when (code) {
                401 -> "Drive-এর অনুমতি আবার দিন"
                403 -> "Drive API চালু আছে ও অনুমতি দেওয়া হয়েছে কি না দেখুন; Drive-এর জায়গা/ব্যবহারের সীমাও পরীক্ষা করুন"
                404 -> "ব্যাকআপটি Drive-এ পাওয়া যায়নি"
                429 -> "Google Drive ব্যস্ত; পরে আবার চেষ্টা হবে"
                else -> "Google Drive-এ সংযোগ হয়নি (HTTP $code)"
            } }
            return conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    require(output.size() + n <= BackupData.MAX_BYTES) { "ব্যাকআপের আকার বেশি" }; output.write(buffer, 0, n) }
                output.toString("UTF-8")
            }
        } finally { conn.disconnect() }
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
