package bd.app.aismartreply

import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceWriterActivityTest {
    private fun <T> field(activity: VoiceWriterActivity, name: String): T {
        val field = VoiceWriterActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") return field.get(activity) as T
    }
    private fun invoke(activity: VoiceWriterActivity, name: String, value: String) {
        VoiceWriterActivity::class.java.getDeclaredMethod(name, String::class.java).apply { isAccessible = true }.invoke(activity, value)
    }
    @Test fun copiesEditedBengaliOnlyWhileForeground() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val activity = controller.get()
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        invoke(activity, "copyText", "হোসাইন ১২৩")
        assertEquals("হোসাইন ১২৩", clipboard.primaryClip!!.getItemAt(0).text.toString())
        controller.pause()
        invoke(activity, "copyText", "late callback")
        assertEquals("হোসাইন ১২৩", clipboard.primaryClip!!.getItemAt(0).text.toString())
        controller.stop().destroy()
    }
    @Test fun originalModeCompletesWithoutApiKeyAndCopies() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val activity = controller.get()
        field<EditText>(activity, "output").setText("বাংলা বক্তব্য ৪২")
        VoiceWriterActivity::class.java.getDeclaredMethod("finishCapture").apply { isAccessible = true }.invoke(activity)
        shadowOf(Looper.getMainLooper()).idle()
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("বাংলা বক্তব্য ৪২", clipboard.primaryClip!!.getItemAt(0).text.toString())
        controller.pause().stop().destroy()
    }
    @Test fun restoresDraftAndAudioRetryWithoutDiscardingRecording() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val dir = File(context.noBackupFilesDir, "voice-writer").apply { mkdirs() }
        File(dir, "draft.txt").writeText("মূল বক্তব্য")
        File(dir, "edited.txt").writeText("সম্পাদিত বক্তব্য")
        val audio = File(dir, "pending.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val activity = controller.get()
        assertEquals("সম্পাদিত বক্তব্য", field<EditText>(activity, "output").text.toString())
        VoiceWriterActivity::class.java.getDeclaredMethod("processAudio").apply { isAccessible = true }.invoke(activity)
        assertTrue(audio.exists())
        assertTrue(field<TextView>(activity, "status").text.toString().contains("Key"))
        controller.pause().stop().destroy()
    }
    @Test fun stopTimeoutPreservesUnconfirmedTailWithoutCopyingIt() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val activity = controller.get()
        invoke(activity, "copyText", "আগের Clipboard")
        val buffer = field<VoiceTranscriptBuffer>(activity, "transcript")
        buffer.reset(); buffer.acceptFinal("প্রথম কথা"); buffer.acceptInterim("শেষ কথা")
        val session = field<VoiceCaptureSession>(activity, "session")
        val id = session.begin(); session.stop()
        VoiceWriterActivity::class.java.getDeclaredField("autoCopy").apply { isAccessible = true }.set(activity, true)
        val settle = VoiceWriterActivity::class.java.getDeclaredMethod("settleSpeech", Int::class.javaPrimitiveType, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        settle.invoke(activity, id, "শেষ অংশ নিশ্চিত হয়নি", true)
        assertEquals("প্রথম কথা শেষ কথা", field<EditText>(activity, "output").text.toString())
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("আগের Clipboard", clipboard.primaryClip!!.getItemAt(0).text.toString())
        settle.invoke(activity, id, null, false)
        assertEquals("প্রথম কথা শেষ কথা", field<EditText>(activity, "output").text.toString())
        controller.pause().stop().destroy()
    }
    @Test fun leavingScreenCancelsSpeechAndKeepsTheLastHypothesis() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val activity = controller.get()
        val buffer = field<VoiceTranscriptBuffer>(activity, "transcript")
        buffer.reset(); buffer.acceptFinal("হোসাইন"); buffer.acceptInterim("চিলাউড়া")
        val session = field<VoiceCaptureSession>(activity, "session")
        val id = session.begin()
        controller.pause()
        assertFalse(session.accepts(id))
        assertEquals("হোসাইন চিলাউড়া", field<EditText>(activity, "output").text.toString())
        controller.stop().destroy()
    }

    @Test fun leavingScreenReleasesTheAudioRecorder() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val activity = controller.get()
        VoiceWriterActivity::class.java.getDeclaredMethod("startAudio").apply { isAccessible = true }.invoke(activity)
        assertNotNull(field<android.media.MediaRecorder?>(activity, "recorder"))
        controller.pause()
        assertNull(field<android.media.MediaRecorder?>(activity, "recorder"))
        controller.stop().destroy()
    }

}
