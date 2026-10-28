package bd.app.aismartreply

import android.content.Context
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupDataTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    @Before fun clean() { ctx.getSharedPreferences("reply_customization", 0).edit().clear().commit() }
    @Test fun businessAndRulesRoundTripWithoutReEnablingAutoReply() {
        ReplyPreferences.saveText(ctx, "facts", "দোকান: হোসাইন টেলিকম, দাম ১২৩ টাকা")
        ReplyPreferences.saveText(ctx, "instructions", "বাংলায় ভদ্র উত্তর")
        ReplyPreferences.saveText(ctx, "examples", "প্রশ্ন: ঠিকানা? উত্তর: চিলাউড়া")
        ReplyPreferences.saveText(ctx, "fallback", "পরে জানাচ্ছি")
        ReplyPreferences.setFallbackEnabled(ctx, true)
        ReplyPreferences.saveRules(ctx, listOf(ReplyRule("one", "বিকাশ|bkash", "০১৭১২৩৪৫৬৭৮", true, false, false)))
        val original = BackupData.export(ctx, "owner-one").toString()
        ctx.getSharedPreferences("reply_settings", 0).edit().putBoolean("auto", false).commit()
        ReplyPreferences.clear(ctx)
        BackupData.restore(ctx, original, "owner-one")
        assertEquals("দোকান: হোসাইন টেলিকম, দাম ১২৩ টাকা", ReplyPreferences.text(ctx, "facts"))
        assertEquals("বাংলায় ভদ্র উত্তর", ReplyPreferences.text(ctx, "instructions"))
        assertEquals("প্রশ্ন: ঠিকানা? উত্তর: চিলাউড়া", ReplyPreferences.text(ctx, "examples"))
        assertTrue(ReplyPreferences.fallbackEnabled(ctx)); assertEquals("পরে জানাচ্ছি", ReplyPreferences.fallback(ctx))
        assertEquals(ReplyRule("one", "বিকাশ|bkash", "০১৭১২৩৪৫৬৭৮", true, false, false), ReplyPreferences.rules(ctx).single())
        assertFalse(ctx.getSharedPreferences("reply_settings", 0).getBoolean("auto", true))
    }
    @Test fun rejectsOtherOwnersAndFutureSchemasBeforeChangingAnything() {
        ReplyPreferences.saveText(ctx, "facts", "বর্তমান তথ্য")
        val root = BackupData.export(ctx, "owner-one")
        assertTrue(runCatching { BackupData.restore(ctx, root.toString(), "owner-two") }.isFailure)
        root.put("schema", 99)
        assertTrue(runCatching { BackupData.restore(ctx, root.toString(), "owner-one") }.isFailure)
        assertEquals("বর্তমান তথ্য", ReplyPreferences.text(ctx, "facts"))
    }
    @Test fun invalidRuleDoesNotPartiallyRestoreBusinessInformation() {
        ReplyPreferences.saveText(ctx, "facts", "মূল তথ্য")
        val root = BackupData.export(ctx, "owner-one")
        root.getJSONObject("data").put("facts", "নতুন তথ্য").getJSONArray("rules").getJSONObject(0).put("reply", "x".repeat(701))
        assertTrue(runCatching { BackupData.restore(ctx, root.toString(), "owner-one") }.isFailure)
        assertEquals("মূল তথ্য", ReplyPreferences.text(ctx, "facts"))
    }
    @Test fun snapshotWhitelistExcludesSecretsMessagesAndAudio() {
        ctx.getSharedPreferences("reply_customization", 0).edit().putString("token", "private-token").putString("audio", "private-audio").putString("messages", "private-inbox").commit()
        val raw = BackupData.export(ctx, "owner-one").toString()
        assertFalse(raw.contains("private-")); assertFalse(raw.contains("messages"))
    }
    @Test fun intentionalEmptyTemplateListStaysEmpty() {
        val root = BackupData.export(ctx, "owner-one")
        root.getJSONObject("data").put("rules", JSONArray())
        BackupData.restore(ctx, root.toString(), "owner-one")
        assertTrue(ReplyPreferences.rules(ctx).isEmpty())
    }
    @Test fun excessiveAndDuplicateRulesAreRejected() {
        val root = BackupData.export(ctx, "owner-one")
        val rules = root.getJSONObject("data").getJSONArray("rules")
        rules.put(rules.getJSONObject(0))
        assertTrue(runCatching { BackupData.validate(root.toString(), "owner-one") }.isFailure)
        assertTrue(runCatching { BackupData.validate(" ".repeat(BackupData.MAX_BYTES + 1), "owner-one") }.isFailure)
    }
}
