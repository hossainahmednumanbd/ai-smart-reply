package bd.app.aismartreply

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceOverlayServiceTest {
    @Test fun permissionDeniedStopsWithoutAttachingOverlay() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = Robolectric.buildService(VoiceOverlayService::class.java).create()
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }
    @Test fun enabledOverlayHasStopActionAndDoesNotRestartAfterStop() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(VoiceOverlayService::class.java).create()
        val service = controller.get()
        val notifications = shadowOf(service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val notification = notifications.allNotifications.first { it.actions?.isNotEmpty() == true }
        assertEquals("Stop", notification.actions.first().title.toString())
        assertNotNull(notification.contentIntent)
        val result = service.onStartCommand(Intent(service, VoiceOverlayService::class.java).setAction(VoiceOverlayService.ACTION_STOP), 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
    @Test fun accessibleBubbleClickOpensWriter() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(VoiceOverlayService::class.java).create()
        val service = controller.get()
        val bubble = VoiceOverlayService::class.java.getDeclaredField("bubble").apply { isAccessible = true }.get(service) as android.view.View
        assertTrue(bubble.performClick())
        val intent = shadowOf(service.application).nextStartedActivity
        assertEquals(VoiceWriterActivity::class.java.name, intent.component!!.className)
        controller.destroy()
    }

}
