package bd.app.aismartreply

/** Reject late/duplicate recognizer callbacks and settle Stop only once. */
class VoiceCaptureSession {
    enum class State { IDLE, LISTENING, STOPPING }
    var state = State.IDLE
        private set
    var generation = 0
        private set
    fun begin(): Int { check(state == State.IDLE); state = State.LISTENING; return ++generation }
    fun stop() { if (state == State.LISTENING) state = State.STOPPING }
    fun accepts(id: Int) = id == generation && state != State.IDLE
    fun settle(id: Int): Boolean {
        if (!accepts(id)) return false
        state = State.IDLE
        return true
    }
    fun cancel() { state = State.IDLE; generation++ }
}
