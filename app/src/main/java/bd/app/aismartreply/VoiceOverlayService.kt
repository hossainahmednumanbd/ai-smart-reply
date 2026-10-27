package bd.app.aismartreply

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.pm.ServiceInfo

/** User-enabled, draggable floating launcher. Microphone is only used after tapping. */
class VoiceOverlayService : Service() {
    companion object {
        const val ACTION_STOP = "bd.app.aismartreply.VOICE_OVERLAY_STOP"
        private const val CHANNEL = "voice_writer_overlay"
        private const val ID = 409
    }
    private var manager: WindowManager? = null
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Voice Writer floating button", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, VoiceOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AI Voice Writer")
            .setContentIntent(PendingIntent.getActivity(this, 2, Intent(this, VoiceWriterActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true)
            .setContentText("Floating button is enabled. Tap to write; stop from notification.")
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (52 * resources.displayMetrics.density).toInt()
        val prefs = getSharedPreferences("voice_overlay", MODE_PRIVATE)
        params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 20)
            y = prefs.getInt("y", 220)
            x = x.coerceIn(0, (resources.displayMetrics.widthPixels - size).coerceAtLeast(0))
            y = y.coerceIn(0, (resources.displayMetrics.heightPixels - size - 80).coerceAtLeast(0))
        }
        val view = object : TextView(this) {
            override fun performClick(): Boolean { super.performClick(); return true }
        }.apply {
            contentDescription = "বাংলা Voice Writer খুলুন"
            text = "🎙"; textSize = 25f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 116, 244), Color.rgb(47, 207, 199))).apply {
                cornerRadius = size.toFloat()
            }
            elevation = 10f
        }
        view.setOnClickListener {
            val intent = Intent(this, VoiceWriterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            runCatching { startActivity(intent) }
        }
        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    initialX = params?.x ?: 0; initialY = params?.y ?: 0
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > ViewConfiguration.get(this).scaledTouchSlop || kotlin.math.abs(dy) > ViewConfiguration.get(this).scaledTouchSlop) moved = true
                    params?.let {
                        it.x = (initialX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - size).coerceAtLeast(0))
                        it.y = (initialY + dy).coerceIn(0, (resources.displayMetrics.heightPixels - size - 80).coerceAtLeast(0))
                        runCatching { manager?.updateViewLayout(view, it) }.onFailure { stopSelf() }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        view.performClick()
                    } else {
                        params?.let { prefs.edit().putInt("x", it.x).putInt("y", it.y).apply() }
                    }
                    true
                }
                else -> false
            }
        }
        bubble = view
        runCatching { manager?.addView(view, params) }.onFailure { stopSelf() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        bubble?.let { runCatching { manager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
