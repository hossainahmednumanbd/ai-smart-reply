package bd.app.aismartreply

import android.Manifest
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class VoiceOverlayServiceTest {
    private fun <T> field(s: VoiceOverlayService, name: String): T {
        @Suppress("UNCHECKED_CAST") return VoiceOverlayService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(s) as T
    }
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun record(s: VoiceOverlayService) {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        VoiceOverlayService::class.java.getDeclaredMethod("beginRecording", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(s, true)
    }
    @Test fun deniedOverlayPermissionStopsService() {
        ShadowSettings.setCanDrawOverlays(false)
        val c = Robolectric.buildService(VoiceOverlayService::class.java).create()
        assertTrue(shadowOf(c.get()).isStoppedBySelf); c.destroy()
    }
    @Test fun bubbleIsNonFocusableAndClickOnlyBootstrapsMicrophone() {
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(VoiceOverlayService::class.java).create(); val s = c.get()
        val params = field<WindowManager.LayoutParams>(s, "params")
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        field<View>(s, "bubble").performClick()
        val request = shadowOf(s.application).nextStartedActivity
        assertEquals(VoiceWriterActivity::class.java.name, request.component!!.className)
        assertTrue(request.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(VoiceOverlayService.recording); c.destroy()
    }
    @Test fun stopNotificationRemainsAvailableAndStopsWithoutRestart() {
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(VoiceOverlayService::class.java).create(); val s = c.get()
        val notifications = shadowOf(s.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        assertEquals("Stop", notifications.allNotifications.first().actions.first().title.toString())
        assertEquals(android.app.Service.START_NOT_STICKY, s.onStartCommand(Intent(s, VoiceOverlayService::class.java).setAction(VoiceOverlayService.ACTION_STOP), 0, 1))
        assertTrue(shadowOf(s).isStoppedBySelf); c.destroy()
    }
    @Test fun recordingBarHasCancelAndFinishControlsAndCancelReleasesMicrophone() {
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(VoiceOverlayService::class.java).create(); val s = c.get(); record(s)
        assertTrue(VoiceOverlayService.recording)
        val controls = views(field(s, "bubble"))
        assertTrue(controls.any { it.contentDescription == "কথা শেষ করে লেখা তৈরি করুন" })
        val audio = File(s.noBackupFilesDir, "voice-writer/pending.m4a").apply { writeText("recorded") }
        controls.first { it.contentDescription == "রেকর্ডিং বাতিল করুন" }.performClick()
        assertFalse(VoiceOverlayService.recording); assertFalse(audio.exists())
        assertNull(field<android.media.MediaRecorder?>(s, "recorder")); c.destroy()
    }
    @Test fun resultCardCopiesWithoutOpeningAnotherPageAndCloseKeepsAudio() {
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(VoiceOverlayService::class.java).create(); val s = c.get()
        VoiceOverlayService::class.java.getDeclaredField("displayed").apply { isAccessible = true }.set(s, "হোসাইন ১২৩")
        val audio = File(s.noBackupFilesDir, "voice-writer/pending.m4a").apply { parentFile!!.mkdirs(); writeText("recorded") }
        VoiceOverlayService::class.java.getDeclaredMethod("showResult", String::class.java).apply { isAccessible = true }.invoke(s, "")
        var controls = views(field(s, "bubble"))
        controls.first { it.contentDescription == "লেখা কপি করুন" }.performClick()
        val clipboard = s.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("হোসাইন ১২৩", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertNull(shadowOf(s.application).nextStartedActivity)
        controls.first { it.contentDescription == "লেখা রেখে বাক্স বন্ধ করুন" }.performClick()
        assertTrue(audio.exists()); c.destroy()
    }
    @Test fun destroyingServiceAlwaysReleasesRecording() {
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(VoiceOverlayService::class.java).create(); val s = c.get(); record(s)
        assertTrue(VoiceOverlayService.recording); c.destroy()
        assertFalse(VoiceOverlayService.recording); assertFalse(VoiceOverlayService.running)
        assertNull(field<android.media.MediaRecorder?>(s, "recorder"))
    }
}
