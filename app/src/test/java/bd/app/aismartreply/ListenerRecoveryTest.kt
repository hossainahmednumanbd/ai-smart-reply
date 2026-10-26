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
    @Test fun repeatedFailedRequestsEscalateToBindingResetButHealthyStateDoesNot() {
        val recovery = ListenerRecovery(); val events = mutableListOf<String>()
        for (time in listOf(0L, 30000L, 60000L)) {
            recovery.check(true, false, time, restart = { events.add("reset") }) { events.add("bind") }
        }
        assertEquals(listOf("bind", "bind", "reset"), events)
        recovery.check(true, true, 90000, restart = { events.add("reset") }) { events.add("bind") }
        assertEquals(3, events.size)
    }
    @Test fun resetNeverLoopsAndRevokedAccessCancelsFurtherAttempts() {
        val recovery = ListenerRecovery(); var resets = 0
        for (time in 0L..290000L step 10000L) {
            recovery.check(true, false, time, restart = { resets++ }) { }
        }
        assertEquals(1, resets)
        recovery.check(false, false, 400000L, restart = { resets++ }) { fail("revoked") }
        assertEquals(1, resets)
    }
    @Test fun oldAndroidWithoutResetSupportKeepsUsingPublicRebindOnly() {
        val recovery = ListenerRecovery(); var binds = 0
        for (time in listOf(0L, 30000L, 60000L, 90000L)) recovery.check(true, false, time) { binds++ }
        assertEquals(4, binds)
    }
}
