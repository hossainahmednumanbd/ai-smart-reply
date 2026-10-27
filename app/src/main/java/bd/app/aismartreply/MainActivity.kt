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
import android.Manifest
import android.text.InputFilter
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
    private var connectionLabel: TextView? = null
    private var connectionDetails: TextView? = null
    private val statusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val statusRefresh = object : Runnable {
        override fun run() {
            connectionLabel?.text = if (ReplyListener.connected()) "✓ মেসেজ সংযোগ সক্রিয়" else "মেসেজ সংযোগ বিচ্ছিন্ন; পুনঃসংযোগের চেষ্টা চলছে"
            connectionDetails?.text = ReplyListener.connectionDetail(this@MainActivity)
            statusHandler.postDelayed(this, 1500L)
        }
    }
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); AutoReplyGuard.installSafetyUpdate(this); render() }
    override fun onResume() {
        super.onResume()
        if (hasAccess()) ReplyListener.reconnect(this)
        AutoReplyService.sync(this)
        render()
        statusHandler.removeCallbacks(statusRefresh); statusHandler.post(statusRefresh)
    }
    override fun onPause() { statusHandler.removeCallbacks(statusRefresh); super.onPause() }
    @Deprecated("Google Sign-In activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GoogleAuth.REQUEST_CODE) {
            GoogleAuth.handleResult(data).onSuccess {
                ReplyStore.set(this, "ai_auto", false)
                AutoReplyService.sync(this)
                Toast.makeText(this, "Google লগইন সফল", Toast.LENGTH_LONG).show()
            }.onFailure {
                AlertDialog.Builder(this).setTitle("Google লগইন ব্যর্থ")
                    .setMessage("Android OAuth SHA-1 এবং টেস্ট ইউজার যাচাই করুন।\\n" + (it.message ?: ""))
                    .setPositiveButton("ঠিক আছে", null).show()
            }
            render()
        }
    }
    private fun render() {
        connectionLabel = null; connectionDetails = null;
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
            "conversation" -> conversation()
            "templates" -> templates()
            "settings" -> settingsPage()
            "training" -> trainingPage()
            "facebook" -> facebookPage()
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
        val brand = ImageView(this).apply { setImageResource(R.drawable.app_logo); contentDescription = "AI Smart Reply লোগো" }
        column.addView(brand, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(10) })
        heading("AI Smart Reply", "১৮+ ব্যবহারকারীর ব্যবসায়িক রিপ্লাই সহকারী")
        column.addView(action("🎙 Voice Writer (পরীক্ষামূলক)", { startActivity(Intent(this, VoiceWriterActivity::class.java)) }, false))
        column.addView(action("🎙 Floating Voice Button চালু করুন", {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this).setTitle("ভাসমান বাটনের অনুমতি")
                    .setMessage("অন্য অ্যাপের ওপর ছোট Voice Writer বাটন দেখাতে অনুমতি দিন। মাইক্রোফোন শুধু আপনার চাপ দেওয়ার পর চালু হবে।")
                    .setPositiveButton("অনুমতি দিন") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")))
                    }.setNegativeButton("বাতিল", null).show()
            } else {
                val service = Intent(this, VoiceOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                Toast.makeText(this, "ভাসমান বাটন চালু হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }, false))
        column.addView(action("Floating Voice Button বন্ধ করুন", {
            stopService(Intent(this, VoiceOverlayService::class.java))
            Toast.makeText(this, "ভাসমান বাটন বন্ধ হয়েছে", Toast.LENGTH_SHORT).show()
        }, false))
        val signedIn = GoogleAuth.account(this)
        val accountCard = card()
        accountCard.addView(label(if (signedIn == null) "Google অ্যাকাউন্ট সংযুক্ত নয়" else "✓ Google: ${signedIn.email ?: "লগইন করা আছে"}", 15f, dark, true))
        accountCard.addView(space(10))
        accountCard.addView(action(if (signedIn == null) "Google দিয়ে লগইন করুন" else "অ্যাকাউন্ট পরিবর্তন করুন", { GoogleAuth.signIn(this) }, false))
        addCard(accountCard)
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
            tile.setOnClickListener { page = if (index == 0) "facebook" else "settings"; render() }
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
        menuCard("✦", "AI নির্দেশনা ও ব্যবসার তথ্য", "নিজের ভাষা, তথ্য ও নমুনা উত্তর সেট করুন") { page = "training"; render() }
        menuCard("▣", "নিজের রিপ্লাই নিয়ম", "কোন মেসেজে কোন উত্তর যাবে ঠিক করুন") { page = "templates"; render() }
        menuCard("⚙", "সেটিংস", "ডিভাইস, অ্যাপ ও রিপ্লাই নিয়ন্ত্রণ") { page = "settings"; render() }
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
        heading("চ্যাট ইনবক্স", "ব্যক্তি অনুযায়ী কথোপকথন")
        val entries = ReplyStore.entries(this)
        val groups = entries.groupBy { it.app to it.sender }
        if (groups.isEmpty()) {
            val tile = card()
            tile.addView(label("এখনো কোনো নতুন মেসেজ নেই", 18f, dark, true))
            tile.addView(action("সেটিংস খুলুন", { page = "settings"; render() }))
            addCard(tile)
        }
        groups.values.sortedByDescending { it.first().time }.forEach { messages ->
            val item = messages.first()
            val tile = card()
            tile.addView(label(item.sender, 18f, dark, true))
            tile.addView(label(item.app, 12f, muted))
            tile.addView(space(8))
            tile.addView(label(item.message.take(90), 14f, muted))
            tile.setOnClickListener { selected = item; page = "conversation"; render() }
            addCard(tile)
        }
        column.addView(action("ইনবক্স রিফ্রেশ", { render() }, false))
    }
    private fun conversation() {
        val person = selected ?: return
        column.addView(action("‹ সব চ্যাট", { page = "chats"; render() }, false))
        column.addView(space(12))
        heading(person.sender, person.app)
        ReplyStore.entries(this).filter { it.app == person.app && it.sender == person.sender }
            .sortedBy { it.time }.forEach { item ->
                val tile = card()
                tile.addView(label(item.message, 16f))
                tile.addView(space(12))
                tile.addView(label("সাজেস্টেড রিপ্লাই", 14f, blue, true))
                tile.addView(label(ReplyPreferences.suggestion(this, item.message).ifEmpty { "কোনো নিয়ম মেলেনি; সাধারণ টেমপ্লেট বন্ধ আছে। নিজের উত্তর লিখুন অথবা AI উত্তর তৈরি করুন।" }, 15f))
                tile.addView(space(10))
                tile.addView(action("AI দিয়ে উত্তর তৈরি করুন", { generateAi(item) }, false))
                tile.addView(space(8))
                tile.addView(action("উত্তর দেখুন ও পাঠান", { openReply(item) }))
                addCard(tile)
            }
        column.addView(label("শুধু নোটিফিকেশনে পাওয়া মেসেজ দেখানো হচ্ছে। একই নামের ভিন্ন ব্যক্তি মিশে যেতে পারে।", 12f, muted))
    }
    private fun generateAi(item: Incoming) {
        if (!AiEngine.configured(this)) {
            AlertDialog.Builder(this).setTitle("Google Login প্রয়োজন")
                .setMessage("AI উত্তর তৈরি করতে প্রথমে Google দিয়ে লগইন করুন। কোনো মেসেজ এখনো AI-তে পাঠানো হয়নি।")
                .setPositiveButton("সেটিংস") { _, _ -> page = "settings"; render() }
                .setNegativeButton("বাতিল", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("AI-তে মেসেজ পাঠাবেন?")
            .setMessage("এই AI সেবা শুধু ১৮+ ব্যবসায়িক ব্যবহারের জন্য। সম্মতি দিলে আপনি ১৮+ নিশ্চিত করছেন। এই মেসেজের লেখা ও আপনার সংরক্ষিত AI নির্দেশনা Cloudflare ও Google Gemini-তে পাঠানো হবে। ব্যক্তিগত বা সংবেদনশীল তথ্য থাকলে পাঠাবেন না।")
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
    private fun openReply(item: Incoming, initialReply: String = ReplyPreferences.suggestion(this, item.message)) {
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
    private fun input(value: String, hintText: String, limit: Int) = EditText(this).apply {
        setText(value); hint = hintText; textSize = 15f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        filters = arrayOf(InputFilter.LengthFilter(limit))
        minLines = 2; setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private fun templates() {
        heading("নিজের রিপ্লাই নিয়ম", "হুবহু ও বেশি প্রয়োজনীয় শব্দ মেলা নিয়ম আগে; সমান হলে ওপরের নিয়ম আগে")
        column.addView(action("+ নতুন নিয়ম যোগ করুন", { editRule(null) }))
        column.addView(space(12))
        val rules = ReplyPreferences.rules(this)
        rules.forEachIndexed { index, rule ->
            val tile = card()
            val enabled = Switch(this).apply { text = when { rule.exact -> "পুরো মেসেজ মিলবে"; rule.allWords -> "সব প্রয়োজনীয় শব্দ মিলবে"; else -> "শব্দ/বাক্যাংশ থাকবে" }; isChecked = rule.enabled }
            enabled.setOnCheckedChangeListener { _, checked ->
                ReplyPreferences.saveRules(this, ReplyPreferences.rules(this).map { if (it.id == rule.id) it.copy(enabled = checked) else it })
            }
            tile.addView(enabled)
            tile.addView(label("মেসেজ: ${rule.keywords}", 14f, blue, true))
            tile.addView(space(6)); tile.addView(label(rule.reply, 16f)); tile.addView(space(8))
            tile.addView(action("পরিবর্তন করুন", { editRule(rule) }, false))
            if (index > 0) {
                tile.addView(space(6)); tile.addView(action("অগ্রাধিকার বাড়ান ↑", {
                    val list = ReplyPreferences.rules(this).toMutableList()
                    val i = list.indexOfFirst { it.id == rule.id }
                    if (i > 0) { val item = list.removeAt(i); list.add(i - 1, item); ReplyPreferences.saveRules(this, list) }
                    render()
                }, false))
            }
            tile.addView(space(6)); tile.addView(action("মুছুন", {
                AlertDialog.Builder(this).setTitle("নিয়ম মুছবেন?").setMessage(rule.keywords)
                    .setNegativeButton("বাতিল", null).setPositiveButton("মুছুন") { _, _ ->
                        ReplyPreferences.saveRules(this, ReplyPreferences.rules(this).filterNot { it.id == rule.id }); render()
                    }.show()
            }, false))
            addCard(tile)
        }
        val fallback = input(ReplyPreferences.fallback(this), "কোনো নিয়ম না মিললে টেমপ্লেট উত্তর", 700)
        val tile = card()
        tile.addView(label("সাধারণ টেমপ্লেট উত্তর", 18f, dark, true))
        val fallbackSwitch = Switch(this).apply {
            text = "সাধারণ টেমপ্লেট উত্তর চালু"; textSize = 15f
            isChecked = ReplyPreferences.fallbackEnabled(this@MainActivity)
        }
        fallbackSwitch.setOnCheckedChangeListener { _, enabled ->
            ReplyPreferences.setFallbackEnabled(this, enabled)
        }
        tile.addView(fallbackSwitch)
        tile.addView(label("বন্ধ থাকলে কোনো নিয়ম না মেলা মেসেজে সাধারণ টেমপ্লেট যাবে না। চালু থাকলে শুধু টেমপ্লেট অটো মোডে যাবে। AI মোডে নিয়ম না মিললে AI উত্তর তৈরি করবে। সুইচের পরিবর্তন সঙ্গে সঙ্গে সংরক্ষিত হয়।", 13f, muted))
        tile.addView(fallback)
        tile.addView(action("সাধারণ উত্তর সংরক্ষণ", {
            if (fallback.text.toString().isBlank()) fallback.error = "উত্তর লিখুন"
            else { ReplyPreferences.saveText(this, "fallback", fallback.text.toString().trim()); Toast.makeText(this, "সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show() }
        }, false)); addCard(tile)
        val test = input("", "পরীক্ষার মেসেজ লিখুন", 2000)
        val preview = card(); preview.addView(label("নিয়ম পরীক্ষা", 18f, dark, true)); preview.addView(test)
        preview.addView(action("কোন উত্তর যাবে দেখুন", {
            val fixed = ReplyPreferences.matched(this, test.text.toString())
            AlertDialog.Builder(this).setTitle("পরীক্ষার ফল")
                .setMessage(fixed ?: if (ReplyPreferences.fallbackEnabled(this))
                    "কোনো নিয়ম মেলেনি। টেমপ্লেট অটো মোডে যাবে:\n${ReplyPreferences.fallback(this)}\n\nAI মোডে AI উত্তর তৈরি করবে।"
                    else "কোনো নিয়ম মেলেনি। সাধারণ টেমপ্লেট বন্ধ, তাই টেমপ্লেট অটো মোডে উত্তর যাবে না। AI মোডে AI উত্তর তৈরি করবে।")
                .setPositiveButton("ঠিক আছে", null).show()
        }, false)); addCard(preview)
    }
    private fun editRule(existing: ReplyRule?) {
        if (existing == null && ReplyPreferences.rules(this).size >= 30) {
            Toast.makeText(this, "সর্বোচ্চ ৩০টি নিয়ম রাখা যাবে", Toast.LENGTH_LONG).show(); return
        }
        val box = vertical().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        box.addView(label("একাধিক বিকল্প | দিয়ে আলাদা করুন। যেমন: দাম|price|মূল্য", 13f, muted))
        val keywords = input(existing?.keywords.orEmpty(), "কোন শব্দ বা মেসেজ এলে", 500)
        val reply = input(existing?.reply.orEmpty(), "আপনার নিজের উত্তর", 700)
        val matchMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("শব্দ/বাক্যাংশ থাকবে", "পুরো মেসেজ হুবহু মিলবে", "সব প্রয়োজনীয় শব্দ মিলবে"))
            setSelection(when { existing?.exact == true -> 1; existing?.allWords == true -> 2; else -> 0 })
        }
        box.addView(keywords); box.addView(reply); box.addView(matchMode)
        box.addView(label("সব প্রয়োজনীয় শব্দ মোডে: বিকাশ/bkash & এজেন্ট/agent & নাম্বার/নম্বর/number\n& দিয়ে প্রয়োজনীয় শব্দের দল, / দিয়ে একই শব্দের বিকল্প লিখুন। সব দল মেসেজে থাকতে হবে; মাঝে অন্য লেখা থাকতে পারে। | দিয়ে আলাদা সম্পূর্ণ নিয়মের বিকল্প। বানান বা অর্থ নিজে থেকে অনুমান করবে না।", 13f, muted))
        val scroll = ScrollView(this).apply { addView(box) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "নতুন নিয়ম" else "নিয়ম পরিবর্তন")
            .setView(scroll).setNegativeButton("বাতিল", null).setPositiveButton("সংরক্ষণ", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val triggers = keywords.text.toString().split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("|")
            val response = reply.text.toString().trim()
            if (triggers.isEmpty()) { keywords.error = "অন্তত একটি শব্দ বা মেসেজ লিখুন"; return@setOnClickListener }
            if (response.isEmpty()) { reply.error = "উত্তর লিখুন"; return@setOnClickListener }
            if (matchMode.selectedItemPosition == 2 && !ReplyRules.validAllWords(triggers)) {
                keywords.error = "অন্তত দুটি শব্দের দল & দিয়ে লিখুন; কোনো দল খালি রাখবেন না"; return@setOnClickListener
            }
            val rule = ReplyRule(existing?.id ?: java.util.UUID.randomUUID().toString(), triggers, response, matchMode.selectedItemPosition == 1, existing?.enabled ?: true, matchMode.selectedItemPosition == 2)
            val list = ReplyPreferences.rules(this).toMutableList()
            val i = list.indexOfFirst { it.id == rule.id }; if (i < 0) list.add(0, rule) else list[i] = rule
            ReplyPreferences.saveRules(this, list); dialog.dismiss(); render()
        } }
        dialog.show()
    }
    private fun trainingPage() {
        heading("AI-কে নিজের মতো নির্দেশ দিন", "এগুলো উত্তর তৈরির নির্দেশনা; Gemini মডেল স্থায়ীভাবে ট্রেন হবে না")
        val instructions = input(ReplyPreferences.text(this, "instructions"), "ভাষা ও আচরণ: যেমন ভদ্র বাংলায় সংক্ষিপ্ত উত্তর দিন, আপনি সম্বোধন করুন", 2000)
        val facts = input(ReplyPreferences.text(this, "facts"), "যাচাই করা ব্যবসার তথ্য: নাম, ঠিকানা, সময়, পণ্য, মূল্য, নিয়ম। যা জানা নেই তা লিখবেন না।", 3000)
        val examples = input(ReplyPreferences.text(this, "examples"), "নমুনা: প্রশ্ন: দোকান কোথায়? উত্তর: ...", 2000)
        listOf("ভাষা ও আচরণ" to instructions, "ব্যবসার তথ্য" to facts, "প্রশ্ন ও উত্তরের নমুনা" to examples).forEach { (title, field) ->
            val tile = card(); tile.addView(label(title, 18f, dark, true)); tile.addView(field); addCard(tile)
        }
        column.addView(label("এখানে পাসওয়ার্ড, OTP বা গোপন তথ্য রাখবেন না। AI অনুরোধের সঙ্গে এই তথ্য Cloudflare ও Google Gemini-তে যাবে। তথ্য না থাকলে AI-কে নিশ্চিত দাবি না করতে বলা হয়েছে; তবুও ভুল হতে পারে।", 13f, muted))
        column.addView(space(12))
        column.addView(action("AI নির্দেশনা সংরক্ষণ", {
            ReplyPreferences.saveText(this, "instructions", instructions.text.toString().trim())
            ReplyPreferences.saveText(this, "facts", facts.text.toString().trim())
            ReplyPreferences.saveText(this, "examples", examples.text.toString().trim())
            Toast.makeText(this, "সংরক্ষিত। পরের AI উত্তরে ব্যবহার হবে।", Toast.LENGTH_LONG).show()
        }))
    }
    private fun facebookPage() {
        heading("Facebook Page কমেন্ট", "Meta সংযোগ এখনো করা হয়নি")
        val tile = card()
        tile.addView(label("নিজের পরিচালিত Page-এর কমেন্ট", 18f, dark, true))
        tile.addView(space(8))
        tile.addView(label("Page-এর কমেন্টে AI উত্তর দিতে Meta App, Page অনুমতি, সার্ভারের webhook ও access token সংযোগ প্রয়োজন। অন্য ব্যবহারকারীর Page যুক্ত করতে Meta App Review লাগতে পারে। ব্যক্তিগত প্রোফাইলের কমেন্ট এখানে যুক্ত নেই।", 15f))
        tile.addView(space(10))
        tile.addView(label("এখন কোনো Facebook কমেন্ট পড়া বা স্বয়ংক্রিয়ভাবে পোস্ট করা হচ্ছে না।", 14f, muted))
        addCard(tile)
    }
    private fun reportReply() {
        val last = getSharedPreferences("ai_output", MODE_PRIVATE).getString("last_reply", "").orEmpty()
        if (last.isEmpty()) { Toast.makeText(this, "আগে একটি AI উত্তর তৈরি করুন", Toast.LENGTH_LONG).show(); return }
        val reason = input("", "কী সমস্যা হয়েছে লিখুন", 500)
        AlertDialog.Builder(this).setTitle("AI উত্তর রিপোর্ট করুন")
            .setMessage("সর্বশেষ AI উত্তর:\n$last\n\nআপনার নির্বাচিত এই উত্তর ও অভিযোগ Cloudflare সার্ভারে সর্বোচ্চ ৩১ দিন রাখা হবে। মূল মেসেজ পাঠানো হবে না।")
            .setView(reason).setNegativeButton("বাতিল", null)
            .setPositiveButton("রিপোর্ট পাঠান") { _, _ ->
                AiEngine.report(this, last, reason.text.toString().ifBlank { "inappropriate_or_incorrect" }) { result ->
                    AlertDialog.Builder(this).setTitle(if (result.isSuccess) "রিপোর্ট গ্রহণ করা হয়েছে" else "রিপোর্ট পাঠানো যায়নি")
                        .setMessage(if (result.isSuccess) "অভিযোগ পর্যালোচনার জন্য সংরক্ষিত হয়েছে।" else result.exceptionOrNull()?.message)
                        .setPositiveButton("ঠিক আছে", null).show()
                }
            }.show()
    }
    private fun eraseLocalData() {
        AiEngine.invalidatePending()
        ReplyStore.set(this, "ai_auto", false); ReplyStore.set(this, "auto", false); AutoReplyService.sync(this)
        listOf("reply_settings", "reply_customization", "auto_dedup_v2", "auto_reply_guard_v1", "ai_auto_state", "ai_output").forEach {
            getSharedPreferences(it, MODE_PRIVATE).edit().clear().apply()
        }
        GoogleAuth.client(this).revokeAccess().addOnCompleteListener {
            GoogleAuth.signOut(this) { page = "home"; render() }
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
            // Programmatic confirmation must not open the consent dialog again.
            if (enabled && ReplyStore.enabled(this, key)) return@setOnCheckedChangeListener
            if (key == "auto" && enabled) {
                toggle.isChecked = false
                AlertDialog.Builder(this).setTitle("অটো রিপ্লাই চালু করবেন?")
                    .setMessage("সমর্থিত নতুন নোটিফিকেশনে আপনার অনুমোদন ছাড়াই টেমপ্লেট উত্তর পাঠানো হতে পারে। এটি AI নয়। ভুল উত্তর যাওয়ার ঝুঁকি আছে।")
                    .setNegativeButton("বাতিল", null)
                    .setPositiveButton("চালু করুন") { _, _ ->
                        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8402)
                        ReplyStore.set(this, "ai_auto", false)
                        ReplyStore.set(this, key, true)
                        AutoReplyService.sync(this)
                        render()
                    }
                    .show()
            } else {
                ReplyStore.set(this, key, enabled)
                if (key == "auto") AutoReplyService.sync(this)
            }
        }
    }
    private fun settingsPage() {
        heading("অটো রিপ্লাই সেটিংস", "আপনার অনুমতি ও রিপ্লাই নিয়ন্ত্রণ")
        val permissions = card()
        permissions.addView(label(if (hasAccess()) "✓ Notification Access চালু" else "⚠ Notification Access বন্ধ", 17f, if (hasAccess()) Color.rgb(0, 148, 74) else dark, true))
        permissions.addView(space(10))
        permissions.addView(action("Notification Access সেটিংস", {
            AlertDialog.Builder(this).setTitle("মেসেজের নোটিফিকেশন পড়ার অনুমতি")
                .setMessage("AI Smart Reply নির্বাচিত WhatsApp ও Messenger-এর মেসেজের লেখা ও প্রেরকের নাম নোটিফিকেশন থেকে পড়বে, অ্যাপ বন্ধ থাকলেও। সর্বশেষ ৫০টি মেসেজ ফোনে থাকবে। টেমপ্লেট উত্তর ফোনেই মেলে। AI চালু করলে মেসেজ ও আপনার AI নির্দেশনা Cloudflare ও Google Gemini-তে পাঠানো হবে। অটো মোডে আপনার অনুমোদন ছাড়াই Reply action দিয়ে উত্তর যাবে। পূর্ণ চ্যাট বা সাধারণ SMS পড়া হবে না। যেকোনো সময় বন্ধ করতে পারবেন।")
                .setNegativeButton("এখন নয়", null).setPositiveButton("সম্মত, সেটিংস খুলুন") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }.show()
        }, false))
        permissions.addView(space(8))
        connectionLabel = label(if (ReplyListener.connected()) "✓ মেসেজ সংযোগ সক্রিয়" else "মেসেজ সংযোগ বিচ্ছিন্ন; পুনঃসংযোগের চেষ্টা চলছে", 14f)
        permissions.addView(connectionLabel)
        connectionDetails = label(ReplyListener.connectionDetail(this), 12f, muted)
        permissions.addView(connectionDetails)
        permissions.addView(action("মেসেজ সংযোগ আবার চেষ্টা করুন", {
            ReplyListener.reconnect(this)
            AutoReplyService.sync(this)
            Toast.makeText(this, "পুনঃসংযোগের অনুরোধ করা হয়েছে; নতুন মেসেজ দিয়ে পরীক্ষা করুন", Toast.LENGTH_LONG).show()
        }, false))
        permissions.addView(label("ফোনের Battery সেটিংসে এই অ্যাপের জন্য No restrictions/Unrestricted রাখুন। ফোনে Background autostart থাকলে অনুমতি দিন। Force stop করলে অ্যাপ আবার খুলতে হবে। পুরোনো মেসেজে হঠাৎ উত্তর যাবে না।", 13f, muted))
        permissions.addView(action("অ্যাপের ব্যাকগ্রাউন্ড সেটিংস", {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
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
        menuCard("✦", "AI নির্দেশনা ও ব্যবসার তথ্য", "নিজের মতো AI উত্তর সেট করুন") { page = "training"; render() }
        menuCard("▣", "নিজের রিপ্লাই নিয়ম", "মেসেজের সঙ্গে নিজের উত্তর মিলিয়ে দিন") { page = "templates"; render() }
        val modes = card()
        modes.addView(label("রিপ্লাই মোড", 19f, dark, true))
        modes.addView(space(12))
        modes.addView(label("◉  ম্যানুয়াল রিপ্লাই", 16f, dark, true))
        modes.addView(label("উত্তর দেখে অনুমোদন দিলে পাঠানোর চেষ্টা করবে", 13f, muted))
        modes.addView(space(15))
        toggle(modes, "অটোমেটিক রিপ্লাই", "সমর্থিত নোটিফিকেশনে টেমপ্লেট উত্তর", "auto")
        modes.addView(space(10))
        val aiAuto = Switch(this).apply {
            text = "AI অটো রিপ্লাই (নিজের AI Gateway)"
            textSize = 15f
            isChecked = ReplyStore.enabled(this@MainActivity, "ai_auto")
        }
        modes.addView(aiAuto)
        modes.addView(label("প্রতিদিন সর্বোচ্চ ২০টি AI অনুরোধ; প্রত্যেক চ্যাটে এক মিনিটে সর্বোচ্চ তিনটি উত্তর; আলাদা ব্যক্তির জন্য আলাদা উত্তর। সংবেদনশীল মেসেজ বাদ যাবে। AI ভুল করতে পারে।", 12f, muted))
        aiAuto.setOnCheckedChangeListener { _, checked ->
            if (checked && ReplyStore.enabled(this, "ai_auto")) return@setOnCheckedChangeListener
            if (checked) {
                aiAuto.isChecked = false
                if (!AiEngine.configured(this)) {
                    AlertDialog.Builder(this).setTitle("Google Login প্রয়োজন")
                        .setMessage("প্রথমে Google অ্যাকাউন্ট দিয়ে লগইন করুন।")
                        .setPositiveButton("ঠিক আছে", null).show()
                } else {
                    AlertDialog.Builder(this).setTitle("AI অটো রিপ্লাই চালু?")
                        .setMessage("এই AI সেবা শুধু ১৮+ ব্যবসায়িক ব্যবহারের জন্য। সম্মতি দিলে আপনি ১৮+ নিশ্চিত করছেন। নিয়ম না মেলা নতুন মেসেজের লেখা ও আপনার AI নির্দেশনা Cloudflare ও Google Gemini-তে পাঠানো হবে, অ্যাপ বন্ধ থাকলেও। উত্তর অনুমোদন ছাড়াই পাঠানোর চেষ্টা হবে। নিয়ম মিললে আপনার লেখা উত্তর যাবে। AI ভুল করতে পারে। গুরুত্বপূর্ণ কথোপকথনে এটি ব্যবহার করবেন না।")
                        .setNegativeButton("বাতিল", null)
                        .setPositiveButton("সম্মত, চালু করুন") { _, _ ->
                            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8402)
                            ReplyStore.set(this, "auto", false)
                            ReplyStore.set(this, "ai_auto", true)
                            AutoReplyService.sync(this)
                            render()
                        }.show()
                }
            } else {
                ReplyStore.set(this, "ai_auto", false)
                AutoReplyService.sync(this)
            }
        }
        modes.addView(space(10))
        modes.addView(label("লুপ সুরক্ষা: এই অ্যাপের পাঠানো লেখায় ১০ মিনিট অটো উত্তর নয়, একই লেখা ১০ মিনিটে একবার, একই চ্যাটে এক মিনিটে সর্বোচ্চ তিনটি অটো উত্তর। অন্য ব্যক্তির চ্যাটে এই বিরতি প্রযোজ্য নয়। অন্য অ্যাকাউন্ট থেকে একই লেখা ফিরলেও বাদ যাবে।", 12f, muted))
        modes.addView(label(if (AutoReplyService.running) "✓ ব্যাকগ্রাউন্ড সেবা চালু" else "ব্যাকগ্রাউন্ড সেবা বন্ধ", 13f, muted))
        modes.addView(label(if (ReplyListener.connected()) "✓ মেসেজ নোটিফিকেশন সংযুক্ত" else "মেসেজ নোটিফিকেশন সংযোগ অপেক্ষায়", 13f, muted))
        val lastState = getSharedPreferences("ai_auto_state", MODE_PRIVATE).getString("status", "").orEmpty()
        if (lastState.isNotEmpty()) modes.addView(label("সর্বশেষ অবস্থা: $lastState", 13f, muted))
        modes.addView(action("সংযোগের অবস্থা দেখুন", { render() }, false))
        addCard(modes)
        val account = card()
        account.addView(label("Google Login ও Gemini AI", 19f, dark, true))
        account.addView(space(8))
        val signedIn = GoogleAuth.account(this)
        account.addView(label(if (signedIn == null) "লগইন করা নেই" else "সংযুক্ত: ${signedIn.email ?: "Google account"}", 14f, muted))
        account.addView(space(10))
        account.addView(action("Google দিয়ে লগইন / অ্যাকাউন্ট পরিবর্তন", { GoogleAuth.signIn(this) }, false))
        if (signedIn != null) {
            account.addView(space(8))
            account.addView(action("Google থেকে লগআউট", {
                ReplyStore.set(this, "ai_auto", false)
                AiEngine.invalidatePending()
                AutoReplyService.sync(this)
                GoogleAuth.signOut(this) { render() }
            }, false))
        }
        account.addView(space(8))
        account.addView(label("AI উত্তর আপনার Cloudflare Worker-এর মাধ্যমে Gemini থেকে তৈরি হবে। কোনো API Key ফোনে রাখা হয় না।", 13f, muted))
        addCard(account)
        val notice = card()
        notice.addView(label("গোপনীয়তা ও সীমাবদ্ধতা", 17f, dark, true))
        notice.addView(space(8))
        notice.addView(label("সর্বশেষ ৫০টি শনাক্ত করা নোটিফিকেশনের লেখা ফোনে রাখা হয়। AI উত্তর চাইলে আপনার অনুমতির পর মেসেজ Cloudflare ও Gemini-তে পাঠানো হয়। সব অ্যাপে রিপ্লাই কাজ নাও করতে পারে।", 14f, muted))
        notice.addView(space(10))
        notice.addView(action("গোপনীয়তার বিস্তারিত", {
            AlertDialog.Builder(this).setTitle("গোপনীয়তা ও ডেটা")
                .setMessage("পরিচালক: Hossain Ahmed Noman\nযোগাযোগ: hossainahmednumanbd@gmail.com\n\nGoogle লগইন, মেসেজ এবং AI নির্দেশনা শুধু রিপ্লাইয়ের কাজে ব্যবহৃত হয়। ফোনে সর্বশেষ ৫০টি নোটিফিকেশন, নিয়ম, নির্দেশনা ও সর্বশেষ AI উত্তর রাখা হয়। Gemini key ফোনে থাকে না। AI-তে শুধু মেসেজের লেখা ও নির্দেশনা যায়, প্রেরকের নাম আলাদা করে পাঠানো হয় না। লেখার ভেতরে থাকা নামসহ ব্যক্তিগত তথ্য অবশ্য যেতে পারে।\n\nCloudflare D1-এ Google subject ID-সহ দৈনিক ব্যবহার গণনা রাখা হয়। মেসেজ ও AI উত্তর সাধারণ অনুরোধে ডেটাবেসে সংরক্ষণ করা হয় না। রিপোর্ট করলে নির্বাচিত AI উত্তর ও অভিযোগ সর্বোচ্চ ৩১ দিন রাখা হয়। Google Gemini ও Cloudflare তাদের শর্ত অনুযায়ী অনুরোধ প্রক্রিয়া করে। Gemini free tier-এ সংবেদনশীল বা গোপন তথ্য পাঠাবেন না।\n\nঅটো মোড বন্ধ বা Notification Access প্রত্যাহার করতে পারবেন। নিচের অপশনে স্থানীয় ডেটা, সার্ভারের রিপোর্ট এবং সংযোগ মুছতে পারবেন। অপব্যবহার ঠেকাতে আজকের ব্যবহার গণনা দিন শেষ পর্যন্ত থাকবে। WhatsApp বা Messenger-এর নিজস্ব চ্যাট মোছা হবে না।")
                .setPositiveButton("ঠিক আছে", null).show()
        }, false))
        notice.addView(space(8)); notice.addView(action("ভুল / আপত্তিকর AI উত্তর রিপোর্ট", { reportReply() }, false))
        notice.addView(space(8)); notice.addView(action("অ্যাকাউন্টের ডেটা মুছুন ও সংযোগ সরান", {
            AlertDialog.Builder(this).setTitle("এই অ্যাপের ডেটা মুছবেন?")
                .setMessage("মেসেজের স্থানীয় কপি, নিজের নিয়ম, AI নির্দেশনা এবং সার্ভারের রিপোর্ট মুছে যাবে। Google সংযোগ সরবে। আজকের AI ব্যবহার গণনা দিন শেষ পর্যন্ত থাকবে।")
                .setNegativeButton("বাতিল", null).setPositiveButton("মুছুন") { _, _ ->
                    ReplyStore.set(this, "ai_auto", false); ReplyStore.set(this, "auto", false); AutoReplyService.sync(this)
                    if (!AiEngine.configured(this)) eraseLocalData()
                    else AiEngine.deleteAccount(this) { result ->
                        if (result.isSuccess) eraseLocalData()
                        else AlertDialog.Builder(this).setTitle("সার্ভারের ডেটা মোছা যায়নি")
                            .setMessage("অটো রিপ্লাই বন্ধ আছে। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন; ডেটা মোছার অনুরোধ অসম্পূর্ণ রয়েছে।")
                            .setPositiveButton("ঠিক আছে", null).show()
                    }
                }.show()
        }, false))
        addCard(notice)
        column.addView(action("✓ সেটিংস সংরক্ষিত হয় স্বয়ংক্রিয়ভাবে", { Toast.makeText(this, "সেটিংস সংরক্ষিত আছে", Toast.LENGTH_SHORT).show() }))
    }
}
