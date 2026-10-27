package bd.app.aismartreply

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Voice-only authenticated gateway. No Gemini key in the app or recording panel. */
object VoiceRefiner {
    enum class Mode { ORIGINAL, REFINE, LEARNER }
    private const val ENDPOINT = "https://wandering-waterfall-20ai-smart-reply-backend.hossainahmednumanbd.workers.dev/voice"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun refine(context: Context, transcript: String, mode: Mode, callback: (Result<String>) -> Unit) {
        if (transcript.isBlank()) { callback(Result.failure(IllegalArgumentException("কোনো লেখা পাওয়া যায়নি"))); return }
        if (mode == Mode.ORIGINAL) { callback(Result.success(transcript.trim())); return }
        request(context, { JSONObject().put("operation", "refine").put("mode", if (mode == Mode.REFINE) "refine" else "learner").put("text", transcript) }, callback)
    }
    fun transcribe(context: Context, audio: File, callback: (Result<String>) -> Unit) = request(context, {
        require(audio.exists() && audio.length() in 1..4_500_000) { "অডিও পাওয়া যায়নি বা আকার বেশি" }
        JSONObject().put("operation", "transcribe").put("mimeType", "audio/mp4")
            .put("audio", Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP))
    }, callback)
    private fun request(context: Context, body: () -> JSONObject, callback: (Result<String>) -> Unit) {
        val ctx = context.applicationContext
        val account = GoogleAuth.account(ctx)?.id
        if (account == null) { callback(Result.failure(IllegalStateException("মূল অ্যাপে Google দিয়ে লগইন করুন"))); return }
        GoogleAuth.withIdToken(ctx) { tokenResult ->
            val token = tokenResult.getOrElse { callback(Result.failure(IllegalStateException("Google লগইন আবার করুন"))); return@withIdToken }
            worker.execute {
                val result = runCatching {
                    val payload = body().toString().toByteArray(Charsets.UTF_8)
                    val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = "POST"; conn.connectTimeout = 15000; conn.readTimeout = 125000
                        conn.instanceFollowRedirects = false; conn.doOutput = true
                        conn.setFixedLengthStreamingMode(payload.size)
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.setRequestProperty("Authorization", "Bearer $token")
                        conn.outputStream.use { it.write(payload) }
                        val code = conn.responseCode
                        check(code == 200) { when (code) {
                            401 -> "Google লগইন মেয়াদ শেষ; মূল অ্যাপে আবার লগইন করুন"
                            404, 503 -> "Voice Writer সার্ভার এখনো প্রস্তুত নয়; অডিও রাখা আছে"
                            429 -> "ভয়েস ব্যবহারের সীমা শেষ; পরে চেষ্টা করুন"
                            422 -> "লেখা সম্পূর্ণ পাওয়া যায়নি; অডিও থেকে আবার চেষ্টা করুন"
                            else -> "ভয়েস সার্ভার সাড়া দেয়নি (HTTP $code); আবার চেষ্টা করুন"
                        } }
                        val response = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                        check(account == GoogleAuth.account(ctx)?.id) { "অ্যাকাউন্ট পরিবর্তিত হয়েছে; আবার চেষ্টা করুন" }
                        parseResponse(response)
                    } finally { conn.disconnect() }
                }
                main.post { callback(result) }
            }
        }
    }
    internal fun parseResponse(response: JSONObject): String {
        val text = response.optString("text").trim()
        require(text.isNotBlank() && text.length <= 60000) { "কোনো সম্পূর্ণ লেখা পাওয়া যায়নি" }
        return text
    }
}
