package bd.app.aismartreply

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.ResultReceiver
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceWriterActivityTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun permissionOnlyBridgeClosesWithoutStartingRecording() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java, Intent(app, VoiceWriterActivity::class.java).putExtra("permission_only", true)).setup()
        assertTrue(controller.get().isFinishing)
        assertNull(shadowOf(app).nextStartedService)
        controller.pause().stop().destroy()
    }
    @Test fun recordingBridgeHasNoSetupFormAndStartsOverlayBeforeFinishing() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        app.getSharedPreferences("voice_writer", 0).edit().putBoolean("recording_notice_accepted", true).apply()
        val controller = Robolectric.buildActivity(VoiceWriterActivity::class.java).setup()
        val activity = controller.get()
        assertFalse(views(activity.window.decorView).any { it is EditText })
        assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        val request = shadowOf(app).nextStartedService
        assertEquals(VoiceOverlayService.ACTION_RECORD, request.action)
        assertEquals(VoiceOverlayService::class.java.name, request.component!!.className)
        assertFalse(activity.isFinishing)
        @Suppress("DEPRECATION") val ready = request.getParcelableExtra<ResultReceiver>("ready")!!
        ready.send(0, Bundle()); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
        controller.pause().stop().destroy()
    }
}
