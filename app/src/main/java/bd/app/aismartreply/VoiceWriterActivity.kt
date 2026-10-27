package bd.app.aismartreply

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.*
import android.view.*
import android.widget.*
import java.io.File

/** Visible, user-initiated capture only. No dependency on the Auto Reply pipeline. */
class VoiceWriterActivity : Activity() {
    private var recognizer: SpeechRecognizer? = null
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private val transcript = VoiceTranscriptBuffer()
    private val session = VoiceCaptureSession()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var output: EditText
    private lateinit var apiKey: EditText
    private lateinit var mode: Spinner
    private lateinit var capture: Spinner
    private lateinit var start: Button
    private lateinit var done: Button
    private lateinit var keys: VoiceKeyStore
    private var continuous = false
    private var busy = false
    private var resumed = false
    private var pendingPermissionStart = false
    private var requestGeneration = 0
    private var rawText = ""
    private var autoCopy = false
    private var retries = 0
    private val audio by lazy { File(noBackupFilesDir, "voice-writer/pending.m4a") }
    private val draft by lazy { File(noBackupFilesDir, "voice-writer/draft.txt") }
    private val edited by lazy { File(noBackupFilesDir, "voice-writer/edited.txt") }
    private val stopTimeout = Runnable { settleSpeech(session.generation, "শেষ অংশ নিশ্চিত হয়নি। লেখা যাচাই করুন।", true) }
    private val restart = Runnable { if (continuous && resumed) startSegment() }
    private val audioLimit = Runnable { stopAudio(); status.text = "১০ মিনিটের সীমায় রেকর্ডিং থেমেছে। অডিও থেকে লেখা তৈরি করুন।" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        keys = VoiceKeyStore(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24))
            setBackgroundColor(android.graphics.Color.rgb(246, 249, 255))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(20), dp(16) + insets.systemWindowInsetTop, dp(20), dp(24) + insets.systemWindowInsetBottom)
                insets
            }
        }
        fun label(value: String, size: Float = 15f) = TextView(this).apply { text = value; textSize = size; setPadding(0, dp(6), 0, dp(6)) }
        fun button(value: String, action: () -> Unit): Button = Button(this).also { b -> b.text = value; b.setOnClickListener { action() }; layout.addView(b) }
        layout.addView(label("🎙 বাংলা Voice Writer", 24f))
        layout.addView(label("শুধু আপনার লেখা তৈরি ও কপি করে। অন্য অ্যাপে পেস্ট করুন।"))
        status = label("কথা বলার পদ্ধতি বেছে শুরু করুন", 17f); layout.addView(status)
        capture = Spinner(this).apply { adapter = ArrayAdapter(this@VoiceWriterActivity, android.R.layout.simple_spinner_dropdown_item,
            listOf("বাংলা Speech Recognition", "নিরবচ্ছিন্ন অডিও রেকর্ডিং + Gemini")) }
        layout.addView(capture)
        mode = Spinner(this).apply { adapter = ArrayAdapter(this@VoiceWriterActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Original: মূল কথা", "Refine: বানান ও যতিচিহ্ন", "Learner: সহজ বাংলা")) }
        layout.addView(mode)
        apiKey = EditText(this).apply {
            hint = "আপনার Gemini API Key"; inputType = 129; setSingleLine(true)
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setText(keys.read())
        }
        layout.addView(apiKey)
        button("API Key সেভ করুন") {
            runCatching { keys.save(apiKey.text.toString()) }.onSuccess { status.text = "Key ফোনে এনক্রিপ্ট করে রাখা হয়েছে" }
                .onFailure { status.text = "Key সেভ হয়নি; আবার চেষ্টা করুন" }
        }
        button("সেভ করা Key মুছুন") { keys.clear(); apiKey.setText(""); status.text = "সেভ করা Key মুছে দেওয়া হয়েছে" }
        layout.addView(label("Speech Recognition ফোনের speech service ব্যবহার করে। Refine ও অডিও থেকে লেখা তৈরিতে Google Gemini-তে তথ্য যাবে এবং আপনার API-র খরচ হতে পারে।"))
        start = button("কথা বলা শুরু করুন") { begin() }
        done = button("শেষ করুন ও লেখা তৈরি করুন") { finishCapture() }
        output = EditText(this).apply {
            hint = "লেখা এখানে আসবে। কপি করার আগে সম্পাদনা করতে পারবেন।"; textSize = 17f
            minLines = 5; gravity = Gravity.TOP; isSaveEnabled = false
        }
        layout.addView(output)
        button("লেখা যাচাই করে কপি করুন") { copyText(output.text.toString()) }
        button("লেখা আবার Refine করুন") { if (!busy && recorder == null && !continuous && session.state == VoiceCaptureSession.State.IDLE) { rawText = output.text.toString(); saveDraft(); refineText() } }
        button("মূল লেখা দেখুন") { if (!busy && !continuous && recorder == null && session.state == VoiceCaptureSession.State.IDLE) { output.setText(rawText); status.text = "মূল লেখা দেখানো হচ্ছে" } }
        button("রাখা অডিও শুনুন / বন্ধ করুন") {
            if (player != null) stopPlayback()
            else if (!busy && recorder == null && !continuous && session.state == VoiceCaptureSession.State.IDLE) playAudio()
        }
        button("রাখা অডিও থেকে আবার লেখা তৈরি করুন") { if (!busy && recorder == null && !continuous && session.state == VoiceCaptureSession.State.IDLE) processAudio() }
        button("নতুন লেখা / রেকর্ডিং মুছুন") {
            if (!busy) AlertDialog.Builder(this).setTitle("এই লেখা ও রাখা অডিও মুছবেন?")
                .setPositiveButton("মুছুন") { _, _ -> clearDraft() }.setNegativeButton("বাতিল", null).show()
        }
        button("ফিরে যান") { finish() }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(layout) })
        rawText = savedInstanceState?.getString("raw") ?: runCatching { draft.readText() }.getOrDefault("")
        transcript.acceptFinal(rawText)
        output.setText(savedInstanceState?.getString("output") ?: runCatching { edited.readText() }.getOrDefault(rawText))
        mode.setSelection(savedInstanceState?.getInt("mode") ?: 0)
        capture.setSelection(savedInstanceState?.getInt("capture") ?: 0)
        if (audio.exists()) status.text = "আগের অডিও রাখা আছে। আবার লেখা তৈরি করতে পারবেন।"
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun selectedMode() = VoiceRefiner.Mode.entries[mode.selectedItemPosition]
    private fun controls() {
        val active = continuous || recorder != null || session.state != VoiceCaptureSession.State.IDLE
        start.isEnabled = !active && !busy; done.isEnabled = active && !busy
        capture.isEnabled = !active && !busy; mode.isEnabled = !active && !busy; apiKey.isEnabled = !active && !busy
        output.isEnabled = !active && !busy
    }
    private fun begin() {
        if (busy || continuous || recorder != null) return
        stopPlayback()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this).setTitle("মাইক্রোফোনের অনুমতি")
                .setMessage("আপনার কথা থেকে বাংলা লেখা তৈরি করতে মাইক্রোফোন লাগবে। রেকর্ডিং বন্ধ করলে বা স্ক্রিন ছেড়ে গেলে মাইক্রোফোন বন্ধ হবে।")
                .setPositiveButton("চালিয়ে যান") { _, _ -> requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100) }
                .setNegativeButton("বাতিল", null).show(); return
        }
        if (capture.selectedItemPosition == 1) {
            if (apiKey.text.isBlank()) { status.text = "অডিও থেকে লেখা তৈরির জন্য Gemini API Key দিন"; return }
            if (audio.exists()) { status.text = "আগের অডিও থেকে লেখা তৈরি করুন অথবা নতুন লেখার বাটনে সেটি মুছুন"; return }
            AlertDialog.Builder(this).setTitle("অডিও রেকর্ডিং ও Gemini")
                .setMessage("এখন অডিও ফোনে রেকর্ড হবে, সর্বোচ্চ ১০ মিনিট। লেখা তৈরির সময় অডিও Google Gemini-তে যাবে। মূল অডিও আপনি মুছে না দেওয়া পর্যন্ত এই ফোনে থাকবে।")
                .setPositiveButton("রেকর্ড করুন") { _, _ -> if (resumed) startAudio() }.setNegativeButton("বাতিল", null).show()
        } else {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) { status.text = "এই ফোনে speech service নেই। অডিও রেকর্ডিং পদ্ধতি ব্যবহার করুন।"; return }
            rawText = output.text.toString().trim(); transcript.reset(); transcript.acceptFinal(rawText)
            retries = 0; continuous = true; startSegment()
        }
    }
    private fun startSegment() {
        if (!continuous || !resumed) return
        recognizer?.destroy(); recognizer = null
        val id = session.begin()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (session.accepts(id)) status.text = "শুনছি... কথা শেষ হলে নিচে চাপুন" }
                override fun onBeginningOfSpeech() { retries = 0 }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                // Remain active until final result or error, including after end-of-speech.
                override fun onEndOfSpeech() {}
                override fun onResults(results: Bundle?) {
                    if (!session.accepts(id)) return
                    val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (value.isBlank()) { settleSpeech(id, "শেষ অংশ নিশ্চিত হয়নি; লেখা যাচাই করুন", true); return }
                    transcript.acceptFinal(value); retries = 0
                    settleSpeech(id, null, false)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (session.accepts(id)) transcript.acceptInterim(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onError(error: Int) {
                    if (!session.accepts(id)) return
                    val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (recoverable && continuous && retries++ < 3) settleSpeech(id, null, true)
                    else { continuous = false; autoCopy = false; settleSpeech(id, "শোনা থেমেছে (ত্রুটি $error)। লেখা যাচাই করে আবার শুরু করুন। দীর্ঘ কথার জন্য অডিও রেকর্ডিং ব্যবহার করুন।", true) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        controls()
        runCatching { recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }) }.onFailure { continuous = false; settleSpeech(id, "মাইক্রোফোন চালু হয়নি। অনুমতি ও speech service যাচাই করুন।", true) }
    }
    private fun settleSpeech(id: Int, warning: String?, preserveInterim: Boolean) {
        if (!session.settle(id)) return
        handler.removeCallbacks(stopTimeout)
        if (preserveInterim) {
            // A provisional tail is never silently discarded or automatically copied as confirmed.
            val tail = transcript.preview().removePrefix(transcript.snapshot()).trim()
            if (tail.isNotBlank()) { transcript.acceptFinal(tail); autoCopy = false }
        }
        rawText = transcript.snapshot(); output.setText(rawText); saveDraft()
        recognizer?.destroy(); recognizer = null
        if (warning != null) { status.text = warning; autoCopy = false }
        if (continuous) { handler.postDelayed(restart, 250); status.text = "পরের অংশ শুনতে প্রস্তুত হচ্ছে..." }
        else if (autoCopy) { autoCopy = false; refineText() }
        controls()
    }
    private fun finishCapture() {
        if (busy) return
        if (recorder != null) { stopAudio(); processAudio(); return }
        continuous = false; handler.removeCallbacks(restart); autoCopy = true
        if (session.state != VoiceCaptureSession.State.IDLE) {
            session.stop(); status.text = "শেষ অংশের ফলাফলের অপেক্ষা করছি..."
            handler.postDelayed(stopTimeout, 5000)
            runCatching { recognizer?.stopListening() }.onFailure { settleSpeech(session.generation, "শেষ অংশ নিশ্চিত হয়নি; লেখা যাচাই করুন", true) }
        } else { autoCopy = false; rawText = output.text.toString(); refineText() }
        controls()
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
                    stopAudio(); status.text = "রেকর্ডিংয়ের সীমায় পৌঁছেছে। রাখা অডিও থেকে লেখা তৈরি করুন।"
                }
            }
            instance.setOnErrorListener { _, _, _ -> stopAudio(); status.text = "রেকর্ডিংয়ে ত্রুটি। অডিও রাখা থাকলে আবার চেষ্টা করুন।" }
            instance.prepare(); instance.start()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            handler.postDelayed(audioLimit, 600000)
            status.text = "অডিও রেকর্ড হচ্ছে... সর্বোচ্চ ১০ মিনিট"; controls()
        } catch (_: Exception) {
            recorder?.release(); recorder = null; audio.delete()
            status.text = "রেকর্ডিং শুরু হয়নি। মাইক্রোফোন অন্য অ্যাপে ব্যস্ত কি না যাচাই করুন।"; controls()
        }
    }
    private fun stopAudio() {
        handler.removeCallbacks(audioLimit)
        val instance = recorder ?: return
        recorder = null
        try { instance.stop() } catch (_: RuntimeException) { audio.delete() } finally { instance.release() }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); controls()
    }
    private fun stopPlayback() { player?.release(); player = null }
    private fun playAudio() {
        if (!audio.exists()) { status.text = "রাখা অডিও পাওয়া যায়নি"; return }
        runCatching {
            val instance = MediaPlayer()
            player = instance
            instance.apply {
                setDataSource(audio.absolutePath)
                setOnCompletionListener { stopPlayback(); status.text = "অডিও শোনা শেষ" }
                setOnErrorListener { _, _, _ -> stopPlayback(); status.text = "অডিও চালানো যায়নি"; true }
                prepare(); start()
            }
            status.text = "রাখা অডিও চলছে..."
        }.onFailure { stopPlayback(); status.text = "অডিও চালানো যায়নি" }
    }
    private fun processAudio() {
        stopPlayback()
        if (!audio.exists() || audio.length() == 0L) { status.text = "রেকর্ড করা অডিও পাওয়া যায়নি"; return }
        if (apiKey.text.isBlank()) { status.text = "Gemini API Key দিন; অডিও ফোনে রাখা আছে"; return }
        busy = true; controls(); status.text = "অডিও থেকে বাংলা লেখা তৈরি হচ্ছে..."
        val id = ++requestGeneration
        VoiceRefiner.transcribe(audio, apiKey.text.toString().trim()) { result ->
            if (isDestroyed || id != requestGeneration) return@transcribe
            busy = false
            result.onSuccess {
                rawText = it; output.setText(it); transcript.reset(); transcript.acceptFinal(it); saveDraft()
                refineText()
            }.onFailure { status.text = "লেখা তৈরি হয়নি: ${it.message ?: "নেটওয়ার্ক যাচাই করুন"}। অডিও ফোনে রাখা আছে।"; controls() }
        }
    }
    private fun refineText() {
        if (rawText.isBlank()) { status.text = "কোনো লেখা পাওয়া যায়নি"; controls(); return }
        busy = true; controls(); status.text = "লেখা তৈরি হচ্ছে..."
        val id = ++requestGeneration
        VoiceRefiner.refine(rawText, selectedMode(), apiKey.text.toString().trim()) { result ->
            if (isDestroyed || id != requestGeneration) return@refine
            busy = false
            result.onSuccess {
                output.setText(it); saveDraft()
                // Clipboard writes require a visible foreground activity. Never copy from a late callback.
                if (resumed) copyText(it) else status.text = "লেখা প্রস্তুত। ফিরে এসে যাচাই করে কপি করুন।"
            }.onFailure { output.setText(rawText); status.text = "Refine ব্যর্থ: ${it.message ?: "নেটওয়ার্ক যাচাই করুন"}। মূল লেখা রাখা আছে।" }
            controls()
        }
    }
    private fun copyText(value: String) {
        if (value.isBlank()) { status.text = "কপি করার মতো লেখা নেই"; return }
        if (!resumed || busy || continuous || recorder != null || session.state != VoiceCaptureSession.State.IDLE) return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Voice Writer", value))
        status.text = "লেখা কপি হয়েছে। অন্য অ্যাপে পেস্ট করুন।"
    }
    private fun saveDraft() { runCatching { draft.parentFile?.mkdirs(); draft.writeText(rawText); edited.writeText(output.text.toString()) } }
    private fun clearDraft() {
        stopPlayback()
        continuous = false; autoCopy = false; handler.removeCallbacksAndMessages(null)
        session.cancel(); recognizer?.destroy(); recognizer = null; stopAudio()
        rawText = ""; transcript.reset(); output.setText(""); audio.delete(); draft.delete(); edited.delete()
        status.text = "নতুন লেখা শুরু করতে পারবেন"; controls()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 100) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingPermissionStart = true
            if (resumed) { pendingPermissionStart = false; begin() }
        }
        else {
            status.text = "মাইক্রোফোনের অনুমতি ছাড়া কথা রেকর্ড করা যাবে না"
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) AlertDialog.Builder(this)
                .setMessage("ফোনের App Settings থেকে মাইক্রোফোনের অনুমতি দিন।")
                .setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .setNegativeButton("পরে", null).show()
        }
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (::start.isInitialized) controls()
        if (pendingPermissionStart) { pendingPermissionStart = false; handler.post { if (resumed) begin() } }
    }
    override fun onPause() {
        stopPlayback()
        resumed = false; continuous = false; autoCopy = false
        handler.removeCallbacks(restart); handler.removeCallbacks(stopTimeout)
        if (session.state != VoiceCaptureSession.State.IDLE) {
            val pending = transcript.preview(); session.cancel(); recognizer?.cancel(); recognizer?.destroy(); recognizer = null
            transcript.reset(); transcript.acceptFinal(pending); rawText = pending; output.setText(rawText); saveDraft()
            status.text = "স্ক্রিন ছেড়ে যাওয়ায় শোনা বন্ধ হয়েছে। শেষ অংশ যাচাই করুন।"
        }
        if (recorder != null) { stopAudio(); status.text = "রেকর্ডিং বন্ধ হয়েছে। অডিও রাখা আছে।" }
        if (!busy) { runCatching { edited.apply { parentFile?.mkdirs() }.writeText(output.text.toString()) } }
        controls(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("raw", rawText); outState.putString("output", output.text.toString())
        outState.putInt("mode", mode.selectedItemPosition); outState.putInt("capture", capture.selectedItemPosition)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        requestGeneration++; handler.removeCallbacksAndMessages(null); session.cancel()
        recognizer?.destroy(); stopPlayback(); stopAudio(); super.onDestroy()
    }
}
