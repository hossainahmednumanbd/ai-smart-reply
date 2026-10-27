package bd.app.aismartreply

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceRefinerTest {
    @Test fun originalWorksWithoutNetworkOrKey() {
        var output = ""
        VoiceRefiner.refine("  বাংলা ১২৩  ", VoiceRefiner.Mode.ORIGINAL, "") { output = it.getOrThrow() }
        assertEquals("বাংলা ১২৩", output)
    }
    @Test fun missingKeyFailsWithoutChangingOriginal() {
        var failed = false
        VoiceRefiner.refine("মূল কথা", VoiceRefiner.Mode.REFINE, "") { failed = it.isFailure }
        assertTrue(failed)
    }
    @Test fun concatenatesAllTextPartsAndExcludesThinking() {
        val response = JSONObject("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"internal","thought":true},{"text":"আমার নাম"},{"text":"হোসাইন ৪২"}]}}]}""")
        assertEquals("আমার নাম\nহোসাইন ৪২", VoiceRefiner.parseResponse(response))
    }
    @Test fun truncatedBlockedOrEmptyAnswersAreNeverCopiedAsComplete() {
        for (json in listOf(
            """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"half"}]}}]}""",
            """{"promptFeedback":{"blockReason":"SAFETY"}}""",
            """{"candidates":[{"finishReason":"STOP","content":{"parts":[]}}]}""")) {
            assertTrue(runCatching { VoiceRefiner.parseResponse(JSONObject(json)) }.isFailure)
        }
    }
}
