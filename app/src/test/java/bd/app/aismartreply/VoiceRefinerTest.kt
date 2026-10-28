package bd.app.aismartreply

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceRefinerTest {
    @Test fun originalWorksWithoutLoginOrNetwork() {
        var output = ""
        VoiceRefiner.refine(RuntimeEnvironment.getApplication(), "  বাংলা ১২৩  ", VoiceRefiner.Mode.ORIGINAL) { output = it.getOrThrow() }
        assertEquals("বাংলা ১২৩", output)
    }
    @Test fun noGoogleLoginGivesActionableErrorWithoutAskingForApiKey() {
        var error = ""
        VoiceRefiner.refine(RuntimeEnvironment.getApplication(), "মূল কথা", VoiceRefiner.Mode.REFINE) { error = it.exceptionOrNull()?.message.orEmpty() }
        assertTrue(error.contains("Google")); assertFalse(error.contains("API"))
    }
    @Test fun parsesVoiceGatewayTextWithoutReplyTruncation() {
        val text = "বাংলা বক্তব্য ১২৩ ".repeat(200)
        assertEquals(text.trim(), VoiceRefiner.parseResponse(JSONObject().put("text", text)))
    }
    @Test fun emptyGatewayResultsAreRejected() {
        assertTrue(runCatching { VoiceRefiner.parseResponse(JSONObject()) }.isFailure)
    }
}
