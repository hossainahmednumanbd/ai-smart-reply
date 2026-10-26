package bd.app.aismartreply

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

data class Incoming(val key: String, val app: String, val sender: String, val message: String, val time: Long)

object ReplyStore {
    private const val PREF = "reply_settings"
    private const val INBOX = "inbox"
    fun enabled(ctx: Context, key: String) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(key, false)
    fun set(ctx: Context, key: String, value: Boolean) { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply() }
    fun entries(ctx: Context): List<Incoming> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(INBOX, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Incoming(o.getString("key"), o.getString("app"), o.getString("sender"), o.getString("message"), o.getLong("time"))
            }
        } catch (_: Exception) { emptyList() }
    }
    @Synchronized fun add(ctx: Context, item: Incoming) {
        val previous = entries(ctx).filterNot { it.key == item.key && it.message == item.message }.take(49)
        val array = JSONArray()
        (listOf(item) + previous).forEach {
            array.put(JSONObject().put("key", it.key).put("app", it.app).put("sender", it.sender).put("message", it.message).put("time", it.time))
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(INBOX, array.toString()).apply()
    }
    fun suggestion(text: String): String {
        val msg = text.lowercase()
        return when {
            listOf("কেমন আছ", "how are you", "kmn acho").any { msg.contains(it) } -> "আলহামদুলিল্লাহ, ভালো আছি। আপনি কেমন আছেন?"
            listOf("ধন্যবাদ", "thanks", "thank you").any { msg.contains(it) } -> "আপনাকেও ধন্যবাদ!"
            listOf("hello", "hi", "হ্যালো", "সালাম", "assalamu").any { msg.contains(it) } -> "আসসালামু আলাইকুম! কীভাবে সাহায্য করতে পারি?"
            else -> "আপনার মেসেজ পেয়েছি। একটু পরে বিস্তারিত জানাচ্ছি।"
        }
    }
}

class ReplyListener : NotificationListenerService() {
    companion object {
        private val packages = mapOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "com.facebook.orca" to "Messenger")
        private fun actionFor(n: Notification): Notification.Action? =
            n.actions?.firstOrNull { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true }

        fun send(ctx: Context, key: String, reply: String): Boolean {
            val listener = instance ?: return false
            val sbn = try { listener.activeNotifications?.firstOrNull { it.key == key } } catch (_: Exception) { null } ?: return false
            val action = actionFor(sbn.notification) ?: return false
            return try {
                val intent = Intent()
                val results = Bundle()
                action.remoteInputs?.forEach { results.putCharSequence(it.resultKey, reply) }
                RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
                action.actionIntent.send(ctx, 0, intent)
                true
            } catch (_: Exception) { false }
        }
        fun connected() = instance != null
        fun reconnect(ctx: Context) {
            if (instance == null) {
                try { NotificationListenerService.requestRebind(ComponentName(ctx, ReplyListener::class.java)) } catch (_: Exception) {}
            }
        }
        @Volatile private var instance: ReplyListener? = null
    }
    override fun onListenerConnected() { super.onListenerConnected(); instance = this }
    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
        if (ReplyStore.enabled(this, "ai_auto")) reconnect(this)
    }
    override fun onDestroy() { instance = null; super.onDestroy() }

    // Persist recent sends across listener restarts; notification keys change after replies.
    private fun shouldAutoReply(app: String, sender: String, message: String): Boolean {
        val prefs = getSharedPreferences("auto_dedup_v2", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val normalized = message.trim().replace(Regex("\\s+"), " ").lowercase()
        val identity = "$app|${sender.trim().lowercase()}|$normalized"
        val key = "msg_" + identity.hashCode().toString()
        val last = prefs.getLong(key, 0L)
        if (last > 0 && now - last in 0..600000L) return false
        // Prevent notification feedback loops from our own template replies.
        if (normalized == ReplyStore.suggestion(message).trim().lowercase()) return false
        prefs.edit().putLong(key, now).apply()
        return true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = packages[sbn.packageName] ?: return
        if (!ReplyStore.enabled(this, "allow_${sbn.packageName}")) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category in listOf(Notification.CATEGORY_CALL, Notification.CATEGORY_SERVICE,
                Notification.CATEGORY_TRANSPORT)) return
        val extras = n.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val message = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (sender.isEmpty() || message.isEmpty() || message.length > 2000) return
        if (message.contains("new messages", ignoreCase = true)) return
        val item = Incoming(sbn.key, app, sender, message, System.currentTimeMillis())
        ReplyStore.add(this, item)
        if (ReplyStore.enabled(this, "ai_auto") && AiEngine.configured(this) && actionFor(n) != null) {
            val state = getSharedPreferences("ai_auto_state", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val day = now / 86400000L
            val count = if (state.getLong("day", -1L) == day) state.getInt("count", 0) else 0
            val signature = sbn.key + ":" + message
            val sensitive = listOf("otp", "password", "pin", "bank", "payment", "বিকাশ", "নগদ", "টাকা", "পাসওয়ার্ড", "জরুরি", "অভিযোগ")
            if (message.length in 2..500 && count < 20 &&
                now - state.getLong("last_sent", 0L) > 60000L &&
                state.getString("last_signature", "") != signature &&
                sensitive.none { message.contains(it, true) }) {
                state.edit().putLong("day", day).putInt("count", count + 1)
                    .putString("last_signature", signature).apply()
                state.edit().putString("status", "AI উত্তর তৈরি হচ্ছে").apply()
                val wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:auto-reply")
                    .apply { acquire(35000L) }
                AiEngine.generate(this, message) { result ->
                    try {
                        val reply = result.getOrNull()
                        if (reply == null) {
                            // Store only the error category, never message text or login tokens.
                            state.edit().putString("status", "AI অনুরোধ ব্যর্থ; ম্যানুয়াল AI পরীক্ষা করুন").apply()
                        } else if (ReplyStore.enabled(this, "ai_auto") && reply.length in 2..700 &&
                            System.currentTimeMillis() - state.getLong("last_sent", 0L) > 60000L &&
                            send(this, sbn.key, reply)) {
                            state.edit().putLong("last_sent", System.currentTimeMillis())
                                .putString("status", "অটো রিপ্লাই পাঠানোর অনুরোধ সফল").apply()
                        } else {
                            state.edit().putString("status", "পাঠানো হয়নি: মোড, বিরতি বা সক্রিয় Reply নোটিফিকেশন পরীক্ষা করুন").apply()
                        }
                    } finally { if (wakeLock.isHeld) wakeLock.release() }
                }
            }
        } else if (ReplyStore.enabled(this, "auto") && actionFor(n) != null) {
            if (shouldAutoReply(app, sender, message)) {
                send(this, sbn.key, ReplyStore.suggestion(message))
            }
        }
    }
}
