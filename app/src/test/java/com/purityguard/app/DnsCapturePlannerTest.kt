package com.purityguard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DnsCapturePlannerTest {
    @Test
    fun buildForwardingTargets_release_isCleanBrowsingOnly() {
        val system = listOf(
            InetAddress.getByName("9.9.9.9"),
            InetAddress.getByName("1.1.1.1")
        )

        val targets = DnsCapturePlanner.buildForwardingTargets(
            systemDnsServers = system, 
            allowDebugFallbacks = false,
            provider = DnsProvider.CLEANBROWSING
        )

        assertTrue(targets.any { it.hostAddress == "185.228.168.168" })
        assertTrue(targets.any { it.hostAddress == "185.228.169.168" })
        assertEquals(0, targets.count { it.hostAddress == "1.1.1.1" })
        assertEquals(0, targets.count { it.hostAddress == "9.9.9.9" })
    }

    @Test
    fun buildForwardingTargets_debug_includesSystemAndFallbackWithoutDupes() {
        val system = listOf(
            InetAddress.getByName("9.9.9.9"),
            InetAddress.getByName("1.1.1.1")
        )

        val targets = DnsCapturePlanner.buildForwardingTargets(
            systemDnsServers = system, 
            allowDebugFallbacks = true,
            provider = DnsProvider.CLEANBROWSING
        )

        assertTrue(targets.any { it.hostAddress == "185.228.168.168" })
        assertTrue(targets.any { it.hostAddress == "8.8.8.8" })
        assertEquals(1, targets.count { it.hostAddress == "1.1.1.1" })
    }

    @Test
    fun routePrefixLength_isPerAddressFamily() {
        assertEquals(32, DnsCapturePlanner.routePrefixLength(InetAddress.getByName("8.8.8.8")))
        assertEquals(128, DnsCapturePlanner.routePrefixLength(InetAddress.getByName("2001:4860:4860::8888")))
    }
}
