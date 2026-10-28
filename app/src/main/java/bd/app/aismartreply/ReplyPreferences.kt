package bd.app.aismartreply

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ReplyPreferences {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("reply_customization", Context.MODE_PRIVATE)
    private fun defaults() = listOf(
        ReplyRule("greeting", "আসসালামু আলাইকুম|সালাম|hello|hi", "ওয়ালাইকুম আসসালাম! কীভাবে সাহায্য করতে পারি?"),
        ReplyRule("wellbeing", "কেমন আছ|how are you|kmn acho", "আলহামদুলিল্লাহ, ভালো আছি। আপনি কেমন আছেন?"),
        ReplyRule("thanks", "ধন্যবাদ|thanks|thank you", "আপনাকেও ধন্যবাদ!")
    )
    fun rules(ctx: Context): List<ReplyRule> {
        val raw = prefs(ctx).getString("rules", null) ?: return defaults()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i -> val o = a.getJSONObject(i)
                ReplyRule(o.getString("id"), o.getString("keywords"), o.getString("reply"),
                    o.optBoolean("exact"), o.optBoolean("enabled", true), o.optBoolean("allWords", false)) }
        }.getOrElse { defaults() }
    }
    fun saveRules(ctx: Context, rules: List<ReplyRule>) {
        val a = JSONArray()
        rules.take(30).forEach { a.put(JSONObject().put("id", it.id).put("keywords", it.keywords)
            .put("reply", it.reply).put("exact", it.exact).put("enabled", it.enabled).put("allWords", it.allWords)) }
        synchronized(DriveBackups) { prefs(ctx).edit().putString("rules", a.toString()).apply(); DriveBackups.changed(ctx) }
    }
    fun matched(ctx: Context, message: String) = ReplyRules.resolve(rules(ctx), message)
    fun fallback(ctx: Context) = prefs(ctx).getString("fallback", "আপনার মেসেজ পেয়েছি। একটু পরে বিস্তারিত জানাচ্ছি।").orEmpty()
    fun fallbackEnabled(ctx: Context) = prefs(ctx).getBoolean("fallback_enabled", false)
    fun setFallbackEnabled(ctx: Context, enabled: Boolean) { synchronized(DriveBackups) { prefs(ctx).edit().putBoolean("fallback_enabled", enabled).apply(); DriveBackups.changed(ctx) } }
    fun templateReply(ctx: Context, message: String) = ReplyRules.templateReply(rules(ctx), message, fallbackEnabled(ctx), fallback(ctx))
    fun suggestion(ctx: Context, message: String) = templateReply(ctx, message).orEmpty()
    fun text(ctx: Context, key: String) = prefs(ctx).getString(key, "").orEmpty()
    fun saveText(ctx: Context, key: String, value: String) { synchronized(DriveBackups) { prefs(ctx).edit().putString(key, value).apply(); DriveBackups.changed(ctx) } }
    fun profile(ctx: Context) = JSONObject().put("instructions", text(ctx, "instructions").take(2000))
        .put("facts", text(ctx, "facts").take(3000)).put("examples", text(ctx, "examples").take(2000))
    fun clear(ctx: Context) { prefs(ctx).edit().clear().apply() }
}
