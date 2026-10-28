package bd.app.aismartreply

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SmartAudioHomeTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun homeKeepsOriginalSectionsAndReplacesOnlyFacebookTile() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        var texts = views(a.window.decorView).filterIsInstance<TextView>()
        assertTrue(texts.any { it.text.toString() == "smart voice writer" })
        assertTrue(texts.any { it.text.toString() == "WhatsApp" })
        assertTrue(texts.any { it.text.toString() == "Messenger" })
        assertFalse(texts.any { it.text.toString() == "Facebook" })
        assertFalse(texts.any { it.text.toString() == "ভাসমান বাটন চালু করুন" })
        (texts.first { it.text.toString() == "smart voice writer" }.parent as View).performClick()
        texts = views(a.window.decorView).filterIsInstance<TextView>()
        assertTrue(texts.any { it.text.toString() == "লেখার ধরন" })
        assertTrue(texts.any { it.text.toString() == "ভাসমান বাটন চালু করুন" })
        texts.first { it.text.toString() == "‹ হোম" }.performClick()
        texts = views(a.window.decorView).filterIsInstance<TextView>()
        assertFalse(texts.any { it.text.toString() == "ভাসমান বাটন চালু করুন" })
        c.pause().stop().destroy()
    }
}
