package bd.app.aismartreply

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Visible, user-controlled service; never starts from a new incoming message. */
class AutoReplyService : Service() {
    companion object {
        private const val CHANNEL = "ai_auto_reply"
        private const val NOTIFICATION_ID = 201
        private const val STOP = "bd.app.aismartreply.STOP_AI_AUTO"
        @Volatile var running = false
            private set

        // Called while the activity is visible, after opt-in or on resume.
        fun sync(ctx: Context) {
            val intent = Intent(ctx, AutoReplyService::class.java)
            if (!ReplyStore.enabled(ctx, "auto") && !(ReplyStore.enabled(ctx, "ai_auto") && AiEngine.configured(ctx))) {
                ctx.stopService(intent)
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            } catch (_: Exception) {
                ctx.getSharedPreferences("ai_auto_state", Context.MODE_PRIVATE).edit()
                    .putString("status", "ব্যাকগ্রাউন্ড সেবা চালু হয়নি; অ্যাপ খুলে আবার চেষ্টা করুন").apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "AI অটো রিপ্লাই", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { ReplyStore.set(this, "ai_auto", false); ReplyStore.set(this, "auto", false) }
        if (!ReplyStore.enabled(this, "auto") && !(ReplyStore.enabled(this, "ai_auto") && AiEngine.configured(this))) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, AutoReplyService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("অটো রিপ্লাই চালু আছে")
            .setContentText("নতুন মেসেজে স্বয়ংক্রিয় উত্তর সক্রিয়। সেটিংস খুলতে চাপুন।")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                "বন্ধ করুন", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
        running = true
        ReplyListener.reconnect(this)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
