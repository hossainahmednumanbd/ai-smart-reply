package bd.app.aismartreply

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.InputType
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var body: LinearLayout
    private val ink = Color.rgb(24, 39, 59)
    private fun text(s: String, size: Float = 16f): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(ink); setPadding(0, 10, 0, 14)
    }
    private fun button(label: String, action: () -> Unit) {
        body.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }
    private fun toggle(label: String, key: String) {
        body.addView(Switch(this).apply {
            text = label; textSize = 16f; setTextColor(ink)
            isChecked = ReplyStore.enabled(this@MainActivity, key)
            setPadding(0, 18, 0, 18)
            setOnCheckedChangeListener { _, checked ->
                if (key == "auto" && checked) {
                    isChecked = false
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("অটো রিপ্লাই চালু করবেন?")
                        .setMessage("সমর্থিত নতুন WhatsApp/Messenger নোটিফিকেশনে আপনার অনুমোদন ছাড়াই নির্দিষ্ট টেমপ্লেট উত্তর পাঠানো হতে পারে। ভুল উত্তরও যেতে পারে। এটি AI নয়। আপনি যেকোনো সময় বন্ধ করতে পারবেন।")
                        .setNegativeButton("বাতিল", null)
                        .setPositiveButton("চালু করুন") { _, _ ->
                            ReplyStore.set(this@MainActivity, key, true); isChecked = true
                        }.show()
                } else ReplyStore.set(this@MainActivity, key, checked)
            }
        })
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); if (::body.isInitialized) render() }
    private fun hasAccess(): Boolean {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= 27) manager.isNotificationListenerAccessGranted(ComponentName(this, ReplyListener::class.java))
        else Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    }
    private fun render() {
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 34, 32, 60)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        scroll.addView(body); setContentView(scroll)
        body.addView(text("AI Smart Reply", 28f))
        body.addView(text("Notification Assistant • Local demo", 14f))
        body.addView(text("এখনও প্রকৃত AI সংযুক্ত নয়। উত্তরগুলো ডিভাইসের নির্দিষ্ট টেমপ্লেট থেকে তৈরি হয়। ব্যক্তিগত মেসেজ সার্ভারে পাঠানো হয় না।", 15f))
        body.addView(text(if (hasAccess()) "✓ Notification Access চালু" else "⚠ Notification Access বন্ধ", 18f))
        button("Notification Access সেটিংস খুলুন") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "সেটিংস খোলা যায়নি", Toast.LENGTH_SHORT).show() }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            button("অ্যাপ নোটিফিকেশনের অনুমতি দিন") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10) }
        }
        body.addView(text("কোন অ্যাপের মেসেজ পড়বে?", 19f))
        toggle("WhatsApp", "allow_com.whatsapp")
        toggle("Messenger", "allow_com.facebook.orca")
        body.addView(text("Reply Mode", 19f))
        toggle("Automatic Reply (ঐচ্ছিক)", "auto")
        body.addView(text("Manual Reply ডিফল্ট। অটো মোড বন্ধ থাকলে আপনার অনুমতি ছাড়া কোনো রিপ্লাই পাঠানো হয় না।", 14f))
        button("ইনবক্স রিফ্রেশ") { render() }
        body.addView(text("সাম্প্রতিক নোটিফিকেশন", 21f))
        val entries = ReplyStore.entries(this)
        if (entries.isEmpty()) body.addView(text("এখনও কোনো মেসেজ নেই। Notification Access এবং অ্যাপ নির্বাচন চালু করে নতুন মেসেজের অপেক্ষা করুন।", 15f))
        entries.take(30).forEach { item ->
            body.addView(text("${item.app} • ${item.sender}", 18f))
            body.addView(text(item.message))
            val suggested = ReplyStore.suggestion(item.message)
            body.addView(text("সাজেস্টেড: $suggested", 15f))
            button("দেখে অনুমোদন দিয়ে রিপ্লাই") {
                val input = EditText(this).apply { setText(suggested); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
                AlertDialog.Builder(this).setTitle("উত্তর যাচাই করুন").setView(input)
                    .setNegativeButton("বাতিল", null)
                    .setPositiveButton("পাঠান") { _, _ ->
                        val reply = input.text.toString().trim()
                        if (reply.isBlank()) return@setPositiveButton
                        val ok = ReplyListener.send(this, item.key, reply)
                        Toast.makeText(this, if (ok) "রিপ্লাই পাঠানোর অনুরোধ দেওয়া হয়েছে" else "রিপ্লাই সম্ভব নয়। নোটিফিকেশনটি সক্রিয় ও Reply সমর্থিত হতে হবে।", Toast.LENGTH_LONG).show()
                    }.show()
            }
            body.addView(View(this).apply { setBackgroundColor(Color.LTGRAY); layoutParams = LinearLayout.LayoutParams(-1, 1) })
        }
        body.addView(text("গোপনীয়তা: এই ডেমো সর্বশেষ ৫০টি নোটিফিকেশনের লেখা শুধু আপনার ফোনে রাখে। Notification Access বন্ধ করলে নতুন মেসেজ পড়া বন্ধ হবে। Android Settings থেকে অনুমতি প্রত্যাহার করা যায়।", 13f))
    }
}
