package bd.app.aismartreply

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ProgressBar

/** Brief visible permission/start bridge, never the writer UI.
 * Android requires a visible activity when starting microphone foreground capture.
 * Recording and all controls stay in the overlay service after this bridge finishes.
 */
class VoiceWriterActivity : Activity() {
    private var visible = false
    private var requested = false
    private var starting = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        setContentView(ProgressBar(this))
        window.setGravity(Gravity.CENTER)
        window.setLayout((56 * resources.displayMetrics.density).toInt(), (56 * resources.displayMetrics.density).toInt())
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }
    override fun onResume() {
        super.onResume(); visible = true
        if (!starting && !requested) prepare()
    }
    override fun onPause() { visible = false; super.onPause() }
    private fun prepare() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requested = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        if (intent.getBooleanExtra("permission_only", false)) { finish(); return }
        val prefs = getSharedPreferences("voice_writer", MODE_PRIVATE)
        if (!prefs.getBoolean("recording_notice_accepted", false)) {
            requested = true
            AlertDialog.Builder(this).setTitle("Smart Audio")
                .setMessage("আপনার চাপ দেওয়ার পর মাইক্রোফোনে কথা রেকর্ড হবে। ✓ চাপলে অ্যাপের সার্ভার ও Google Gemini দিয়ে বাংলা লেখা তৈরি হবে। ✕ চাপলে রেকর্ডিং বাতিল হবে।")
                .setPositiveButton("শুরু করুন") { _, _ -> prefs.edit().putBoolean("recording_notice_accepted", true).apply(); beginOverlayCapture() }
                .setNegativeButton("পরে") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            return
        }
        beginOverlayCapture()
    }
    private fun beginOverlayCapture() {
        if (starting) return
        starting = true
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) { finish() }
        }
        val request = Intent(this, VoiceOverlayService::class.java).setAction(VoiceOverlayService.ACTION_RECORD)
            .putExtra("ready", receiver).putExtra("replace_pending", intent.getBooleanExtra("replace_pending", false))
        runCatching { startForegroundService(request) }.onFailure { finish() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 100) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requested = false
            if (visible) prepare() // Otherwise onResume performs the visible start.
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this).setMessage("Smart Audio-এর জন্য App Settings থেকে মাইক্রোফোন অনুমতি দিন।")
                .setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))); finish() }
                .setNegativeButton("পরে") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
        } else finish()
    }
}
