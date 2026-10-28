package bd.app.aismartreply

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Only owner-authored reply configuration. Never tokens, inbox, audio or send permissions. */
internal object BackupData {
    const val MAX_BYTES = 256_000
    val textLimits = linkedMapOf("instructions" to 2000, "facts" to 3000, "examples" to 2000, "fallback" to 700)
    fun export(ctx: Context, owner: String): JSONObject {
        val data = JSONObject()
        textLimits.forEach { (key, _) -> data.put(key, if (key == "fallback") ReplyPreferences.fallback(ctx) else ReplyPreferences.text(ctx, key)) }
        data.put("fallback_enabled", ReplyPreferences.fallbackEnabled(ctx))
        val rules = JSONArray()
        ReplyPreferences.rules(ctx).forEach { r -> rules.put(JSONObject().put("id", r.id).put("keywords", r.keywords)
            .put("reply", r.reply).put("enabled", r.enabled).put("exact", r.exact).put("allWords", r.allWords)) }
        data.put("rules", rules)
        return JSONObject().put("schema", 1).put("owner", owner).put("created", System.currentTimeMillis()).put("data", data)
    }
    fun validate(raw: String, owner: String): JSONObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "ব্যাকআপের আকার বেশি" }
        val root = JSONObject(raw)
        require(root.getInt("schema") == 1 && root.getString("owner") == owner) { "ব্যাকআপের অ্যাকাউন্ট বা সংস্করণ মেলেনি" }
        val data = root.getJSONObject("data")
        textLimits.forEach { (key, limit) -> require(data.get(key) is String && data.getString(key).length <= limit) }
        require(data.get("fallback_enabled") is Boolean)
        val rules = data.getJSONArray("rules"); require(rules.length() <= 30)
        val ids = mutableSetOf<String>()
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            require(rule.getString("id").length in 1..100 && ids.add(rule.getString("id")))
            require(rule.getString("keywords").length in 1..500 && rule.getString("reply").length in 1..700)
            for (key in listOf("enabled", "exact", "allWords")) require(rule.get(key) is Boolean)
        }
        return data
    }
    fun restore(ctx: Context, raw: String, owner: String) {
        val data = validate(raw, owner) // validate everything before changing any local value
        val edit = ctx.getSharedPreferences("reply_customization", 0).edit()
        textLimits.keys.forEach { edit.putString(it, data.getString(it)) }
        edit.putBoolean("fallback_enabled", data.getBoolean("fallback_enabled"))
            .putString("rules", data.getJSONArray("rules").toString())
        synchronized(DriveBackups) { check(edit.commit()) { "ফোনে সংরক্ষণ করা যায়নি" } }
    }
}
