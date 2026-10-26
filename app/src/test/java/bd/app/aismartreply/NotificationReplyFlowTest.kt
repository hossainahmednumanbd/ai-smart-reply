package bd.app.aismartreply

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationReplyFlowTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val peer get() = Person.Builder().setName("Contact").setKey("peer").build()
    private val owner get() = Person.Builder().setName("Me").setKey("owner").build()
    @Before fun reset() {
        listOf("auto_reply_guard_v1", "reply_settings", "reply_customization").forEach {
            ctx.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun sbn(n: Notification, id: Int = 7) = StatusBarNotification(
        "com.whatsapp", "com.whatsapp", id, null, 1000, 0, n, Process.myUserHandle(), 5000L)
    private fun styled(vararg messages: Notification.MessagingStyle.Message, id: Int = 7): StatusBarNotification {
        val style = Notification.MessagingStyle(owner)
        messages.forEach { style.addMessage(it) }
        return sbn(Notification.Builder(ctx, "messages").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setShortcutId("one-chat").setStyle(style).build(), id)
    }
    private fun incoming(text: String, time: Long) = Notification.MessagingStyle.Message(text, time, peer)
    private fun outgoing(text: String, time: Long) = Notification.MessagingStyle.Message(text, time, null as Person?)
    @Test fun matchingRuleDoesNotGenerateFallbackOnReplyEchoOrNotificationRepost() {
        val rules = listOf(ReplyRule("greet", "hi", "জি ভাই"))
        val sent = mutableListOf<String>()
        val first = NotificationMessageReader.read(styled(incoming("hi", 1000)))!!
        if (AutoReplyGuard.claim(ctx, first, 10000)) {
            val reply = ReplyRules.templateReply(rules, first.text, true, "সাধারণ উত্তর")!!
            sent.add(reply); AutoReplyGuard.markSent(ctx, first.route, reply, 10001)
        }
        assertNull(NotificationMessageReader.read(styled(incoming("hi", 1000), outgoing("জি ভাই", 1100))))
        val repost = NotificationMessageReader.read(styled(incoming("hi", 1000), id = 99))!!
        assertFalse(AutoReplyGuard.claim(ctx, repost, 10002))
        assertEquals(listOf("জি ভাই"), sent)
        val unmatched = NotificationMessageReader.read(styled(incoming("অন্য প্রশ্ন", 1200)))!!
        assertTrue(AutoReplyGuard.claim(ctx, unmatched, 10003))
        assertEquals("সাধারণ উত্তর", ReplyRules.templateReply(rules, unmatched.text, true, "সাধারণ উত্তর"))
    }
    @Test fun onlyNewestIncomingMessageIsMatchedNotOlderConversationLines() {
        val latest = NotificationMessageReader.read(styled(incoming("hi", 1000), incoming("দাম কত", 2000)))!!
        assertEquals("দাম কত", latest.text)
        assertEquals("দাম দেখে জানাচ্ছি", ReplyRules.templateReply(listOf(
            ReplyRule("hi", "hi", "জি ভাই"), ReplyRule("price", "দাম", "দাম দেখে জানাচ্ছি")), latest.text, true, "সাধারণ উত্তর"))
    }
    @Test fun sameTextWithNewSenderTimestampIsANewMessage() {
        val first = NotificationMessageReader.read(styled(incoming("hi", 1000)))!!
        val next = NotificationMessageReader.read(styled(incoming("hi", 2000)))!!
        assertTrue(AutoReplyGuard.claim(ctx, first, 10000))
        assertFalse(AutoReplyGuard.claim(ctx, first, 10001))
        assertTrue(AutoReplyGuard.claim(ctx, next, 10002))
    }
    @Test fun explicitSelfPersonIsNotAnIncomingMessage() {
        val ownMessage = Notification.MessagingStyle.Message("জি ভাই", 1000, owner)
        assertNull(NotificationMessageReader.read(styled(ownMessage)))
    }
    @Test fun plainRemoteInputHistoryAndRecordedEchoCannotTriggerFallback() {
        val n = Notification.Builder(ctx, "messages").setContentTitle("You").setContentText("জি ভাই")
            .setRemoteInputHistory(arrayOf<CharSequence>("জি ভাই")).build()
        assertNull(NotificationMessageReader.read(sbn(n)))
        val plain = Notification.Builder(ctx, "messages").setContentTitle("You").setContentText("জি ভাই").build()
        val message = NotificationMessageReader.read(sbn(plain))!!
        AutoReplyGuard.markSent(ctx, message.route, "জি ভাই", 10000)
        assertTrue(AutoReplyGuard.isSentEcho(ctx, message, 10001))
        assertFalse(AutoReplyGuard.isSentEcho(ctx, message.copy(route = "other-chat"), 10001))
    }
    @Test fun realContactCanRepeatOurReplyWithAnExplicitIncomingSender() {
        val message = NotificationMessageReader.read(styled(incoming("জি ভাই", 1000)))!!
        AutoReplyGuard.markSent(ctx, message.route, "জি ভাই", 10000)
        assertFalse(AutoReplyGuard.isSentEcho(ctx, message, 10001))
    }
    @Test fun plainCurrentTextWinsOverJoinedBigText() {
        val n = Notification.Builder(ctx, "messages").setContentTitle("Contact").setContentText("দাম কত")
            .setStyle(Notification.BigTextStyle().bigText("hi\nজি ভাই\nদাম কত")).build()
        assertEquals("দাম কত", NotificationMessageReader.read(sbn(n))!!.text)
    }
    @Test fun listenerStoresInboundOnlyAndDoesNotStoreItsOwnReplyAsYou() {
        val controller = Robolectric.buildService(ReplyListener::class.java).create()
        val listener = controller.get()
        ReplyStore.set(ctx, "allow_com.whatsapp", true)
        listener.onNotificationPosted(styled(incoming("hi", 1000)))
        listener.onNotificationPosted(styled(incoming("hi", 1000), outgoing("জি ভাই", 1100)))
        assertEquals(1, ReplyStore.entries(ctx).size)
        assertEquals("hi", ReplyStore.entries(ctx).single().message)
        controller.destroy()
    }
}
