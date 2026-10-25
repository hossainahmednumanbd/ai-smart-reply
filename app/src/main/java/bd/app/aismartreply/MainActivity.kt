package bd.app.aismartreply

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val blue = Color.rgb(10, 106, 248)
    private val dark = Color.rgb(18, 32, 62)
    private val muted = Color.rgb(105, 116, 136)
    private val bg = Color.rgb(246, 249, 255)
    private var page = "home"
    private lateinit var column: LinearLayout
    private var selected: Incoming? = null
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun label(value: String, size: Float = 16f, color: Int = dark, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun space(n: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun card(): LinearLayout = vertical().apply {
        background = shape(Color.WHITE, 18, Color.rgb(233, 237, 246))
        setPadding(dp(16), dp(16), dp(16), dp(16)); elevation = dp(2).toFloat()
    }
    private fun addCard(c: LinearLayout) {
        column.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun action(text: String, onClick: () -> Unit, filled: Boolean = true): TextView =
        label(text, 15f, if (filled) Color.WHITE else blue, true).apply {
            gravity = Gravity.CENTER; background = shape(if (filled) blue else Color.rgb(231, 241, 255), 13)
            setPadding(dp(15), dp(13), dp(15), dp(13)); setOnClickListener { onClick() }
        }
    private fun hasAccess(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= 27)
            nm.isNotificationListenerAccessGranted(ComponentName(this, ReplyListener::class.java))
        else Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val root = vertical().apply {
            setBackgroundColor(bg)
            if (Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { view, insets ->
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    insets
                }
            } else {
                fitsSystemWindows = true
            }
        }
        val scroll = ScrollView(this).apply { isFillViewport = false }
        column = vertical().apply { setPadding(dp(16), dp(18), dp(16), dp(20)) }
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = horizontal().apply {
            setPadding(dp(5), dp(8), dp(5), dp(8)); setBackgroundColor(Color.WHITE)
        }
        listOf(Triple("⌂", "হোম", "home"), Triple("▤", "চ্যাট", "chats"),
            Triple("▣", "টেমপ্লেট", "templates"), Triple("⚙", "সেটিংস", "settings")).forEach { (icon, title, id) ->
            val item = vertical().apply { gravity = Gravity.CENTER; setPadding(dp(2), dp(2), dp(2), dp(2)) }
            item.addView(label(icon, 24f, if (page == id) blue else muted).apply { gravity = Gravity.CENTER })
            item.addView(label(title, 12f, if (page == id) blue else muted, page == id).apply { gravity = Gravity.CENTER })
            item.setOnClickListener { page = id; render() }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        root.addView(nav)
        setContentView(root)
        when (page) {
            "home" -> home()
            "chats" -> chats()
            "templates" -> templates()
            "settings" -> settingsPage()
        }
    }
    private fun heading(title: String, subtitle: String = "") {
        column.addView(label(title, 25f, dark, true))
        if (subtitle.isNotEmpty()) {
            column.addView(space(4)); column.addView(label(subtitle, 13f, muted))
        }
        column.addView(space(17))
    }
    private fun home() {
        heading("🤖 AI Smart Reply", "স্মার্ট উত্তর, আপনার নিয়ন্ত্রণে")
        val banner = vertical().apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(blue, Color.rgb(33, 175, 249))).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(20), dp(23), dp(20), dp(23))
        }
        banner.addView(label("স্বাগতম!  🤖", 25f, Color.WHITE, true))
        banner.addView(space(8))
        banner.addView(label("AI দিয়ে দ্রুত ও স্মার্টভাবে উত্তর দিন", 15f, Color.WHITE))
        column.addView(banner); column.addView(space(17))
        val apps = horizontal()
        listOf(Triple("f", "Facebook", false), Triple("◉", "WhatsApp", ReplyStore.enabled(this, "allow_com.whatsapp")),
            Triple("ϟ", "Messenger", ReplyStore.enabled(this, "allow_com.facebook.orca"))).forEachIndexed { index, (symbol, name, active) ->
            val tile = card().apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(16), dp(4), dp(16)) }
            tile.addView(label(symbol, 34f, if (index == 1) Color.rgb(0, 195, 89) else blue, true).apply { gravity = Gravity.CENTER })
            tile.addView(space(8))
            tile.addView(label(name, 13f, dark, true).apply { gravity = Gravity.CENTER })
            tile.addView(label(if (active) "● চালু" else "○ বন্ধ", 12f, if (active) Color.rgb(0, 160, 70) else muted).apply { gravity = Gravity.CENTER })
            tile.setOnClickListener { page = "settings"; render() }
            apps.addView(tile, LinearLayout.LayoutParams(0, dp(132), 1f).apply { marginEnd = dp(if (index == 2) 0 else 7) })
        }
        column.addView(apps); column.addView(space(10))
        val business = card()
        val businessLine = horizontal()
        businessLine.addView(label("🟢  WhatsApp Business", 16f, dark, true), LinearLayout.LayoutParams(0, -2, 1f))
        businessLine.addView(label(if (ReplyStore.enabled(this, "allow_com.whatsapp.w4b")) "● চালু" else "○ বন্ধ", 13f,
            if (ReplyStore.enabled(this, "allow_com.whatsapp.w4b")) Color.rgb(0,160,70) else muted))
        business.addView(businessLine)
        business.setOnClickListener { page = "settings"; render() }
        addCard(business)
        column.addView(space(5))
        menuCard("▣", "ম্যানুয়াল রিপ্লাই", "সাজেস্টেড উত্তর দেখে অনুমোদন দিন") { page = "chats"; render() }
        menuCard("ϟ", "অটো রিপ্লাই", "আপনার সেটিংস অনুযায়ী উত্তর পাঠান") { page = "settings"; render() }
        menuCard("⚙", "সেটিংস", "ডিভাইস, অ্যাপ ও রিপ্লাই নিয়ন্ত্রণ") { page = "settings"; render() }
        val count = ReplyStore.entries(this).size
        val stats = horizontal()
        listOf("$count\nমোট মেসেজ", "০\nযাচাইকৃত রিপ্লাই", "$count\nইনবক্স").forEach {
            val tile = card().apply { gravity = Gravity.CENTER }
            tile.addView(label(it, 15f, dark, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER })
            stats.addView(tile, LinearLayout.LayoutParams(0, dp(82), 1f).apply { marginEnd = dp(6) })
        }
        column.addView(stats)
        column.addView(space(10))
        column.addView(label("নোট: Facebook ব্যক্তিগত ইনবক্সের সরাসরি সংযোগ নেই। WhatsApp ও Messenger-এর অনুমোদিত Android নোটিফিকেশন ব্যবহার করা হয়।", 12f, muted))
    }
    private fun menuCard(icon: String, title: String, subtitle: String, click: () -> Unit) {
        val tile = card()
        val line = horizontal()
        val symbol = label(icon, 25f, blue, true).apply {
            gravity = Gravity.CENTER; background = shape(Color.rgb(231, 241, 255), 12)
        }
        line.addView(symbol, LinearLayout.LayoutParams(dp(52), dp(52)))
        val info = vertical().apply { setPadding(dp(12), 0, 0, 0) }
        info.addView(label(title, 18f, dark, true)); info.addView(space(4)); info.addView(label(subtitle, 13f, muted))
        line.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(label("›", 28f, muted))
        tile.addView(line); tile.setOnClickListener { click() }; addCard(tile)
    }
    private fun chats() {
        heading("চ্যাট ইনবক্স", "নতুন নোটিফিকেশন ও সাজেস্টেড রিপ্লাই")
        val items = ReplyStore.entries(this)
        if (items.isEmpty()) {
            val tile = card()
            tile.addView(label("💬  এখনো কোনো নতুন মেসেজ নেই", 18f, dark, true))
            tile.addView(space(10))
            tile.addView(label("Notification Access চালু করে WhatsApp বা Messenger-এ নতুন মেসেজ পেলে এখানে দেখা যাবে।", 14f, muted))
            tile.addView(space(14))
            tile.addView(action("সেটিংস খুলুন", { page = "settings"; render() }))
            addCard(tile)
        }
        items.take(30).forEach { item ->
            val tile = card()
            tile.addView(label("${if (item.app == "WhatsApp") "🟢" else "🔵"}  ${item.sender}", 18f, dark, true))
            tile.addView(label(item.app, 12f, muted))
            tile.addView(space(12))
            val bubble = label(item.message, 16f).apply {
                background = shape(Color.rgb(240, 243, 248), 15)
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            tile.addView(bubble)
            tile.addView(space(15))
            tile.addView(label("✦ সাজেস্টেড রিপ্লাই", 15f, blue, true))
            tile.addView(space(7))
            tile.addView(label(ReplyStore.suggestion(item.message), 15f))
            tile.addView(space(8))
            tile.addView(action("✦ AI দিয়ে উত্তর তৈরি করুন", { generateAi(item) }, false))
            tile.addView(space(12))
            tile.addView(action("উত্তর দেখুন ও পাঠান", { openReply(item) }))
            addCard(tile)
        }
        column.addView(action("⟳  ইনবক্স রিফ্রেশ", { render() }, false))
    }
    private fun generateAi(item: Incoming) {
        if (!AiEngine.configured(this)) {
            AlertDialog.Builder(this).setTitle("AI সার্ভার সংযুক্ত নয়")
                .setMessage("AI উত্তর তৈরি করতে আগে সেটিংস থেকে আপনার নিরাপদ HTTPS AI সার্ভার সংযুক্ত করতে হবে। কোনো মেসেজ এখনো AI-তে পাঠানো হয়নি।")
                .setPositiveButton("সেটিংস") { _, _ -> page = "settings"; render() }
                .setNegativeButton("বাতিল", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("AI-তে মেসেজ পাঠাবেন?")
            .setMessage("এই মেসেজের লেখা আপনার কনফিগার করা AI সার্ভারে পাঠানো হবে। ব্যক্তিগত বা সংবেদনশীল তথ্য থাকলে পাঠাবেন না।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("AI উত্তর তৈরি") { _, _ ->
                Toast.makeText(this, "AI উত্তর তৈরি হচ্ছে...", Toast.LENGTH_SHORT).show()
                AiEngine.generate(this, item.message) { result ->
                    result.onSuccess { reply -> openReply(item, reply) }
                        .onFailure { error -> AlertDialog.Builder(this).setTitle("AI উত্তর পাওয়া যায়নি")
                            .setMessage(error.message ?: "সার্ভার সংযোগ পরীক্ষা করুন").setPositiveButton("ঠিক আছে", null).show() }
                }
            }.show()
    }
    private fun openReply(item: Incoming, initialReply: String = ReplyStore.suggestion(item.message)) {
        val edit = EditText(this).apply {
            setText(initialReply)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        AlertDialog.Builder(this).setTitle("${item.sender} • রিপ্লাই যাচাই")
            .setMessage("নিচের উত্তরটি যাচাই করুন। পাঠালে সংশ্লিষ্ট অ্যাপের সক্রিয় Reply নোটিফিকেশন ব্যবহার করা হবে।")
            .setView(edit).setNegativeButton("বাতিল", null)
            .setPositiveButton("পাঠান") { _, _ ->
                val response = edit.text.toString().trim()
                if (response.isNotEmpty()) {
                    val ok = ReplyListener.send(this, item.key, response)
                    Toast.makeText(this, if (ok) "রিপ্লাই পাঠানোর অনুরোধ দেওয়া হয়েছে" else "সক্রিয় Reply নোটিফিকেশন পাওয়া যায়নি", Toast.LENGTH_LONG).show()
                }
            }.show()
    }
    private fun templates() {
        heading("রিপ্লাই টেমপ্লেট", "এই সংস্করণে স্থানীয় নমুনা উত্তর")
        listOf("আসসালামু আলাইকুম! কীভাবে সাহায্য করতে পারি?",
            "আপনার মেসেজ পেয়েছি। একটু পরে বিস্তারিত জানাচ্ছি।",
            "আলহামদুলিল্লাহ, ভালো আছি। আপনি কেমন আছেন?",
            "আপনাকেও ধন্যবাদ!").forEach {
            val tile = card()
            tile.addView(label(it, 16f))
            tile.addView(space(10))
            tile.addView(action("কপি করুন", {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Reply", it))
                Toast.makeText(this, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
            }, false))
            addCard(tile)
        }
    }
    private fun toggle(tile: LinearLayout, title: String, description: String, key: String) {
        val row = horizontal()
        val info = vertical()
        info.addView(label(title, 16f, dark, true))
        info.addView(label(description, 12f, muted))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply { isChecked = ReplyStore.enabled(this@MainActivity, key) }
        row.addView(toggle)
        tile.addView(row); tile.addView(space(12))
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (key == "auto" && enabled) {
                toggle.isChecked = false
                AlertDialog.Builder(this).setTitle("অটো রিপ্লাই চালু করবেন?")
                    .setMessage("সমর্থিত নতুন নোটিফিকেশনে আপনার অনুমোদন ছাড়াই টেমপ্লেট উত্তর পাঠানো হতে পারে। এটি AI নয়। ভুল উত্তর যাওয়ার ঝুঁকি আছে।")
                    .setNegativeButton("বাতিল", null)
                    .setPositiveButton("চালু করুন") { _, _ -> ReplyStore.set(this, key, true); toggle.isChecked = true }
                    .show()
            } else ReplyStore.set(this, key, enabled)
        }
    }
    private fun settingsPage() {
        heading("অটো রিপ্লাই সেটিংস", "আপনার অনুমতি ও রিপ্লাই নিয়ন্ত্রণ")
        val permissions = card()
        permissions.addView(label(if (hasAccess()) "✓ Notification Access চালু" else "⚠ Notification Access বন্ধ", 17f, if (hasAccess()) Color.rgb(0, 148, 74) else dark, true))
        permissions.addView(space(10))
        permissions.addView(action("Notification Access সেটিংস", {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, false))
        addCard(permissions)
        val platforms = card()
        platforms.addView(label("অ্যাপ নির্বাচন", 19f, dark, true))
        platforms.addView(space(12))
        platforms.addView(label("Facebook ব্যক্তিগত অ্যাকাউন্টের সরাসরি API সংযোগ সমর্থিত নয়।", 13f, muted))
        platforms.addView(space(12))
        toggle(platforms, "🟢 WhatsApp Personal", "ব্যক্তিগত WhatsApp নোটিফিকেশন", "allow_com.whatsapp")
        toggle(platforms, "🟢 WhatsApp Business", "ব্যবসার গ্রাহকের নোটিফিকেশন", "allow_com.whatsapp.w4b")
        toggle(platforms, "🔵 Messenger", "নোটিফিকেশন থেকে মেসেজ শনাক্ত", "allow_com.facebook.orca")
        addCard(platforms)
        val modes = card()
        modes.addView(label("রিপ্লাই মোড", 19f, dark, true))
        modes.addView(space(12))
        modes.addView(label("◉  ম্যানুয়াল রিপ্লাই", 16f, dark, true))
        modes.addView(label("উত্তর দেখে অনুমোদন দিলে পাঠানোর চেষ্টা করবে", 13f, muted))
        modes.addView(space(15))
        toggle(modes, "অটোমেটিক রিপ্লাই", "সমর্থিত নোটিফিকেশনে টেমপ্লেট উত্তর", "auto")
        addCard(modes)
        val ai = card()
        ai.addView(label("✦ AI Engine সংযোগ", 19f, dark, true))
        ai.addView(space(8))
        ai.addView(label(if (AiEngine.configured(this)) "HTTPS AI সার্ভার কনফিগার করা আছে" else "AI সার্ভার এখনো সংযুক্ত নয়", 14f, muted))
        ai.addView(space(10))
        ai.addView(action("AI সার্ভার কনফিগার করুন", {
            val prefs = getSharedPreferences("ai_config", MODE_PRIVATE)
            val layout = vertical().apply { setPadding(dp(20), dp(10), dp(20), 0) }
            val url = EditText(this).apply {
                hint = "https://your-domain.example/reply"
                setSingleLine(true)
                setText(prefs.getString("url", ""))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            val token = EditText(this).apply {
                hint = "Backend access token"
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            layout.addView(url); layout.addView(token)
            AlertDialog.Builder(this).setTitle("নিজস্ব HTTPS AI সার্ভার").setView(layout)
                .setMessage("এখানে OpenAI API Key দেবেন না। শুধু আপনার নিজস্ব backend URL ও access token ব্যবহার করুন।")
                .setNegativeButton("বাতিল", null)
                .setPositiveButton("সংরক্ষণ") { _, _ ->
                    if (AiEngine.save(this, url.text.toString(), token.text.toString())) render()
                    else Toast.makeText(this, "সঠিক HTTPS URL দিন", Toast.LENGTH_LONG).show()
                }.show()
        }, false))
        addCard(ai)
        val notice = card()
        notice.addView(label("গোপনীয়তা ও সীমাবদ্ধতা", 17f, dark, true))
        notice.addView(space(8))
        notice.addView(label("সর্বশেষ ৫০টি শনাক্ত করা নোটিফিকেশনের লেখা ফোনে রাখা হয়। AI ব্যবহারের জন্য আলাদা HTTPS সার্ভার প্রয়োজন। আপনার অনুমতি ছাড়া মেসেজ AI-তে পাঠানো হয় না। সব অ্যাপে রিপ্লাই কাজ নাও করতে পারে।", 14f, muted))
        addCard(notice)
        column.addView(action("✓ সেটিংস সংরক্ষিত হয় স্বয়ংক্রিয়ভাবে", { Toast.makeText(this, "সেটিংস সংরক্ষিত আছে", Toast.LENGTH_SHORT).show() }))
    }
}
