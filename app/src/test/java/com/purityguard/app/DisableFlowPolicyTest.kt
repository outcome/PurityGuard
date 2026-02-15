package com.purityguard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisableFlowPolicyTest {

    @Test
    fun startUsesConfiguredPrimaryWait() {
        val state = DisableFlowPolicy.start(DisableFlowConfig(waitPrimarySeconds = 2, waitConfirmSeconds = 1))
        assertEquals(DisableStage.WAIT_15, state.stage)
        assertEquals(2, state.secondsLeft)
        assertTrue(state.protectionEnabled)
    }

    @Test
    fun zeroWaitConfigAllowsImmediateProgress() {
        val cfg = DisableFlowConfig(waitPrimarySeconds = 0, waitConfirmSeconds = 0)
        var state = DisableFlowPolicy.start(cfg)
        state = DisableFlowPolicy.confirm(state, cfg)
        assertEquals(DisableStage.WAIT_5, state.stage)
        assertEquals(0, state.secondsLeft)
        state = DisableFlowPolicy.confirm(state, cfg)
        assertEquals(null, state.stage)
        assertFalse(state.protectionEnabled)
    }

    @Test
    fun fullFlowRespectsConfiguredDurationsThenDisables() {
        val cfg = DisableFlowConfig(waitPrimarySeconds = 3, waitConfirmSeconds = 2)
        var state = DisableFlowPolicy.start(cfg)

        repeat(3) { state = DisableFlowPolicy.tick(state) }
        state = DisableFlowPolicy.confirm(state, cfg)
        assertEquals(DisableStage.WAIT_5, state.stage)
        assertEquals(2, state.secondsLeft)

        repeat(2) { state = DisableFlowPolicy.tick(state) }
        state = DisableFlowPolicy.confirm(state, cfg)
        assertEquals(null, state.stage)
        assertEquals(0, state.secondsLeft)
        assertFalse(state.protectionEnabled)
    }
}
