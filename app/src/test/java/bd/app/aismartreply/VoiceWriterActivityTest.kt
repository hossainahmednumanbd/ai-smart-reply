package bd.app.aismartreply

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaRecorder
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceWriterActivityTest {
    private fun <T> field(activity: VoiceWriterActivity, name: String): T {
        val field = VoiceWriterActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") return field.get(activity) as T
    }
    private fun invoke(activity: VoiceWriterActivity, name: String) {
        VoiceWriterActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }
    private fun copy(activity: VoiceWriterActivity, value: String) {
        VoiceWriterActivity::class.java.getDeclaredMethod("copyText", String::class.java).apply { isAccessible = true }.invoke(activity, value)
    }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    @Test fun opensFloatingPanelInSeparateTaskWithoutApiKeyForm() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val a = controller.get()
        val attrs = a.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
        assertTrue(attrs.getBoolean(0, false)); attrs.recycle()
        val info = a.packageManager.getActivityInfo(ComponentName(a, VoiceWriterActivity::class.java), 0)
        assertEquals("bd.app.aismartreply.voice", info.taskAffinity)
        val editors = all(a.window.decorView).filterIsInstance<EditText>()
        assertEquals(1, editors.size)
        assertFalse(editors.any { it.hint?.toString()?.contains("Key", true) == true })
        controller.pause().stop().destroy()
    }
    @Test fun copiesEditedBengaliOnlyWhileForeground() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val a = controller.get(); val clipboard = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        copy(a, "হোসাইন ১২৩"); assertEquals("হোসাইন ১২৩", clipboard.primaryClip!!.getItemAt(0).text.toString())
        controller.pause(); copy(a, "late")
        assertEquals("হোসাইন ১২৩", clipboard.primaryClip!!.getItemAt(0).text.toString())
        controller.stop().destroy()
    }
    @Test fun restoresEditedDraftAndRetainsAudioWhenServerLoginFails() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.noBackupFilesDir, "voice-writer").apply { mkdirs() }
        File(dir, "draft.txt").writeText("মূল বক্তব্য"); File(dir, "edited.txt").writeText("সম্পাদিত বক্তব্য")
        val audio = File(dir, "pending.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val a = controller.get()
        assertEquals("সম্পাদিত বক্তব্য", field<EditText>(a, "output").text.toString())
        invoke(a, "processAudio")
        assertTrue(audio.exists()); assertTrue(field<TextView>(a, "status").text.toString().contains("Google"))
        controller.pause().stop().destroy()
    }
    @Test fun leavingPanelReleasesRecorderAndDoesNotUpload() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val a = controller.get(); invoke(a, "startAudio")
        assertNotNull(field<MediaRecorder?>(a, "recorder"))
        controller.pause(); assertNull(field<MediaRecorder?>(a, "recorder"))
        assertFalse(field<Boolean>(a, "busy"))
        controller.stop().destroy()
    }
    @Test fun copyCannotRunWhileRecording() {
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val a = controller.get(); copy(a, "আগের Clipboard"); invoke(a, "startAudio"); copy(a, "অসম্পূর্ণ")
        val clipboard = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("আগের Clipboard", clipboard.primaryClip!!.getItemAt(0).text.toString())
        controller.pause().stop().destroy()
    }
}
