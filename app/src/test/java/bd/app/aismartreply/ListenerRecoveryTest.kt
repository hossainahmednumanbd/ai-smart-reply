package bd.app.aismartreply

import org.junit.Assert.*
import org.junit.Test

class ListenerRecoveryTest {
    @Test fun retriesDisconnectedListenerWithoutReopeningAppAndThrottlesRequests() {
        val recovery = ListenerRecovery(); var calls = 0
        recovery.check(true, false, 0) { calls++ }
        recovery.check(true, false, 1000) { calls++ }
        assertEquals(1, calls)
        recovery.check(true, false, 30000) { calls++ }
        recovery.check(true, false, 60000) { calls++ }
        assertEquals(3, calls)
    }
    @Test fun healthyConnectionAndRevokedAccessNeverTriggerRebind() {
        val recovery = ListenerRecovery(); var calls = 0
        recovery.check(true, true, 0) { calls++ }
        recovery.check(false, false, 30000) { calls++ }
        recovery.check(false, false, 60000) { calls++ }
        assertEquals(0, calls)
        recovery.check(true, false, 60001) { calls++ }
        assertEquals(1, calls)
    }
    @Test fun recoveryCanRetryImmediatelyAfterANewDisconnection() {
        val recovery = ListenerRecovery(); var calls = 0
        recovery.check(true, false, 1000) { calls++ }
        recovery.reset()
        recovery.check(true, false, 1001) { calls++ }
        assertEquals(2, calls)
    }
}
