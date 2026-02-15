package com.purityguard.app

import org.junit.Assert.assertTrue
import org.junit.Test

class PurityVpnServiceTest {
    @Test
    fun parseMissAllowLogLine_isExplicitlyFailOpen() {
        val line = PurityVpnService.parseMissAllowLogLine(123)

        assertTrue(line.contains("reason=parse_miss"))
        assertTrue(line.contains("decision=ALLOW"))
        assertTrue(line.contains("len=123"))
    }
}
