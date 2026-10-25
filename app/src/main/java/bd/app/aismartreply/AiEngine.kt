package bd.app.aismartreply

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * User-managed HTTPS gateway. Each user configures their own endpoint and bearer credential.
 * Provider secrets must stay on their gateway, never inside the APK.
 */
object AiEngine {
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    fun configured(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        return p.getString("url", "")?.startsWith("https://") == true &&
            !p.getString("token", "").isNullOrBlank()
    }
    fun save(ctx: Context, url: String, token: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        val parsed = runCatching { URL(normalized) }.getOrNull() ?: return false
        if (parsed.protocol != "https" || parsed.host.isBlank() || parsed.userInfo != null ||
            parsed.ref != null || token.isBlank()) return false
        ctx.getSharedPreferences("ai_config", Context.MODE_PRIVATE).edit()
            .putString("url", normalized).putString("token", token.trim()).apply()
        return true
    }
    fun generate(ctx: Context, message: String, callback: (Result<String>) -> Unit) {
        if (!configured(ctx)) {
            main.post { callback(Result.failure(IllegalStateException("নিরাপদ AI Gateway সংযুক্ত নয়"))) }
            return
        }
        val pref = ctx.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        val endpoint = pref.getString("url", "").orEmpty()
        val token = pref.getString("token", "").orEmpty()
        pool.execute {
            val result = runCatching {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 12000
                    conn.readTimeout = 25000
                    conn.doOutput = true
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    val body = JSONObject().put("message", message.take(2000)).put("language", "auto").toString()
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    require(conn.responseCode == 200) { "AI সার্ভার HTTP ${conn.responseCode}" }
                    val raw = conn.inputStream.bufferedReader().use { it.readText().take(20000) }
                    val reply = JSONObject(raw).optString("reply").trim()
                    require(reply.isNotEmpty()) { "AI উত্তর পাওয়া যায়নি" }
                    reply.take(1200)
                } finally { conn.disconnect() }
            }
            main.post { callback(result) }
        }
    }
}
