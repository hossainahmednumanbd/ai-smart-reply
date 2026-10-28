package bd.app.aismartreply

import android.accounts.Account
import android.app.Activity
import android.app.AlertDialog
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DriveBackups {
    const val REQUEST_CODE = 8402
    private const val JOB = 8403
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("drive_backups", 0)
    private fun owner(ctx: Context) = GoogleAuth.account(ctx)?.id.orEmpty()
    private fun matches(ctx: Context, expected: String) = expected.isNotBlank() && owner(ctx) == expected
    private fun active(ctx: Context) = owner(ctx).isNotBlank() && prefs(ctx).getString("owner", "") == owner(ctx)
    private fun request(ctx: Context): AuthorizationRequest {
        val email = GoogleAuth.account(ctx)?.email ?: error("আগে Google দিয়ে লগইন করুন")
        return AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
            .setRequestedScopes(listOf(Scope(SCOPE))).build()
    }
    fun status(ctx: Context): String {
        val p = prefs(ctx)
        if (!active(ctx)) return (if (owner(ctx).isBlank()) "Drive ব্যাকআপ বন্ধ। আগে Google দিয়ে লগইন করুন।"
            else "Google অ্যাকাউন্ট সংযুক্ত। Drive ব্যাকআপ এখনো চালু হয়নি; ব্যাকআপ চালু / অনুমতি দিন চাপুন।") +
            p.getString("error", "").orEmpty().let { if (it.isBlank()) "" else "\nসর্বশেষ চেষ্টা ব্যর্থ: $it" }
        val last = p.getLong("last", 0)
        val time = if (last == 0L) "এখনো সফল ব্যাকআপ হয়নি" else "সর্বশেষ সফল ব্যাকআপ: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last))
        val pending = p.getLong("revision", 0) != p.getLong("saved_revision", -1)
        return "অ্যাকাউন্ট: ${GoogleAuth.account(ctx)?.email}\n$time\n" +
            (if (pending) "পরিবর্তন ব্যাকআপের অপেক্ষায়। " else "এই ফোনের পরিবর্তন সংরক্ষিত। ") + p.getString("error", "")
    }
    @Synchronized fun disconnect(ctx: Context) {
        prefs(ctx).edit().remove("owner").remove("pending_owner").remove("pending_action").remove("session").remove("error").apply()
        ctx.getSystemService(JobScheduler::class.java).cancel(JOB)
    }
    @Synchronized fun changed(ctx: Context) {
        val p = prefs(ctx)
        p.edit().putLong("revision", p.getLong("revision", 0) + 1).apply()
        schedule(ctx)
    }
    fun schedule(ctx: Context) {
        if (!active(ctx)) return
        if (prefs(ctx).getLong("revision", 0) == prefs(ctx).getLong("saved_revision", -1)) return
        val job = JobInfo.Builder(JOB, ComponentName(ctx, DriveBackupJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(3000)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build()
        runCatching { ctx.getSystemService(JobScheduler::class.java).schedule(job) }
            .onFailure { prefs(ctx).edit().putString("error", "স্বয়ংক্রিয় কাজ চালু হয়নি; এখনই ব্যাকআপ চাপুন।").apply() }
    }
    fun show(activity: Activity, refreshed: () -> Unit) {
        val items = arrayOf("Drive ব্যাকআপ চালু / অনুমতি দিন", "এখনই ব্যাকআপ করুন", "Drive থেকে ফিরিয়ে আনুন", "স্বয়ংক্রিয় ব্যাকআপ বন্ধ করুন")
        AlertDialog.Builder(activity).setTitle("নিজের Google Drive-এ ব্যাকআপ")
            .setMessage(status(activity) + "\n\nব্যবসার তথ্য, নিয়ম, টেমপ্লেট ও AI নির্দেশনা আপনার Drive-এ থাকবে। ইমেইল, অডিও, মেসেজ বা পাসওয়ার্ড যাবে না।")
            .setPositiveButton("অপশন দেখুন") { _, _ ->
                AlertDialog.Builder(activity).setTitle("ব্যাকআপের অপশন").setItems(items) { _, index ->
                    when (index) {
                        0 -> authorize(activity, "setup")
                        1 -> if (active(activity)) authorize(activity, "backup") else toast(activity, "আগে Drive ব্যাকআপ চালু করুন")
                        2 -> authorize(activity, "restore")
                        3 -> { disconnect(activity); toast(activity, "ব্যাকআপ বন্ধ। আগের Drive কপিগুলো রাখা আছে।"); refreshed() }
                    }
                }.setNegativeButton("বন্ধ", null).show()
            }.setNegativeButton("বন্ধ", null).show()
    }
    fun start(activity: Activity, operation: String) {
        if (operation == "backup" && !active(activity)) { authorize(activity, "setup"); return }
        authorize(activity, operation)
    }
    private fun failed(activity: Activity, expected: String, message: String) {
        if (!matches(activity, expected)) return
        prefs(activity).edit().putString("error", message).apply()
        if (!activity.isDestroyed && !activity.isFinishing) AlertDialog.Builder(activity)
            .setTitle("Drive ব্যাকআপ সম্পূর্ণ হয়নি").setMessage(message + "\n\nফোনের তথ্য অক্ষত আছে। সফল ব্যাকআপের সময় না দেখা পর্যন্ত ব্যাকআপ নিশ্চিত নয়।")
            .setPositiveButton("ঠিক আছে", null).show()
    }
    private fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
    private fun authorize(activity: Activity, action: String) {
        val expected = owner(activity)
        if (expected.isBlank()) { toast(activity, "আগে Google দিয়ে লগইন করুন"); return }
        prefs(activity).edit().putString("pending_owner", expected).putString("pending_action", action).apply()
        Identity.getAuthorizationClient(activity).authorize(request(activity))
            .addOnSuccessListener { result ->
                if (!matches(activity, expected) || activity.isDestroyed) return@addOnSuccessListener
                if (result.hasResolution()) {
                    runCatching { activity.startIntentSenderForResult(result.pendingIntent!!.intentSender, REQUEST_CODE, null, 0, 0, 0) }
                        .onFailure { toast(activity, "Google-এর অনুমতির পেজ খুলতে পারেনি") }
                } else result.accessToken?.let { action(activity, expected, action, it) }
                    ?: toast(activity, "Drive-এর অনুমতি পাওয়া যায়নি")
            }.addOnFailureListener { failed(activity, expected, "Google Drive-এর অনুমতি পাওয়া যায়নি। আবার অনুমতি দিন; Google Cloud-এর OAuth সেটিংও পরীক্ষা করুন।") }
    }
    fun result(activity: Activity, resultCode: Int, data: Intent?) {
        val p = prefs(activity); val expected = p.getString("pending_owner", "").orEmpty()
        val operation = p.getString("pending_action", "setup").orEmpty()
        p.edit().remove("pending_owner").remove("pending_action").apply()
        if (resultCode != Activity.RESULT_OK || !matches(activity, expected)) { toast(activity, "Drive অনুমতি দেওয়া হয়নি; ফোনের তথ্য অক্ষত আছে"); return }
        runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken ?: error("No token") }
            .onSuccess { action(activity, expected, operation, it) }.onFailure { toast(activity, "Drive-এর অনুমতি সম্পূর্ণ হয়নি") }
    }
    @Synchronized private fun connect(ctx: Context, expected: String) {
        check(matches(ctx, expected)) { "অ্যাকাউন্ট পরিবর্তিত হয়েছে" }
        prefs(ctx).edit().putString("owner", expected).putString("session", java.util.UUID.randomUUID().toString())
            .putLong("saved_revision", -1).putLong("last", 0).putString("error", "").apply()
    }
    private fun action(activity: Activity, expected: String, operation: String, token: String) {
        if (!matches(activity, expected)) return
        if (operation == "backup") { manualUpload(activity, expected, token); return }
        toast(activity, "Drive-এ ব্যাকআপ খোঁজা হচ্ছে…")
        executor.execute {
            val result = runCatching { DriveBackupApi(token).list() }
            main.post {
                if (!matches(activity, expected) || activity.isDestroyed || activity.isFinishing) return@post
                result.onSuccess { entries ->
                    if (entries.isEmpty()) {
                        if (operation == "restore") toast(activity, "এই Google অ্যাকাউন্টে ব্যাকআপ পাওয়া যায়নি")
                        else confirmCurrent(activity, expected, token)
                    } else {
                        val labels = entries.map { it.time.replace('T', ' ').take(19) + " UTC" }.toTypedArray()
                        AlertDialog.Builder(activity).setTitle("কোন ব্যাকআপ ফিরিয়ে আনবেন?")
                            .setItems(labels) { _, index -> confirmRestore(activity, expected, token, entries[index]) }
                            .setNeutralButton("ফোনের বর্তমান তথ্য ব্যাকআপ") { _, _ -> confirmCurrent(activity, expected, token) }
                            .setNegativeButton("পরে", null).show()
                    }
                }.onFailure { failed(activity, expected, it.message ?: "Drive-এ সংযোগ হয়নি") }
            }
        }
    }
    private fun confirmCurrent(activity: Activity, expected: String, token: String) {
        AlertDialog.Builder(activity).setTitle("বর্তমান তথ্যের ব্যাকআপ চালু করবেন?")
            .setMessage("এই ফোনের টেমপ্লেট ও ব্যবসার তথ্য ${GoogleAuth.account(activity)?.email}-এর Drive-এ যাবে। এরপর পরিবর্তন হলে স্বয়ংক্রিয় ব্যাকআপের চেষ্টা হবে। আগের Drive কপি মুছবে না।")
            .setNegativeButton("বাতিল", null).setPositiveButton("চালু ও ব্যাকআপ") { _, _ ->
                if (matches(activity, expected)) { connect(activity, expected); manualUpload(activity, expected, token) }
            }.show()
    }
    private fun confirmRestore(activity: Activity, expected: String, token: String, entry: DriveBackupApi.Entry) {
        val initial = BackupData.export(activity, expected).getJSONObject("data").toString()
        executor.execute {
            val downloaded = runCatching { val raw = DriveBackupApi(token).download(entry.id); BackupData.validate(raw, expected); raw }
            main.post {
                if (!matches(activity, expected) || activity.isDestroyed || activity.isFinishing) return@post
                downloaded.onSuccess { raw ->
                    AlertDialog.Builder(activity).setTitle("টেমপ্লেট ও ব্যবসার তথ্য ফিরিয়ে আনবেন?")
                        .setMessage("এই ফোনের বর্তমান টেমপ্লেট ও ব্যবসার তথ্য নির্বাচিত ব্যাকআপ দিয়ে বদলাবে। আগে বর্তমান তথ্যের একটি Drive কপি রাখা হবে। অটো রিপ্লাই নিজে থেকে চালু হবে না।")
                        .setNegativeButton("বাতিল", null).setPositiveButton("ফিরিয়ে আনুন") { _, _ ->
                            executor.execute {
                                val result = runCatching {
                                    check(matches(activity, expected)) { "অ্যাকাউন্ট পরিবর্তিত হয়েছে" }
                                    check(initial == BackupData.export(activity, expected).getJSONObject("data").toString()) { "ফোনের তথ্য বদলেছে; নতুন করে Restore করুন" }
                                    DriveBackupApi(token).upload(BackupData.export(activity, expected).toString())
                                    main.post {
                                        if (!matches(activity, expected) || activity.isDestroyed) return@post
                                        if (initial != BackupData.export(activity, expected).getJSONObject("data").toString()) { toast(activity, "ফোনের তথ্য বদলেছে; Restore হয়নি"); return@post }
                                        runCatching { BackupData.restore(activity, raw, expected); connect(activity, expected); changed(activity) }
                                            .onSuccess { toast(activity, "তথ্য ফিরেছে। Settings আবার খুলে দেখুন।") }
                                            .onFailure { toast(activity, "Restore হয়নি; ফোনের তথ্য পরীক্ষা করুন") }
                                    }
                                }
                                result.onFailure { error -> main.post { toast(activity, error.message ?: "Restore হয়নি") } }
                            }
                        }.show()
                }.onFailure { toast(activity, it.message ?: "ব্যাকআপ পড়া যায়নি") }
            }
        }
    }
    private fun manualUpload(activity: Activity, expected: String, token: String) {
        executor.execute { val result = runCatching { upload(activity.applicationContext, expected, token) }
            if (result.isFailure && matches(activity, expected) && active(activity)) {
                prefs(activity).edit().putString("error", result.exceptionOrNull()?.message ?: "ব্যাকআপ হয়নি; আবার চেষ্টা হবে।").apply()
                schedule(activity.applicationContext)
            }
            main.post { if (!activity.isDestroyed && matches(activity, expected)) {
                if (result.isSuccess) AlertDialog.Builder(activity).setTitle("ব্যাকআপ সফল হয়েছে")
                    .setMessage(status(activity)).setPositiveButton("ঠিক আছে", null).show()
                else failed(activity, expected, result.exceptionOrNull()?.message ?: "ব্যাকআপ হয়নি")
            } }
        }
    }
    @Synchronized private fun snapshot(ctx: Context, expected: String): Triple<Long, String, String> {
        check(active(ctx) && matches(ctx, expected)) { "ব্যাকআপ বন্ধ বা অ্যাকাউন্ট পরিবর্তিত" }
        val revision = prefs(ctx).getLong("revision", 0)
        val raw = BackupData.export(ctx, expected).toString(); BackupData.validate(raw, expected)
        check(revision == prefs(ctx).getLong("revision", 0)) { "তথ্য বদলাচ্ছে; আবার ব্যাকআপ হবে" }
        return Triple(revision, raw, prefs(ctx).getString("session", "").orEmpty())
    }
    private fun upload(ctx: Context, expected: String, token: String) {
        val (revision, raw, session) = snapshot(ctx, expected)
        DriveBackupApi(token).upload(raw)
        synchronized(this) {
            if (active(ctx) && matches(ctx, expected) && prefs(ctx).getString("session", "") == session)
                prefs(ctx).edit().putLong("last", System.currentTimeMillis()).putLong("saved_revision", revision).putString("error", "").apply()
        }
    }
    internal fun runJob(ctx: Context): Boolean {
        if (!active(ctx)) return false
        val expected = owner(ctx)
        return try {
            val result = Tasks.await(Identity.getAuthorizationClient(ctx).authorize(request(ctx)), 30, TimeUnit.SECONDS)
            if (result.hasResolution() || result.accessToken.isNullOrBlank()) {
                prefs(ctx).edit().putString("error", "Drive-এর অনুমতি আবার দিন।").apply(); false
            } else { upload(ctx, expected, result.accessToken!!); prefs(ctx).getLong("revision", 0) != prefs(ctx).getLong("saved_revision", -1) }
        } catch (_: Exception) { prefs(ctx).edit().putString("error", "ব্যাকআপ হয়নি; ইন্টারনেট ও Drive অনুমতি দেখুন। আবার চেষ্টা হবে।").apply(); active(ctx) }
    }
}
