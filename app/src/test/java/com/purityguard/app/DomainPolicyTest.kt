package com.purityguard.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainPolicyTest {

    @Test
    fun majorPornDomainsAreBlocked() {
        val blocker = DomainBlocker(
            setOf("xnxx.com", "xvideos.com", "pornhub.com", "rule34.xxx", "sex.com")
        )

        assertTrue(blocker.shouldBlock("xnxx.com"))
        assertTrue(blocker.shouldBlock("www.xvideos.com"))
        assertTrue(blocker.shouldBlock("m.pornhub.com"))
        assertTrue(blocker.shouldBlock("api.rule34.xxx"))
        assertTrue(blocker.shouldBlock("sex.com"))
    }

    @Test
    fun generalPurposePlatformsNotBlockedByDefault() {
        val blocker = DomainBlocker(
            setOf("xnxx.com", "xvideos.com", "pornhub.com", "rule34.xxx", "sex.com")
        )

        assertFalse(blocker.shouldBlock("twitter.com"))
        assertFalse(blocker.shouldBlock("x.com"))
        assertFalse(blocker.shouldBlock("reddit.com"))
        assertFalse(blocker.shouldBlock("www.reddit.com"))
    }

    @Test
    fun strictDomainMatchDoesNotOverblockAdjacentDomains() {
        val blocker = DomainBlocker(setOf("pornhub.com"))
        assertTrue(blocker.shouldBlock("pornhub.com"))
        assertTrue(blocker.shouldBlock("img.pornhub.com"))
        assertFalse(blocker.shouldBlock("notpornhub.com"))
        assertFalse(blocker.shouldBlock("pornhub.com.evil.com"))
    }
}
