package bd.app.aismartreply

/** Throttled retries only while access remains granted. Never toggles a permission. */
internal class ListenerRecovery {
    private var lastAttempt: Long? = null
    @Synchronized fun reset() { lastAttempt = null }
    @Synchronized fun check(access: Boolean, connected: Boolean, now: Long, rebind: () -> Unit) {
        if (!access || connected) { reset(); return }
        val last = lastAttempt
        if (last != null && now >= last && now - last < 30000L) return
        lastAttempt = now
        rebind()
    }
}
