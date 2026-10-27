package bd.app.aismartreply

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdatesTest {
    private fun release(code: Int, preview: Boolean = false, draft: Boolean = false, url: String = "https://github.com/${AppUpdates.REPO}/releases/download/v1/ai-smart-reply-$code.apk") =
        JSONObject().put("draft", draft).put("prerelease", preview).put("name", "Version $code")
            .put("assets", JSONArray().put(JSONObject().put("name", "ai-smart-reply-$code.apk").put("state", "uploaded").put("browser_download_url", url)))

    @Test fun rejectsForeignOrInsecureDownloadLinks() {
        for (url in listOf("http://github.com/${AppUpdates.REPO}/releases/download/v/a.apk",
            "https://github.com.evil.test/${AppUpdates.REPO}/releases/download/v/a.apk",
            "https://github.com/another/repo/releases/download/v/a.apk",
            "https://github.com/${AppUpdates.REPO}/releases/download/../../evil.apk",
            "https://github.com/${AppUpdates.REPO}/releases/download/%2e%2e/evil.apk")) assertFalse(url, AppUpdates.safeDownload(url))
    }
    @Test fun neverPromptsForDowngradeOrSameVersion() {
        assertNull(AppUpdates.newest(JSONArray().put(release(14)).put(release(15)), 15, true))
    }
    @Test fun stableUsersNeverReceiveDraftOrPreviewBuilds() {
        val feed = JSONArray().put(release(20, draft = true)).put(release(19, preview = true)).put(release(16))
        assertEquals(16L, AppUpdates.newest(feed, 15, false)?.code)
        assertEquals(19L, AppUpdates.newest(feed, 15, true)?.code)
    }
    @Test fun malformedAndForeignAssetsAreIgnored() {
        val feed = JSONArray().put(JSONObject()).put(release(99, url = "https://evil.test/a.apk")).put(release(16))
        assertEquals(16L, AppUpdates.newest(feed, 15, true)?.code)
    }
}
