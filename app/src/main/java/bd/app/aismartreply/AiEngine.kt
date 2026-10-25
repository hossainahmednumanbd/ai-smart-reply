package bd.app.aismartreply

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks only to an explicitly configured HTTPS backend owned by the operator.
 * Never embed an AI provider API key in an APK.
 * Backend contract: POST {"message":"...","language":"auto"} -> {"reply":"..."}.
 */
object AiEngine {
    private val pool = Executors.newFixedThreadPool(2)
    fun configured(ctx: Context): Boolean =
        ctx.getSharedPreferences("ai_config", Context.MODE_PRIVATE).getString("url", "")?.startsWith("https://") == true

    fun save(ctx: Context, url: String, token: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("https://") || normalized.contains("@") || normalized.contains("#")) return false
        ctx.getSharedPreferences("ai_config", Context.MODE_PRIVATE).edit()
            .putString("url", normalized).putString("token", token.trim()).apply()
        return true
    }

    fun generate(ctx: Context, message: String, callback: (Result<String>) -> Unit) {
        val pref = ctx.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        val endpoint = pref.getString("url", "").orEmpty()
        val token = pref.getString("token", "").orEmpty()
        if (!endpoint.startsWith("https://")) {
            callback(Result.failure(IllegalStateException("AI সার্ভার এখনো সংযুক্ত নয়")))
            return
        }
        pool.execute {
            val result = runCatching {
                val url = URL(endpoint)
                require(url.protocol == "https") { "HTTPS required" }
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 12000
                    conn.readTimeout = 25000
                    conn.doOutput = true
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
                    val body = JSONObject().put("message", message.take(2000)).put("language", "auto").toString()
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    require(conn.responseCode == 200) { "AI সার্ভার HTTP ${conn.responseCode}" }
                    val raw = conn.inputStream.bufferedReader().use { it.readText().take(20000) }
                    val reply = JSONObject(raw).optString("reply").trim()
                    require(reply.isNotEmpty()) { "AI উত্তর পাওয়া যায়নি" }
                    reply.take(2000)
                } finally { conn.disconnect() }
            }
            (ctx as? android.app.Activity)?.runOnUiThread { callback(result) }
                ?: android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
        }
    }
}
