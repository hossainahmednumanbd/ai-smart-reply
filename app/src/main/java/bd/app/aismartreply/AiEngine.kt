package bd.app.aismartreply

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Cloudflare gateway authenticates each request with a Google ID token. */
object AiEngine {
    private const val ENDPOINT = "https://wandering-waterfall-20ai-smart-reply-backend.hossainahmednumanbd.workers.dev/reply"
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    fun configured(ctx: Context) = GoogleAuth.account(ctx) != null
    fun generate(ctx: Context, message: String, callback: (Result<String>) -> Unit) {
        if (!configured(ctx)) {
            main.post { callback(Result.failure(IllegalStateException("প্রথমে Google দিয়ে লগইন করুন"))) }
            return
        }
        GoogleAuth.withIdToken(ctx) { tokenResult ->
            val token = tokenResult.getOrElse {
                callback(Result.failure(IllegalStateException("Google লগইন মেয়াদ শেষ। আবার লগইন করুন।")))
                return@withIdToken
            }
            pool.execute {
                val result = runCatching {
                    val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 12000
                        conn.readTimeout = 25000
                        conn.doOutput = true
                        conn.instanceFollowRedirects = false
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.setRequestProperty("Authorization", "Bearer $token")
                        val body = JSONObject().put("message", message.take(2000)).toString()
                        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        val status = conn.responseCode
                        if (status != 200) {
                            val detail = conn.errorStream?.bufferedReader()?.use { it.readText().take(1000) }.orEmpty()
                            val error = runCatching { JSONObject(detail).optString("error") }.getOrNull().orEmpty()
                            error("AI সার্ভার HTTP $status: $error")
                        }
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
}
