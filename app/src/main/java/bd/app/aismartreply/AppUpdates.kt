package bd.app.aismartreply

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Public, reviewed GitHub releases only. No login token, install or storage permission. */
object AppUpdates {
    internal const val REPO = "hossainahmednumanbd/ai-smart-reply"
    internal data class Update(val code: Long, val name: String, val url: String, val notes: String)
    private val executor = Executors.newSingleThreadExecutor()
    private val checking = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    internal fun safeDownload(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.rawPath.startsWith("/$REPO/releases/download/") &&
            !uri.rawPath.contains("%", ignoreCase = true) && !uri.rawPath.contains("..")
    }.getOrDefault(false)

    internal fun newest(releases: JSONArray, installed: Long, preview: Boolean): Update? {
        val updates = mutableListOf<Update>()
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft") || (!preview && release.optBoolean("prerelease"))) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val match = Regex("ai-smart-reply-([0-9]+)\\.apk").matchEntire(asset.optString("name")) ?: continue
                val code = match.groupValues[1].toLongOrNull() ?: continue
                val url = asset.optString("browser_download_url")
                if (code <= installed || asset.optString("state") != "uploaded" || !safeDownload(url)) continue
                updates += Update(code, release.optString("name").take(100), url, userNotes(release.optString("body")))
            }
        }
        return updates.maxByOrNull { it.code }
    }

    // Release checksums remain public on GitHub for verification, outside the app's update dialog.
    internal fun userNotes(body: String): String = body.lineSequence()
        .filterNot { it.contains("SHA-256", true) || it.contains("SHA256", true) || Regex(".*[a-fA-F0-9]{64}.*").matches(it) }
        .joinToString("\n").trim().take(1600)

    @Suppress("DEPRECATION")
    fun check(activity: Activity, manual: Boolean = false) {
        val ctx = activity.applicationContext
        val prefs = ctx.getSharedPreferences("app_updates", 0)
        if (!manual && System.currentTimeMillis() - prefs.getLong("last_check", 0) < 6 * 60 * 60 * 1000L) return
        if (!checking.compareAndSet(false, true)) {
            if (manual) Toast.makeText(activity, "আপডেট পরীক্ষা চলছে…", Toast.LENGTH_SHORT).show()
            return
        }
        if (manual) Toast.makeText(activity, "নতুন ভার্সন খোঁজা হচ্ছে…", Toast.LENGTH_SHORT).show()
        val owner = WeakReference(activity)
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val preview = info.versionName.orEmpty().contains("preview", true)
        val play = ctx.packageManager.getInstallerPackageName(ctx.packageName) == "com.android.vending"
        executor.execute {
            val result = runCatching {
                val conn = URL("https://api.github.com/repos/$REPO/releases?per_page=20").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 10000; conn.readTimeout = 10000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", "AI-Smart-Reply-Android")
                    check(conn.responseCode == 200) { "ইন্টারনেট সংযোগ দেখে পরে আবার চেষ্টা করুন" }
                    val bytes = conn.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 1_000_000)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 1_000_000)
                    newest(JSONArray(String(bytes, Charsets.UTF_8)), code, preview)
                } finally { conn.disconnect() }
            }
            main.post {
                checking.set(false)
                val screen = owner.get() ?: return@post
                if (screen.isFinishing || screen.isDestroyed) return@post
                result.onSuccess { update ->
                    prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    if (update == null) {
                        if (manual) Toast.makeText(screen, "আপনার জন্য নতুন আপডেট নেই", Toast.LENGTH_LONG).show()
                    } else {
                        if (!manual && prefs.getLong("later_code", 0) == update.code &&
                            System.currentTimeMillis() - prefs.getLong("later_at", 0) < 24 * 60 * 60 * 1000L) return@onSuccess
                        val message = "নতুন ভার্সন: ${update.name}\n\n${update.notes}\n\n" +
                            if (play) "Google Play থেকে আপডেট করুন।" else "ডাউনলোড ব্রাউজারে খুলবে। APK নামলে সেটি খুলে Update চাপুন। আগের অ্যাপ আনইনস্টল করবেন না।"
                        AlertDialog.Builder(screen).setTitle("নতুন আপডেট পাওয়া গেছে")
                            .setMessage(message)
                            .setNegativeButton("পরে") { _, _ -> prefs.edit().putLong("later_code", update.code).putLong("later_at", System.currentTimeMillis()).apply() }
                            .setPositiveButton("আপডেট করুন") { _, _ ->
                                val uri = if (play) "https://play.google.com/store/apps/details?id=${ctx.packageName}" else update.url
                                runCatching { screen.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                                    .onFailure { Toast.makeText(screen, "লিংক খুলতে একটি ব্রাউজার চালু রাখুন", Toast.LENGTH_LONG).show() }
                            }.show()
                    }
                }.onFailure { if (manual) Toast.makeText(screen, "আপডেট পরীক্ষা করা যায়নি। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
