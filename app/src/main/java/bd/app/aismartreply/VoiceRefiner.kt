package bd.app.aismartreply

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Optional user-provided Gemini API key. Kept separate from the existing
 * Cloudflare-backed automatic reply pipeline.
 * Do not embed API keys in source code or send them to the auto-reply backend.
 */
object VoiceRefiner {
    enum class Mode { ORIGINAL, REFINE, LEARNER }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun refine(transcript: String, mode: Mode, key: String, callback: (Result<String>) -> Unit) {
        val original = transcript.trim()
        if (original.isBlank()) {
            callback(Result.failure(IllegalArgumentException("কোনো বক্তব্য পাওয়া যায়নি")))
            return
        }
        if (mode == Mode.ORIGINAL) {
            callback(Result.success(original))
            return
        }
        if (key.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Gemini API Key যোগ করুন")))
            return
        }
        worker.execute {
            val result = runCatching {
                val prompt = if (mode == Mode.REFINE)
                    "Correct Bangla spelling and punctuation. Preserve every fact, name, number, intention and meaningful detail. Do not summarize, omit, invent, or answer the speaker. Return only the polished original text."
                else
                    "Rewrite the Bangla transcript into clear, easy-to-understand Bengali without losing facts, names, numbers or details. Do not invent information. Return only the rewritten text."
                val request = JSONObject().put("contents", org.json.JSONArray().put(
                    JSONObject().put("parts", org.json.JSONArray().put(
                        JSONObject().put("text", "$prompt\n\nTranscript:\n$original")
                    ))
                )).put("generationConfig", JSONObject().put("temperature", 0.1))
                val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                    .openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 12000
                    conn.readTimeout = 35000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setRequestProperty("x-goog-api-key", key)
                    conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                    val status = conn.responseCode
                    if (status != 200) throw IllegalStateException("Gemini HTTP $status")
                    val response = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val candidates = response.optJSONArray("candidates")
                    val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val polished = parts?.optJSONObject(0)?.optString("text").orEmpty().trim()
                    require(polished.isNotBlank()) { "AI থেকে লেখা পাওয়া যায়নি" }
                    polished
                } finally { conn.disconnect() }
            }
            main.post { callback(result) }
        }
    }
}
