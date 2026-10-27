package bd.app.aismartreply

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File

/** Compact floating recording panel. The previous app remains visible behind it. */
class VoiceWriterActivity : Activity() {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var output: EditText
    private lateinit var start: Button
    private lateinit var done: Button
    private lateinit var retry: Button
    private lateinit var mode: Spinner
    private lateinit var progress: ProgressBar
    private var resumed = false
    private var busy = false
    private var pendingStart = false
    private var firstResume = true
    private var generation = 0
    private var startedAt = 0L
    private var rawText = ""
    private val audio by lazy { File(noBackupFilesDir, "voice-writer/pending.m4a") }
    private val draft by lazy { File(noBackupFilesDir, "voice-writer/draft.txt") }
    private val edited by lazy { File(noBackupFilesDir, "voice-writer/edited.txt") }
    private val timer = object : Runnable {
        override fun run() {
            if (recorder == null) return
            val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
            status.text = "● রেকর্ড হচ্ছে  %02d:%02d".format(seconds / 60, seconds % 60)
            handler.postDelayed(this, 1000)
        }
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(24).toFloat() }
        }
        fun text(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(18, 32, 62)); setPadding(0, dp(4), 0, dp(4)) }
        val title = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(text("🎙 কথা বলে লিখুন", 21f), LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(Button(this).apply { this.text = "✕"; contentDescription = "প্যানেল বন্ধ করুন"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(52), dp(48)))
        root.addView(title)
        status = text("কথা শেষ হলে ‘শেষ করুন’ চাপুন", 17f); root.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true; visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        mode = Spinner(this).apply { adapter = ArrayAdapter(this@VoiceWriterActivity, android.R.layout.simple_spinner_dropdown_item,
            listOf("Refine • সুন্দর করে লিখুন", "Original • মূল কথা", "Learner • সহজ বাংলা")) }
        root.addView(mode)
        fun row() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; root.addView(this) }
        fun button(parent: LinearLayout, value: String, click: () -> Unit): Button = Button(this).also {
            it.text = value; it.isAllCaps = false; it.setOnClickListener { click() }
            parent.addView(it, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        val captureRow = row()
        start = button(captureRow, "🎙 শুরু করুন") { begin() }
        done = button(captureRow, "■ শেষ করুন") { finishCapture() }
        output = EditText(this).apply {
            textSize = 17f; gravity = Gravity.TOP; hint = "আপনার লেখা এখানে আসবে"; isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            visibility = View.GONE
        }
        root.addView(output, LinearLayout.LayoutParams(-1, dp(140)))
        val resultRow = row()
        button(resultRow, "লেখা কপি করুন") { copyText(output.text.toString()) }
        retry = button(resultRow, "আবার তৈরি করুন") { if (!busy && recorder == null) processAudio() }
        val extraRow = row()
        button(extraRow, "অডিও শুনুন") { if (player != null) stopPlayback() else if (!busy && recorder == null) playAudio() }
        button(extraRow, "নতুন রেকর্ডিং") { newRecording() }
        root.addView(text("আপনার কথাই লেখা হবে। শেষ করলে অডিও অ্যাপের সার্ভার হয়ে Google-এ যাবে। এখানে কোনো API key দিতে হবে না।", 12f))
        setContentView(ScrollView(this).apply { addView(root) })
        rawText = savedInstanceState?.getString("raw") ?: runCatching { draft.readText() }.getOrDefault("")
        output.setText(savedInstanceState?.getString("output") ?: runCatching { edited.readText() }.getOrDefault(rawText))
        if (output.text.isNotBlank()) output.visibility = View.VISIBLE
        mode.setSelection(savedInstanceState?.getInt("mode") ?: 0)
        firstResume = savedInstanceState == null
        // Old preview keys are no longer read or needed.
        getSharedPreferences("voice_writer_key", MODE_PRIVATE).edit().clear().apply()
        fitPanel()
        if (audio.exists()) status.text = "আগের অডিও রাখা আছে। আবার তৈরি বা নতুন রেকর্ডিং বেছে নিন।"
    }
    private fun fitPanel() {
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setLayout(minOf(dp(560), resources.displayMetrics.widthPixels - dp(24)), WindowManager.LayoutParams.WRAP_CONTENT)
        window.attributes = window.attributes.apply { y = dp(24) }
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); fitPanel() }
    private fun controls() {
        start.isEnabled = !busy && recorder == null && !audio.exists()
        done.isEnabled = recorder != null && !busy
        retry.isEnabled = audio.exists() && recorder == null && !busy
        mode.isEnabled = !busy && recorder == null
        output.isEnabled = !busy && recorder == null
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }
    private fun begin() {
        if (!resumed || busy || recorder != null) return
        if (audio.exists()) { status.text = "আগের অডিও রাখা আছে। নতুন রেকর্ডিং বাটন চাপুন।"; return }
        val prefs = getSharedPreferences("voice_writer", MODE_PRIVATE)
        if (!prefs.getBoolean("recording_notice_accepted", false)) {
            AlertDialog.Builder(this).setTitle("বাংলা ভয়েস লেখা")
                .setMessage("আপনার কথা ফোনে রেকর্ড হবে। শেষ করলে অ্যাপের সার্ভার ও Google Gemini দিয়ে লেখা তৈরি হবে। মাইক্রোফোনের অনুমতি শুধু রেকর্ডিংয়ের জন্য লাগবে।")
                .setPositiveButton("শুরু করুন") { _, _ -> prefs.edit().putBoolean("recording_notice_accepted", true).apply(); begin() }
                .setNegativeButton("পরে", null).show(); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100); return
        }
        stopPlayback(); startAudio()
    }
    @Suppress("DEPRECATION")
    private fun startAudio() {
        audio.parentFile?.mkdirs()
        try {
            val instance = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder = instance
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioSamplingRate(16000); instance.setAudioEncodingBitRate(48000)
            instance.setMaxDuration(600000); instance.setMaxFileSize(4_500_000)
            instance.setOutputFile(audio.absolutePath)
            instance.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    stopAudio(); status.text = "সীমায় পৌঁছে রেকর্ডিং থেমেছে। আবার তৈরি করুন চাপুন।"
                }
            }
            instance.setOnErrorListener { _, _, _ -> stopAudio(); status.text = "রেকর্ডিং থেমেছে। রাখা অডিও শুনে যাচাই করুন।" }
            instance.prepare(); instance.start(); startedAt = SystemClock.elapsedRealtime()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            handler.post(timer); controls()
        } catch (_: Exception) {
            recorder?.release(); recorder = null; audio.delete()
            status.text = "মাইক্রোফোন চালু হয়নি। অন্য অ্যাপে রেকর্ডিং বা ফোনকল চলছে কি না দেখুন।"; controls()
        }
    }
    private fun stopAudio(): Boolean {
        handler.removeCallbacks(timer)
        val instance = recorder ?: return audio.exists()
        recorder = null
        var valid = true
        try { instance.stop() } catch (_: RuntimeException) { valid = false; audio.delete() } finally { instance.release() }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); controls()
        return valid
    }
    private fun finishCapture() {
        if (busy || recorder == null) return
        if (stopAudio()) processAudio() else status.text = "অডিও খুব ছোট হয়েছে বা রেকর্ড হয়নি। আবার শুরু করুন।"
    }
    private fun selectedMode() = when (mode.selectedItemPosition) { 1 -> VoiceRefiner.Mode.ORIGINAL; 2 -> VoiceRefiner.Mode.LEARNER; else -> VoiceRefiner.Mode.REFINE }
    private fun processAudio() {
        if (!audio.exists() || audio.length() == 0L) { status.text = "কোনো রেকর্ড করা অডিও নেই"; return }
        stopPlayback(); busy = true; controls(); status.text = "বাংলা লেখা তৈরি হচ্ছে..."
        val selected = selectedMode(); val id = ++generation
        VoiceRefiner.transcribe(this, audio) { result ->
            if (isDestroyed || id != generation) return@transcribe
            result.onSuccess { text ->
                rawText = text; output.setText(text); output.visibility = View.VISIBLE; saveDraft()
                VoiceRefiner.refine(this, text, selected) { refined ->
                    if (isDestroyed || id != generation) return@refine
                    busy = false
                    refined.onSuccess {
                        output.setText(it); saveDraft()
                        if (resumed) copyText(it) else status.text = "লেখা প্রস্তুত। প্যানেলে ফিরে কপি করুন।"
                    }.onFailure { status.text = "${it.message ?: "লেখা সাজানো যায়নি"}। মূল লেখা কপি করতে পারবেন।" }
                    controls()
                }
            }.onFailure { busy = false; status.text = "${it.message ?: "নেটওয়ার্ক সমস্যা"}। অডিও ফোনে রাখা আছে।"; controls() }
        }
    }
    private fun copyText(value: String) {
        if (!resumed || busy || recorder != null) return
        if (value.isBlank()) { status.text = "কপি করার মতো লেখা নেই"; return }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Voice Writer", value))
        status.text = "✓ কপি হয়েছে। প্যানেল বন্ধ করে পেস্ট করুন।"
    }
    private fun saveDraft() { runCatching { draft.parentFile?.mkdirs(); draft.writeText(rawText); edited.writeText(output.text.toString()) } }
    private fun newRecording() {
        if (busy || recorder != null) return
        if (!audio.exists() && output.text.isBlank()) { begin(); return }
        AlertDialog.Builder(this).setTitle("আগের অডিও ও লেখা মুছে নতুন করে শুরু করবেন?")
            .setPositiveButton("নতুন করে শুরু") { _, _ -> stopPlayback(); audio.delete(); draft.delete(); edited.delete(); rawText = ""; output.setText(""); output.visibility = View.GONE; controls(); begin() }
            .setNegativeButton("আগেরটি রাখুন", null).show()
    }
    private fun stopPlayback() { player?.release(); player = null }
    private fun playAudio() {
        if (!audio.exists()) { status.text = "শোনার মতো অডিও নেই"; return }
        runCatching {
            val instance = MediaPlayer(); player = instance
            instance.setDataSource(audio.absolutePath)
            instance.setOnCompletionListener { stopPlayback(); status.text = "অডিও শোনা শেষ" }
            instance.setOnErrorListener { _, _, _ -> stopPlayback(); status.text = "অডিও চালানো যায়নি"; true }
            instance.prepare(); instance.start(); status.text = "অডিও চলছে... আবার চাপলে বন্ধ হবে"
        }.onFailure { stopPlayback(); status.text = "অডিও চালানো যায়নি" }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 100) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { pendingStart = true; if (resumed) { pendingStart = false; begin() } }
        else {
            status.text = "মাইক্রোফোনের অনুমতি দেওয়া হয়নি"
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) AlertDialog.Builder(this)
                .setMessage("App Settings থেকে মাইক্রোফোনের অনুমতি দিন।")
                .setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .setNegativeButton("পরে", null).show()
        }
    }
    override fun onResume() {
        super.onResume(); resumed = true; controls(); fitPanel()
        val shouldStart = pendingStart || (firstResume && !audio.exists())
        pendingStart = false; firstResume = false
        if (shouldStart) handler.post { if (resumed && recorder == null && !busy) begin() }
    }
    override fun onPause() {
        resumed = false; stopPlayback()
        if (recorder != null) { stopAudio(); status.text = "রেকর্ডিং বন্ধ হয়েছে। অডিও রাখা আছে।" }
        saveDraft(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("raw", rawText); outState.putString("output", output.text.toString()); outState.putInt("mode", mode.selectedItemPosition)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { generation++; handler.removeCallbacksAndMessages(null); stopPlayback(); stopAudio(); super.onDestroy() }
}
