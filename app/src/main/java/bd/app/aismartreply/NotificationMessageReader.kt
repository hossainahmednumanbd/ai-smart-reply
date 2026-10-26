package bd.app.aismartreply

import android.app.Notification
import android.content.Context
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
        "${sbn.packageName}|${sbn.user.identifier}|${sbn.notification.shortcutId ?: "${sbn.id}:${sbn.tag.orEmpty()}"}"

    @Suppress("DEPRECATION")
    fun read(sbn: StatusBarNotification): NotificationMessage? {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            n.category in listOf(Notification.CATEGORY_CALL, Notification.CATEGORY_SERVICE, Notification.CATEGORY_TRANSPORT)) return null
        val extras = n.extras ?: return null
        val style = Notification.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style != null) {
            // Do not search backward for an old incoming message after an outgoing reply.
            val latest = style.messages.lastOrNull() ?: return null
            val author = if (Build.VERSION.SDK_INT >= 28) latest.senderPerson?.name?.toString() else latest.sender?.toString()
            if (author.isNullOrBlank()) return null
            if (Build.VERSION.SDK_INT >= 28) {
                val authorPerson = latest.senderPerson
                val user = style.user
                if ((!authorPerson?.key.isNullOrBlank() && authorPerson?.key == user.key) ||
                    (!authorPerson?.uri.isNullOrBlank() && authorPerson?.uri == user.uri)) return null
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

/** Persistent bounded hashes: callback repeats and RemoteInput echoes cannot send a second reply. */
object AutoReplyGuard {
    private const val PREF = "auto_reply_guard_v1"
    private const val ECHO_TTL = 120000L
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    @Synchronized fun claim(ctx: Context, message: NotificationMessage, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(ctx)
        val key = "event_" + message.fingerprint
        val ttl = if (message.structured) 86400000L else 60000L
        val previous = p.getLong(key, 0L)
        if (previous > 0 && now - previous in 0 until ttl) return false
        prune(ctx, now)
        p.edit().putLong(key, now).apply()
        return true
    }
    fun isSentEcho(ctx: Context, message: NotificationMessage, now: Long = System.currentTimeMillis()): Boolean {
        // Explicit incoming sender/timestamp is authoritative, even when a contact repeats our words.
        if (message.structured) return false
        val text = message.text.replace(Regex("^(?:You|আপনি)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        val key = "sent_" + digest(message.route + "|" + ReplyRules.normalize(text))
        val sentAt = prefs(ctx).getLong(key, 0L)
        return sentAt > 0 && now - sentAt in 0 until ECHO_TTL
    }
    @Synchronized fun markSent(ctx: Context, route: String, reply: String, now: Long = System.currentTimeMillis()): String {
        prune(ctx, now)
        val key = "sent_" + digest(route + "|" + ReplyRules.normalize(reply))
        prefs(ctx).edit().putLong(key, now).apply()
        return key
    }
    fun forgetSent(ctx: Context, marker: String) { prefs(ctx).edit().remove(marker).apply() }
    private fun prune(ctx: Context, now: Long) {
        val p = prefs(ctx); val edit = p.edit()
        p.all.entries.sortedByDescending { (it.value as? Long) ?: 0L }.forEachIndexed { i, entry ->
            val time = (entry.value as? Long) ?: 0L
            val ttl = if (entry.key.startsWith("sent_")) ECHO_TTL else 86400000L
            if (i >= 199 || now - time >= ttl || time > now) edit.remove(entry.key)
        }
        edit.apply()
    }
}
