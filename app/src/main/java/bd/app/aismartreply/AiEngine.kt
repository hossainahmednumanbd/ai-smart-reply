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
        val requestedAccount = GoogleAuth.account(ctx)?.id
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
                        val body = JSONObject().put("message", message.take(2000))
                            .put("profile", ReplyPreferences.profile(ctx)).toString()
                        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        val status = conn.responseCode
                        if (status != 200) {
                            val detail = conn.errorStream?.bufferedReader()?.use { it.readText().take(1000) }.orEmpty()
                            val serverError = runCatching { JSONObject(detail).optString("error") }.getOrNull().orEmpty()
                            throw IllegalStateException("AI সার্ভার HTTP $status: $serverError")
                        }
                        val raw = conn.inputStream.bufferedReader().use { it.readText().take(20000) }
                        val reply = JSONObject(raw).optString("reply").trim()
                        require(reply.isNotEmpty()) { "AI উত্তর পাওয়া যায়নি" }
                        check(requestedAccount == GoogleAuth.account(ctx)?.id) { "অ্যাকাউন্ট পরিবর্তিত হয়েছে; আবার চেষ্টা করুন" }
                        ctx.getSharedPreferences("ai_output", Context.MODE_PRIVATE).edit()
                            .putString("last_reply", reply.take(1200)).apply()
                        reply.take(1200)
                    } finally { conn.disconnect() }
                }
                main.post { callback(result) }
            }
        }
    }
    fun report(ctx: Context, reply: String, reason: String, callback: (Result<Unit>) -> Unit) =
        operation(ctx, "/feedback", "POST", JSONObject().put("reply", reply.take(1200))
            .put("reason", reason.take(500)), callback)
    fun deleteAccount(ctx: Context, callback: (Result<Unit>) -> Unit) =
        operation(ctx, "/account", "DELETE", null, callback)
    private fun operation(ctx: Context, path: String, method: String, body: JSONObject?, callback: (Result<Unit>) -> Unit) {
        GoogleAuth.withIdToken(ctx) { tokenResult ->
            val token = tokenResult.getOrElse { callback(Result.failure(it)); return@withIdToken }
            pool.execute {
                val result = runCatching {
                    val conn = URL(ENDPOINT.removeSuffix("/reply") + path).openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = method; conn.connectTimeout = 12000; conn.readTimeout = 25000
                        conn.instanceFollowRedirects = false
                        conn.setRequestProperty("Authorization", "Bearer $token")
                        if (body != null) {
                            conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        }
                        check(conn.responseCode == 200) { "অনুরোধ সফল হয়নি (HTTP ${conn.responseCode})। আবার চেষ্টা করুন।" }
                        Unit
                    } finally { conn.disconnect() }
                }
                main.post { callback(result) }
            }
        }
    }

}
