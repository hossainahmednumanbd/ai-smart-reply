package bd.app.aismartreply

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.view.Gravity

/** Opt-in voice capture screen. Does not interact with automatic replies. */
class VoiceWriterActivity : Activity() {
    private var recognizer: SpeechRecognizer? = null
    private val transcript = VoiceTranscriptBuffer()
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var apiKey: EditText
    private lateinit var mode: Spinner
    private var listening = false
    private var finishing = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        layout.addView(TextView(this).apply { text = "AI Voice Writer"; textSize = 24f })
        status = TextView(this).apply { text = "মাইক্রোফোন চালু করতে নিচে চাপুন"; textSize = 17f }
        output = TextView(this).apply { textSize = 17f; minHeight = 180 }
        layout.addView(status)
        layout.addView(output)
        mode = Spinner(this).apply { adapter = ArrayAdapter(this@VoiceWriterActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Original", "Refine", "Learner")) }
        layout.addView(mode)
        apiKey = EditText(this).apply { hint = "Gemini API Key (Refine / Learner)"; inputType = 129; setSingleLine(true) }
        layout.addView(apiKey)
        layout.addView(Button(this).apply { text = "কথা বলা শুরু করুন"; setOnClickListener { startListening() } })
        layout.addView(Button(this).apply { text = "শেষ করুন ও কপি করুন"; setOnClickListener { finishAndCopy() } })
        layout.addView(Button(this).apply { text = "বাতিল"; setOnClickListener { finish() } })
        setContentView(layout)
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "এই ফোনে Speech Recognition পাওয়া যায়নি"
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { status.text = "শুনছি..." }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { listening = false }
                    override fun onError(error: Int) {
                        listening = false
                        if (!finishing) status.text = "শোনা থেমেছে। আবার শুরু করুন (ত্রুটি: $error)"
                    }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        transcript.acceptFinal(value)
                        output.text = transcript.snapshot()
                        if (!finishing) status.text = "আরও বলতে আবার শুরু করুন"
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        transcript.acceptInterim(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
        finishing = false
        transcript.acceptInterim("")
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        recognizer?.startListening(request)
    }

    private fun finishAndCopy() {
        finishing = true
        if (listening) {
            recognizer?.stopListening()
            listening = false
            status.text = "শেষ অংশ সংগ্রহ করছি..."
            handler.postDelayed({ refineAndCopy() }, 1300)
        } else refineAndCopy()
    }

    private fun refineAndCopy() {
        val raw = transcript.snapshot()
        if (raw.isBlank()) { status.text = "কোনো নিশ্চিত লেখা পাওয়া যায়নি; আবার চেষ্টা করুন"; return }
        val selected = when (mode.selectedItemPosition) {
            1 -> VoiceRefiner.Mode.REFINE
            2 -> VoiceRefiner.Mode.LEARNER
            else -> VoiceRefiner.Mode.ORIGINAL
        }
        status.text = if (selected == VoiceRefiner.Mode.ORIGINAL) "কপি করছি..." else "AI দিয়ে Refine করা হচ্ছে..."
        VoiceRefiner.refine(raw, selected, apiKey.text.toString().trim()) { result ->
            result.onSuccess { copyText(it) }.onFailure {
                status.text = "Refine ব্যর্থ: ${it.message}. মূল লেখা সংরক্ষিত আছে।"
                output.text = raw
            }
        }
    }

    private fun copyText(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Voice Writer", text))
        status.text = "লেখা কপি হয়েছে। অন্য অ্যাপে পেস্ট করুন।"
        output.text = text
    }

    private fun copyConfirmed() {
        val text = transcript.snapshot()
        if (text.isBlank()) {
            status.text = "কোনো নিশ্চিত লেখা পাওয়া যায়নি; আবার চেষ্টা করুন"
            return
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Voice Writer", text))
        status.text = "লেখা কপি হয়েছে। অন্য অ্যাপে পেস্ট করুন।"
        output.text = text
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        super.onDestroy()
    }
}
