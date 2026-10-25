package bd.app.aismartreply

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        fun label(message: String, size: Float) = TextView(this).apply {
            text = message
            textSize = size
            setTextColor(Color.rgb(25, 35, 55))
            setPadding(0, 0, 0, 32)
        }
        root.addView(label("AI Smart Reply", 28f))
        root.addView(label("প্রথম Android ডেমো", 21f))
        root.addView(label("এখনও WhatsApp বা Messenger-এর মেসেজ পড়া বা পাঠানো হয় না। এই সংস্করণে কোনো ব্যক্তিগত তথ্য সংগ্রহ করা হয় না।", 17f))
        root.addView(label("পরবর্তী ধাপ: ব্যবহারকারীর সম্মতিসহ নোটিফিকেশন অ্যাক্সেস, ম্যানুয়াল অনুমোদন, সমর্থিত রিপ্লাই এবং AI ইঞ্জিন।", 16f))
        val button = Button(this).apply {
            text = "ডেমো রিপ্লাই দেখুন"
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("AI Reply Demo")
                    .setMessage("নমুনা মেসেজ: কেমন আছেন?\n\nসাজেস্টেড রিপ্লাই: আলহামদুলিল্লাহ, ভালো আছি। আপনি কেমন আছেন?\n\nএটি শুধু ডেমো, কোনো মেসেজ পাঠানো হয়নি।")
                    .setPositiveButton("ঠিক আছে", null).show()
            }
        }
        root.addView(button)
        setContentView(root)
    }
}
