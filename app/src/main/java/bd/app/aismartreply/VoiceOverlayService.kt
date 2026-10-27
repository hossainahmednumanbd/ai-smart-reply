package bd.app.aismartreply

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File

/** Non-focusable overlay: idle bubble → recording bar → result card.
 * Keyboard and the current app retain focus. No settings/setup page is opened.
 */
class VoiceOverlayService : Service() {
    companion object {
        const val ACTION_STOP = "bd.app.aismartreply.VOICE_OVERLAY_STOP"
        const val ACTION_RECORD = "bd.app.aismartreply.VOICE_RECORD"
        const val ACTION_RETRY = "bd.app.aismartreply.VOICE_RETRY"
        const val ACTION_RESULT = "bd.app.aismartreply.VOICE_RESULT"
        private const val CHANNEL = "voice_writer_overlay"
        private const val ID = 409
        var running = false; private set
        var recording = false; private set
    }
    private var manager: WindowManager? = null
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var recorder: MediaRecorder? = null
    private var busy = false
    private var generation = 0
    private var startedAt = 0L
    private var wave: TextView? = null
    private var timerLabel: TextView? = null
    private var original = ""
    private var bengali = ""
    private var displayed = ""
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("voice_writer", MODE_PRIVATE) }
    private val positions by lazy { getSharedPreferences("voice_overlay", MODE_PRIVATE) }
    private val audio by lazy { File(noBackupFilesDir, "voice-writer/pending.m4a") }
    private val draft by lazy { File(noBackupFilesDir, "voice-writer/draft.txt") }
    private val edited by lazy { File(noBackupFilesDir, "voice-writer/edited.txt") }
    private val tick = object : Runnable {
        override fun run() {
            val instance = recorder ?: return
            val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
            timerLabel?.text = "%02d:%02d".format(seconds / 60, seconds % 60)
            val level = runCatching { instance.maxAmplitude }.getOrDefault(0)
            wave?.text = if (level < 100) "▁ ▁ ▁ ▁ ▁" else if (level < 4000) "▂ ▃ ▂ ▄ ▂" else "▃ ▇ ▅ █ ▄"
            handler.postDelayed(this, 180)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 24) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), Color.rgb(233, 232, 241)) }
    private fun text(value: String, size: Float = 16f) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(44, 44, 60)); gravity = Gravity.CENTER_VERTICAL }
    private fun button(value: String, description: String, action: () -> Unit) = Button(this).apply {
        text = value; contentDescription = description; isAllCaps = false; minWidth = 0; minimumWidth = 0
        setPadding(dp(4), 0, dp(4), 0); setOnClickListener { action() }
    }
    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "Smart Audio", NotificationManager.IMPORTANCE_LOW))
        if (!foreground(false)) { stopSelf(); return }
        running = true
        original = runCatching { draft.readText() }.getOrDefault("")
        bengali = runCatching { edited.readText() }.getOrDefault(original)
        displayed = bengali
        showIdle()
    }
    private fun foreground(microphone: Boolean): Boolean = runCatching {
        val stop = PendingIntent.getService(this, 1, Intent(this, VoiceOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).putExtra("open_smart_audio", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (microphone) "Smart Audio • রেকর্ড হচ্ছে" else "Smart Audio")
            .setContentText(if (microphone) "ভাসমান বারে ✓ দিয়ে শেষ বা ✕ দিয়ে বাতিল করুন" else "ভাসমান বাটন চালু আছে")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or (if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0))
        else if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        else startForeground(ID, notification)
    }.isSuccess
    private fun attach(view: View, width: Int, height: Int, result: Boolean = false) {
        bubble?.let { runCatching { manager?.removeView(it) } }
        val screen = resources.displayMetrics
        val w = minOf(dp(width), screen.widthPixels - dp(16))
        params = WindowManager.LayoutParams(w, if (height > 0) dp(height) else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = positions.getInt("x", dp(20)).coerceIn(0, (screen.widthPixels - w).coerceAtLeast(0))
            y = (positions.getInt("y", dp(220)) - if (result) dp(210) else 0).coerceIn(dp(24), (screen.heightPixels - dp(if (result) 330 else 70)).coerceAtLeast(dp(24)))
        }
        view.elevation = dp(8).toFloat()
        bubble = view
        runCatching { manager?.addView(view, params) }.onFailure { stopSelf() }
    }
    private fun drag(handle: View, click: (() -> Unit)? = null) {
        handle.setOnClickListener { click?.invoke() }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params?.x ?: 0; startY = params?.y ?: 0; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > ViewConfiguration.get(this).scaledTouchSlop || kotlin.math.abs(dy) > ViewConfiguration.get(this).scaledTouchSlop) moved = true
                    if (moved) params?.let {
                        it.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - it.width).coerceAtLeast(0))
                        it.y = (startY + dy).coerceIn(dp(24), (resources.displayMetrics.heightPixels - dp(80)).coerceAtLeast(dp(24)))
                        runCatching { manager?.updateViewLayout(bubble, it) }.onFailure { stopSelf() }
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) handle.performClick() else params?.let { positions.edit().putInt("x", it.x).putInt("y", it.y).apply() }; true
                }
                else -> false
            }
        }
    }
    private fun launchCapture(replacePending: Boolean = false) {
        if (busy || recorder != null) return
        runCatching { startActivity(Intent(this, VoiceWriterActivity::class.java)
            .putExtra("replace_pending", replacePending).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            .onFailure { showResult("মাইক্রোফোন চালু করতে Smart Audio খুলুন।") }
    }
    private fun showIdle() {
        val view = object : TextView(this) { override fun performClick(): Boolean { super.performClick(); return true } }.apply {
            text = "≋"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.rgb(135, 112, 179))
            contentDescription = "Smart Audio: কথা বলে লিখুন"; background = shape(Color.rgb(249, 248, 252))
        }
        attach(view, 44, 44); drag(view) { launchCapture() }
    }
    private fun showRecording() {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; background = shape(Color.WHITE); setPadding(dp(3), 0, dp(3), 0) }
        row.addView(button("✕", "রেকর্ডিং বাতিল করুন") { cancelCapture() }, LinearLayout.LayoutParams(dp(44), dp(48)))
        val handle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        wave = text("▁ ▁ ▁ ▁ ▁", 19f).apply { gravity = Gravity.CENTER; setTextColor(Color.rgb(139, 112, 178)) }
        timerLabel = text("00:00", 10f).apply { gravity = Gravity.CENTER }
        handle.addView(wave); handle.addView(timerLabel)
        row.addView(handle, LinearLayout.LayoutParams(0, dp(48), 1f)); drag(handle)
        row.addView(button("✓", "কথা শেষ করে লেখা তৈরি করুন") { finishCapture() }, LinearLayout.LayoutParams(dp(44), dp(48)))
        attach(row, 220, 52)
    }
    private fun showBusy(message: String) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = shape(Color.WHITE); setPadding(dp(10), dp(5), dp(5), dp(5)) }
        row.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(28), dp(28)))
        row.addView(text(message, 13f), LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(button("✕", "অডিও রেখে অপেক্ষা বাতিল করুন") { generation++; busy = false; showIdle() }, LinearLayout.LayoutParams(dp(40), dp(42)))
        attach(row, 255, 52)
    }
    private fun showResult(message: String = "") {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(Color.WHITE, 20); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("BN", "বাংলা লেখা দেখুন") { displayed = bengali; showResult() }, LinearLayout.LayoutParams(dp(48), dp(40)))
        header.addView(button("EN", "ইংরেজি অনুবাদ করুন") { translateEnglish() }, LinearLayout.LayoutParams(dp(48), dp(40)))
        val grip = text("Smart Audio", 11f); header.addView(grip, LinearLayout.LayoutParams(0, dp(40), 1f)); drag(grip)
        header.addView(button("✕", "লেখা রেখে বাক্স বন্ধ করুন") { showIdle() }, LinearLayout.LayoutParams(dp(42), dp(40)))
        root.addView(header)
        val content = text(if (displayed.isBlank()) "লেখা এখনো তৈরি হয়নি" else displayed, 17f)
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, minOf(dp(170), maxOf(dp(60), displayed.length / 25 * dp(23)))))
        if (message.isNotBlank()) root.addView(text(message, 12f).apply { setTextColor(Color.rgb(150, 72, 40)) })
        val actions = LinearLayout(this)
        actions.addView(button("কপি", "লেখা কপি করুন") { copyText() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(button("↻", "রাখা অডিও থেকে আবার তৈরি করুন") { processAudio() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(button("শেয়ার", "লেখা শেয়ার করুন") { shareText() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        actions.addView(button("নতুন", "নতুন রেকর্ডিং শুরু করুন") { launchCapture(true) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(actions); attach(root, 340, 0, true)
    }
    @Suppress("DEPRECATION")
    private fun beginRecording(replacePending: Boolean) {
        if (busy || recorder != null) return
        if (!Settings.canDrawOverlays(this)) return
        if (audio.exists() && !prefs.getBoolean("pending_processed", false) && !replacePending) { showResult("আগের অডিও আছে। ↻ চাপুন অথবা নতুন রেকর্ডিং বেছে নিন।"); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || !foreground(true)) { showResult("মাইক্রোফোন অনুমতি পাওয়া যায়নি। Smart Audio থেকে অনুমতি দিন।"); return }
        try {
            audio.parentFile?.mkdirs()
            val instance = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder(); recorder = instance
            instance.setAudioSource(MediaRecorder.AudioSource.MIC); instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); instance.setAudioSamplingRate(16000); instance.setAudioEncodingBitRate(48000)
            instance.setMaxDuration(600000); instance.setMaxFileSize(4_500_000); instance.setOutputFile(audio.absolutePath)
            instance.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) finishCapture() }
            instance.setOnErrorListener { _, _, _ -> releaseCapture(); foreground(false); showResult("রেকর্ডিং থেমেছে। অডিও রেখে আবার চেষ্টা করুন।") }
            instance.prepare(); instance.start(); recording = true; startedAt = SystemClock.elapsedRealtime()
            prefs.edit().putBoolean("pending_processed", false).apply(); displayed = ""; original = ""; bengali = ""
            showRecording(); handler.post(tick)
        } catch (_: Exception) {
            recorder?.release(); recorder = null; recording = false; audio.delete(); foreground(false)
            showResult("মাইক্রোফোন চালু হয়নি। অন্য অ্যাপে কল বা রেকর্ডিং চলছে কি না দেখুন।")
        }
    }
    private fun releaseCapture(): Boolean {
        handler.removeCallbacks(tick); val instance = recorder ?: return audio.exists()
        recorder = null; recording = false
        var valid = true
        try { instance.stop() } catch (_: RuntimeException) { valid = false; audio.delete() } finally { instance.release() }
        wave = null; timerLabel = null; return valid
    }
    private fun cancelCapture() {
        generation++; releaseCapture(); audio.delete(); busy = false; prefs.edit().putBoolean("pending_processed", false).apply()
        foreground(false); showIdle()
    }
    private fun finishCapture() {
        if (recorder == null || busy) return
        val valid = releaseCapture(); foreground(false)
        if (valid) processAudio() else showResult("অডিও খুব ছোট হয়েছে বা রেকর্ড হয়নি। নতুন করে চেষ্টা করুন।")
    }
    private fun mode() = when (prefs.getString("mode", "refine")) { "original" -> VoiceRefiner.Mode.ORIGINAL; "learner" -> VoiceRefiner.Mode.LEARNER; else -> VoiceRefiner.Mode.REFINE }
    private fun processAudio() {
        if (busy || recorder != null) return
        if (!audio.exists() || audio.length() == 0L) { showResult("কোনো রেকর্ড করা অডিও নেই। নতুন রেকর্ডিং শুরু করুন।"); return }
        busy = true; val id = ++generation; val selected = mode(); showBusy("বাংলা লেখা তৈরি হচ্ছে…")
        VoiceRefiner.transcribe(this, audio) { result ->
            if (!running || id != generation) return@transcribe
            result.onSuccess { raw ->
                original = raw; bengali = raw; displayed = raw; saveDraft(); prefs.edit().putBoolean("pending_processed", true).apply()
                VoiceRefiner.refine(this, raw, selected) { refined ->
                    if (!running || id != generation) return@refine
                    busy = false
                    refined.onSuccess { bengali = it; displayed = it; saveDraft(); showResult() }
                        .onFailure { showResult("${it.message ?: "লেখা সাজানো যায়নি"}। মূল লেখা রাখা আছে।") }
                }
            }.onFailure { busy = false; showResult("${it.message ?: "নেটওয়ার্ক সমস্যা"}। অডিও রাখা আছে।") }
        }
    }
    private fun translateEnglish() {
        if (busy || bengali.isBlank()) return
        busy = true; val id = ++generation; showBusy("ইংরেজি তৈরি হচ্ছে…")
        VoiceRefiner.refine(this, bengali, VoiceRefiner.Mode.ENGLISH) { result ->
            if (!running || id != generation) return@refine
            busy = false; result.onSuccess { displayed = it; showResult() }.onFailure { showResult(it.message ?: "অনুবাদ হয়নি") }
        }
    }
    private fun saveDraft() { runCatching { draft.parentFile?.mkdirs(); draft.writeText(original); edited.writeText(bengali) } }
    private fun copyText() {
        if (busy || recorder != null || displayed.isBlank()) return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Smart Audio", displayed))
        Toast.makeText(this, "লেখা কপি হয়েছে। পেস্ট করুন।", Toast.LENGTH_SHORT).show()
    }
    private fun shareText() {
        if (displayed.isBlank() || busy) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, displayed)
        runCatching { startActivity(Intent.createChooser(send, "Smart Audio লেখা শেয়ার").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_RECORD -> { beginRecording(intent.getBooleanExtra("replace_pending", false)); intent.getParcelableExtra<ResultReceiver>("ready")?.send(0, Bundle()) }
            ACTION_RETRY -> processAudio()
            ACTION_RESULT -> showResult()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        generation++; releaseCapture(); handler.removeCallbacksAndMessages(null)
        bubble?.let { runCatching { manager?.removeView(it) } }; bubble = null; running = false; recording = false
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
