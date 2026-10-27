package bd.app.aismartreply

import org.junit.Assert.*
import org.junit.Test

class VoiceCaptureSessionTest {
    @Test fun stopWaitsForFinalAndSettlesExactlyOnce() {
        val session = VoiceCaptureSession()
        val id = session.begin(); session.stop()
        assertEquals(VoiceCaptureSession.State.STOPPING, session.state)
        assertTrue(session.accepts(id)); assertTrue(session.settle(id))
        assertFalse(session.settle(id)); assertFalse(session.accepts(id))
    }
    @Test fun lateResultAfterTimeoutCannotContaminateNextSegment() {
        val session = VoiceCaptureSession()
        val old = session.begin(); session.stop(); session.settle(old)
        val next = session.begin()
        assertFalse(session.accepts(old)); assertFalse(session.settle(old))
        assertTrue(session.accepts(next)); assertEquals(VoiceCaptureSession.State.LISTENING, session.state)
    }
    @Test fun cancellationInvalidatesAllPendingCallbacks() {
        val session = VoiceCaptureSession()
        val id = session.begin(); session.cancel()
        assertFalse(session.accepts(id)); assertFalse(session.settle(id))
        assertTrue(session.accepts(session.begin()))
    }
    @Test fun finalReplacesHypothesisWithoutLosingEarlierBengaliSegments() {
        val buffer = VoiceTranscriptBuffer()
        buffer.acceptFinal("আমার নাম হোসাইন।")
        buffer.acceptInterim("আমি চিলা")
        buffer.acceptInterim("আমি চিলাউড়ায় থাকি")
        assertEquals("আমার নাম হোসাইন।", buffer.snapshot())
        buffer.acceptFinal("আমি চিলাউড়ায় থাকি।")
        assertEquals("আমার নাম হোসাইন। আমি চিলাউড়ায় থাকি।", buffer.snapshot())
        assertEquals(buffer.snapshot(), buffer.preview())
    }
    @Test fun provisionalTailSurvivesStopTimeoutAndResetClearsIt() {
        val buffer = VoiceTranscriptBuffer()
        buffer.acceptFinal("প্রথম কথা")
        buffer.acceptInterim("শেষ কথা")
        assertEquals("প্রথম কথা শেষ কথা", buffer.preview())
        buffer.acceptFinal(buffer.preview().removePrefix(buffer.snapshot()).trim())
        assertEquals("প্রথম কথা শেষ কথা", buffer.snapshot())
        buffer.reset(); assertEquals("", buffer.preview())
    }
    @Test fun repeatedSpokenPhrasesArePreservedAcrossSegments() {
        val buffer = VoiceTranscriptBuffer()
        buffer.acceptFinal("হ্যাঁ"); buffer.acceptFinal("হ্যাঁ")
        assertEquals("হ্যাঁ হ্যাঁ", buffer.snapshot())
    }
}
