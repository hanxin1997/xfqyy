package com.xfqiu.floatball.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSetupPhaseTest {

    @Test
    fun unknownStoredValue_isSafelyReset() {
        assertEquals(BackgroundSetupPhase.IDLE, BackgroundSetupPhase.fromKey("future-value"))
    }

    @Test
    fun waitingPhase_survivesStorageRoundTrip() {
        val phase = BackgroundSetupPhase.WAITING_FOR_NATIVE_RETURN

        assertEquals(phase, BackgroundSetupPhase.fromKey(phase.storageKey))
    }
}
