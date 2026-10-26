package bd.app.aismartreply

/** Escalate repeated failed binds without revoking access or repeatedly resetting a healthy service. */
internal class ListenerRecovery {
    private var lastAttempt: Long? = null
    private var failures = 0
    private var lastRestart: Long? = null
    @Synchronized fun reset() { lastAttempt = null; failures = 0 }
    @Synchronized fun check(access: Boolean, connected: Boolean, now: Long,
        restart: (() -> Unit)? = null, rebind: () -> Unit) {
        if (!access || connected) { reset(); return }
        val last = lastAttempt
        if (last != null && now >= last && now - last < 30000L) return
        lastAttempt = now
        val previousRestart = lastRestart
        if (failures >= 2 && restart != null &&
            (previousRestart == null || now < previousRestart || now - previousRestart >= 300000L)) {
            lastRestart = now; failures = 0; restart()
        } else { failures++; rebind() }
    }
}
