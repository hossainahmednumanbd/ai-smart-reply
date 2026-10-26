package bd.app.aismartreply

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import android.os.Build
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

/** One newest incoming message, never a joined history or our own reply. */
data class NotificationMessage(val sender: String, val text: String, val timestamp: Long,
    val route: String, val structured: Boolean) {
    val fingerprint: String get() = digest("$route|$sender|$text|${if (structured) timestamp else 0L}")
}
private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

object NotificationMessageReader {
    fun route(sbn: StatusBarNotification): String =
        "${sbn.packageName}|${sbn.user}|${sbn.notification.shortcutId ?: "${sbn.id}:${sbn.tag.orEmpty()}"}"

    @Suppress("DEPRECATION")
    fun read(sbn: StatusBarNotification): NotificationMessage? {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            n.category in listOf(Notification.CATEGORY_CALL, Notification.CATEGORY_SERVICE, Notification.CATEGORY_TRANSPORT)) return null
        val extras = n.extras ?: return null
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style != null) {
            // Do not search backward for an old incoming message after an outgoing reply.
            val latest = style.messages.lastOrNull() ?: return null
            val author = latest.person?.name?.toString() ?: latest.sender?.toString()
            if (author.isNullOrBlank()) return null
            run {
                val authorPerson = latest.person
                val user = style.user
                // Framework MessagingStyle stores the full owner Person separately from
                // the compatibility display-name bundle. Keep its stable identity when present.
                val frameworkUser = if (Build.VERSION.SDK_INT >= 28)
                    runCatching { extras.getParcelable<android.app.Person>("android.messagingUser") }.getOrNull() else null
                val userKey = if (Build.VERSION.SDK_INT >= 28) frameworkUser?.key ?: user.key else user.key
                val userUri = if (Build.VERSION.SDK_INT >= 28) frameworkUser?.uri ?: user.uri else user.uri
                if ((!authorPerson?.key.isNullOrBlank() && authorPerson?.key == userKey) ||
                    (!authorPerson?.uri.isNullOrBlank() && authorPerson?.uri == userUri)) return null
            }
            val text = latest.text?.toString()?.trim().orEmpty()
            if (text.isBlank() || text.length > 2000) return null
            val sender = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: author
            return NotificationMessage(sender, text, latest.timestamp, route(sbn), latest.timestamp > 0)
        }
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        // EXTRA_TEXT is the current message; BIG_TEXT can contain the entire conversation.
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        if (sender.isBlank() || text.isBlank() || text.length > 2000 || text.contains("new messages", true)) return null
        val sentHistory = extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY).orEmpty()
        if (sentHistory.any { ReplyRules.normalize(it.toString()) == ReplyRules.normalize(text) }) return null
        return NotificationMessage(sender, text, sbn.postTime, route(sbn), false)
    }
}

/** Conservative installation-wide loop protection, including Personal <-> Business account echoes. */
object AutoReplyGuard {
    private const val PREF = "auto_reply_guard_v1"
    private const val ECHO_TTL = 600000L
    private const val CHAT_PAUSE = 60000L
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun clean(text: String) = ReplyRules.normalize(text
        .replace(Regex("[\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]"), "")
        .replace(Regex("^(?:You|আপনি|Me)\\s*:\\s*", RegexOption.IGNORE_CASE), ""))
    private fun scope(message: NotificationMessage) = message.route.split('|').take(2).joinToString("|")
    private fun status(ctx: Context, text: String) {
        ctx.getSharedPreferences("ai_auto_state", Context.MODE_PRIVATE).edit().putString("status", text).apply()
    }
    /** Updates must not silently resume automatic sending after the reported runaway loop. */
    @Synchronized fun installSafetyUpdate(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (p.getBoolean("control_safety_033", false)) return false
        ctx.getSharedPreferences("reply_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("auto", false).putBoolean("ai_auto", false).commit()
        p.edit().putBoolean("control_safety_033", true).commit()
        status(ctx, "সুরক্ষা আপডেট: অটো বন্ধ রাখা হয়েছে। নিয়ম যাচাই করে নিজে চালু করুন।")
        return true
    }
    @Synchronized fun claim(ctx: Context, message: NotificationMessage, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(ctx)
        val eventKey = "event_" + message.fingerprint
        // A repost may have a new timestamp/notification ID. Content is also deduplicated.
        val contentKey = "content_" + digest(scope(message) + "|" + clean(message.sender) + "|" + clean(message.text))
        val eventAt = p.getLong(eventKey, 0L)
        val contentAt = p.getLong(contentKey, 0L)
        if ((eventAt > 0 && now - eventAt in 0 until 86400000L) ||
            (contentAt > 0 && now - contentAt in 0 until ECHO_TTL)) return false
        prune(ctx, now)
        p.edit().putLong(eventKey, now).putLong(contentKey, now).apply()
        return true
    }
    fun isSentEcho(ctx: Context, message: NotificationMessage, now: Long = System.currentTimeMillis()): Boolean {
        // A reply can return as an actual incoming message in another enabled account.
        // Sender metadata and a new timestamp do not prove it came from a human.
        val p = prefs(ctx)
        val echoAt = p.getLong("echo_" + digest(clean(message.text)), 0L)
        if (echoAt > 0 && now - echoAt in 0 until ECHO_TTL) return true
        val oldAt = p.getLong("sent_" + digest(message.route + "|" + clean(message.text)), 0L)
        return oldAt > 0 && now - oldAt in 0 until ECHO_TTL
    }
    /** Last gate for every automatic send, shared by templates, custom rules and AI. */
    @Synchronized fun reserveAutoSend(ctx: Context, route: String, reply: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!ReplyStore.enabled(ctx, "auto") && !ReplyStore.enabled(ctx, "ai_auto")) return false
        val p = prefs(ctx)
        val chatKey = "chat_" + digest(route)
        val replyKey = "reply_" + digest(route + "|" + clean(reply))
        val lastChat = p.getLong(chatKey, 0L)
        val lastReply = p.getLong(replyKey, 0L)
        if (lastReply > 0 && now - lastReply in 0 until ECHO_TTL) {
            status(ctx, "একই অটো উত্তর ১০ মিনিটে পুনরাবৃত্তি হওয়ায় পাঠানো হয়নি"); return false
        }
        if (lastChat > 0 && now - lastChat in 0 until CHAT_PAUSE) {
            status(ctx, "এই চ্যাটে এক মিনিটের অটো রিপ্লাই বিরতি চলছে"); return false
        }
        val start = p.getLong("control_burst_start", 0L)
        val count = if (start > 0 && now - start in 0 until 60000L) p.getLong("control_burst_count", 0L) else 0L
        if (count >= 5L) {
            ctx.getSharedPreferences("reply_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("auto", false).putBoolean("ai_auto", false).commit()
            status(ctx, "দ্রুত পুনরাবৃত্তি ধরা পড়ায় সুরক্ষার জন্য সব অটো রিপ্লাই বন্ধ হয়েছে")
            AutoReplyService.sync(ctx)
            return false
        }
        p.edit().putLong(chatKey, now).putLong(replyKey, now)
            .putLong("control_burst_start", if (count == 0L) now else start)
            .putLong("control_burst_count", count + 1L).commit()
        return true
    }
    @Synchronized fun markSent(ctx: Context, route: String, reply: String, now: Long = System.currentTimeMillis()): String {
        prune(ctx, now)
        val key = "sent_" + digest(route + "|" + clean(reply))
        // Global across all enabled messaging packages, before dispatching RemoteInput.
        prefs(ctx).edit().putLong(key, now).putLong("echo_" + digest(clean(reply)), now).commit()
        return key
    }
    fun forgetSent(ctx: Context, marker: String) {
        // Retain the global echo marker conservatively after an uncertain send attempt.
        prefs(ctx).edit().remove(marker).apply()
    }
    private fun prune(ctx: Context, now: Long) {
        val p = prefs(ctx); val edit = p.edit()
        p.all.entries.filterNot { it.key.startsWith("control_") }
            .sortedByDescending { (it.value as? Long) ?: 0L }.forEachIndexed { i, entry ->
                val time = (entry.value as? Long) ?: 0L
                val ttl = if (entry.key.startsWith("event_")) 86400000L else ECHO_TTL
                if (i >= 499 || now - time >= ttl || time > now) edit.remove(entry.key)
            }
        edit.apply()
    }
}
