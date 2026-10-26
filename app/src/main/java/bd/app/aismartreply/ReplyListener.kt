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

}

class ReplyListener : NotificationListenerService() {
    companion object {
        private val packages = mapOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "com.facebook.orca" to "Messenger")
        private fun actionFor(n: Notification): Notification.Action? =
            n.actions?.firstOrNull { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true }

        fun send(ctx: Context, key: String, reply: String, expectedFingerprint: String? = null): Boolean {
            val listener = instance ?: return false
            val sbn = try { listener.activeNotifications?.firstOrNull { it.key == key } } catch (_: Exception) { null } ?: return false
            val action = actionFor(sbn.notification) ?: return false
            if (expectedFingerprint != null && NotificationMessageReader.read(sbn)?.fingerprint != expectedFingerprint) return false
            if (expectedFingerprint != null && !AutoReplyGuard.reserveAutoSend(ctx, NotificationMessageReader.route(sbn), reply)) return false
            // Mark before dispatch because the app can repost its notification synchronously.
            val sentMarker = AutoReplyGuard.markSent(ctx, NotificationMessageReader.route(sbn), reply)
            return try {
                val intent = Intent()
                val results = Bundle()
                action.remoteInputs?.forEach { results.putCharSequence(it.resultKey, reply) }
                RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
                action.actionIntent.send(ctx, 0, intent)
                true
            } catch (_: Exception) { AutoReplyGuard.forgetSent(ctx, sentMarker); false }
        }
        fun connected() = instance != null
        private val recovery = ListenerRecovery()
        private val recoveryHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var pendingRebind: Runnable? = null
        fun hasAccess(ctx: Context): Boolean {
            val component = ComponentName(ctx, ReplyListener::class.java)
            return if (android.os.Build.VERSION.SDK_INT >= 27)
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .isNotificationListenerAccessGranted(component)
            else android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
        }
        private fun diagnostic(ctx: Context, text: String) {
            ctx.getSharedPreferences("listener_health", Context.MODE_PRIVATE).edit()
                .putString("detail", text).putLong("time", System.currentTimeMillis()).apply()
        }
        fun connectionDetail(ctx: Context): String = ctx.getSharedPreferences("listener_health", Context.MODE_PRIVATE)
            .getString("detail", "সংযোগের কোনো তথ্য এখনও পাওয়া যায়নি").orEmpty()
        private fun requestBind(ctx: Context) {
            if (!hasAccess(ctx) || connected()) return
            try {
                NotificationListenerService.requestRebind(ComponentName(ctx, ReplyListener::class.java))
                diagnostic(ctx, "Android-কে সংযোগের অনুরোধ পাঠানো হয়েছে; সংযুক্ত হওয়ার অপেক্ষা")
            } catch (error: Exception) {
                diagnostic(ctx, "সংযোগের অনুরোধ ব্যর্থ: ${error.javaClass.simpleName}")
            }
        }
        private fun restartBinding(ctx: Context) {
            if (android.os.Build.VERSION.SDK_INT < 34 || !hasAccess(ctx) || connected()) return
            val app = ctx.applicationContext
            try {
                // API 34 provides a public reset for our own listener even without a live instance.
                NotificationListenerService.requestUnbind(ComponentName(app, ReplyListener::class.java))
                diagnostic(app, "বারবার সংযোগ না হওয়ায় Android-এর আটকে থাকা binding নতুন করে যুক্ত করা হচ্ছে")
                pendingRebind?.let { recoveryHandler.removeCallbacks(it) }
                val retry = Runnable {
                    pendingRebind = null
                    if (hasAccess(app) && !connected()) requestBind(app)
                }
                pendingRebind = retry
                recoveryHandler.postDelayed(retry, 1000L)
            } catch (error: Exception) {
                diagnostic(app, "সংযোগ পুনঃস্থাপন ব্যর্থ: ${error.javaClass.simpleName}")
                requestBind(app)
            }
        }
        fun reconnect(ctx: Context) {
            val access = hasAccess(ctx)
            val current = instance
            val healthy = current != null && runCatching { current.activeNotifications != null }.getOrDefault(false)
            if (!healthy && instance === current) instance = null
            if (!access) {
                pendingRebind?.let { recoveryHandler.removeCallbacks(it) }; pendingRebind = null
                diagnostic(ctx, "Notification Access বন্ধ; কোনো পুনঃসংযোগ করা হবে না")
            }
            recovery.check(access, healthy, android.os.SystemClock.elapsedRealtime(),
                restart = if (android.os.Build.VERSION.SDK_INT >= 34) ({ restartBinding(ctx) }) else null) {
                requestBind(ctx)
            }
        }
        @Volatile private var instance: ReplyListener? = null
    }
    override fun onCreate() { super.onCreate(); AutoReplyGuard.installSafetyUpdate(this); diagnostic(this, "নোটিফিকেশন সেবা তৈরি হয়েছে; Android সংযোগের অপেক্ষা") }
    override fun onListenerConnected() {
        super.onListenerConnected(); instance = this; recovery.reset()
        pendingRebind?.let { recoveryHandler.removeCallbacks(it) }; pendingRebind = null
        diagnostic(this, "Android নোটিফিকেশন সংযোগ সফল")
    }
    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        diagnostic(this, "Android মেসেজ সংযোগ বিচ্ছিন্ন করেছে")
        super.onListenerDisconnected()
        if (ReplyStore.enabled(this, "ai_auto") || ReplyStore.enabled(this, "auto")) reconnect(this)
    }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        getSharedPreferences("listener_health", Context.MODE_PRIVATE).edit()
            .putLong("last_event", System.currentTimeMillis()).apply()
        val app = packages[sbn.packageName] ?: return
        if (!ReplyStore.enabled(this, "allow_${sbn.packageName}")) return
        val n = sbn.notification
        val incoming = NotificationMessageReader.read(sbn) ?: return
        if (AutoReplyGuard.isSentEcho(this, incoming)) return
        val sender = incoming.sender
        val message = incoming.text
        val item = Incoming(sbn.key, app, sender, message, if (incoming.timestamp > 0) incoming.timestamp else System.currentTimeMillis())
        ReplyStore.add(this, item)
        if (ReplyStore.enabled(this, "ai_auto") && AiEngine.configured(this) && actionFor(n) != null) {
            val fixed = ReplyPreferences.matched(this, message)
            if (fixed != null) {
                if (!AutoReplyGuard.claim(this, incoming)) return
                val sent = send(this, sbn.key, fixed, incoming.fingerprint)
                if (sent) getSharedPreferences("ai_auto_state", Context.MODE_PRIVATE).edit()
                    .putString("status", "নিজের নিয়ম অনুযায়ী উত্তর পাঠানোর অনুরোধ সফল").apply()
                return
            }
            val state = getSharedPreferences("ai_auto_state", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val day = now / 86400000L
            val count = if (state.getLong("day", -1L) == day) state.getInt("count", 0) else 0
            val signature = sbn.key + ":" + message
            val sensitive = listOf("otp", "password", "pin", "bank", "payment", "বিকাশ", "নগদ", "টাকা", "পাসওয়ার্ড", "জরুরি", "অভিযোগ")
            if (message.length in 2..500 && count < 20 &&
                sensitive.none { message.contains(it, true) }) {
                if (!AutoReplyGuard.claim(this, incoming)) return
                state.edit().putLong("day", day).putInt("count", count + 1)
                    .putString("last_signature", signature).apply()
                state.edit().putString("status", "AI উত্তর তৈরি হচ্ছে").apply()
                val wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:auto-reply")
                    .apply { acquire(35000L) }
                val generationAccount = GoogleAuth.account(this)?.id
                AiEngine.generate(this, message) { result ->
                    try {
                        val reply = result.getOrNull()
                        if (reply == null) {
                            // Store only the error category, never message text or login tokens.
                            val error = result.exceptionOrNull()
                            val category = when {
                                error?.message?.contains("Google") == true -> "Google লগইন নবায়ন করুন"
                                error is java.net.SocketTimeoutException -> "সংযোগের সময় শেষ হয়েছে"
                                error is java.io.IOException -> "ইন্টারনেট সংযোগ পরীক্ষা করুন"
                                else -> Regex("HTTP \\d{3}").find(error?.message.orEmpty())?.value ?: "উত্তর তৈরি হয়নি"
                            }
                            state.edit().putString("status", "AI অনুরোধ ব্যর্থ: $category; ম্যানুয়াল AI পরীক্ষা করুন").apply()
                        } else if (ReplyStore.enabled(this, "ai_auto") &&
                            generationAccount == GoogleAuth.account(this)?.id && reply.length in 2..700 &&
                            send(this, sbn.key, reply, incoming.fingerprint)) {
                            state.edit().putLong("last_sent", System.currentTimeMillis())
                                .putString("status", "অটো রিপ্লাই পাঠানোর অনুরোধ সফল").apply()
                        } else if (ReplyStore.enabled(this, "ai_auto")) {
                            state.edit().putString("status", "পাঠানো হয়নি: বিরতি বা সক্রিয় Reply নোটিফিকেশন পরীক্ষা করুন").apply()
                        }
                    } finally { if (wakeLock.isHeld) wakeLock.release() }
                }
            }
        } else if (ReplyStore.enabled(this, "auto") && actionFor(n) != null) {
            val response = ReplyPreferences.templateReply(this, message)
            if (response != null && AutoReplyGuard.claim(this, incoming)) {
                send(this, sbn.key, response, incoming.fingerprint)
            }
        }
    }
}
