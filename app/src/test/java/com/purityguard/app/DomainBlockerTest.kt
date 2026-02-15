package com.purityguard.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlockerTest {
    @Test
    fun blocksRootAndSubdomain() {
        val blocker = DomainBlocker(setOf("xvideos.com"))
        assertTrue(blocker.shouldBlock("xvideos.com"))
        assertTrue(blocker.shouldBlock("m.xvideos.com"))
        assertFalse(blocker.shouldBlock("example.com"))
    }

    @Test
    fun strictDotBoundaryPreventsSubstringOverblock() {
        val blocker = DomainBlocker(setOf("pornhub.com"))
        assertTrue(blocker.shouldBlock("pornhub.com"))
        assertTrue(blocker.shouldBlock("www.pornhub.com"))
        assertTrue(blocker.shouldBlock("m.pornhub.com"))

        // Must NOT match as a substring.
        assertFalse(blocker.shouldBlock("notpornhub.com"))
        assertFalse(blocker.shouldBlock("pornhub.com.evil.com"))
    }

    @Test
    fun failOpenForInvalidOrUnknownDomains() {
        val blocker = DomainBlocker(setOf("pornhub.com"))

        assertFalse(blocker.shouldBlock(""))
        assertFalse(blocker.shouldBlock("com"))
        assertFalse(blocker.shouldBlock("bad domain"))
        assertFalse(blocker.shouldBlock("foo_bar.com"))
    }

    @Test
    fun invalidBlocklistEntriesAreIgnoredWithoutOverblocking() {
        val blocker = DomainBlocker(setOf("com", "-bad.com", "", "pornhub.com"))

        assertTrue(blocker.shouldBlock("pornhub.com"))
        assertFalse(blocker.shouldBlock("google.com"))
    }

    @Test
    fun domainNormalizationStaysStrictAndSafe() {
        val blocker = DomainBlocker(setOf("PornHub.COM."))

        assertTrue(blocker.shouldBlock("WWW.PORNHUB.COM."))
        assertFalse(blocker.shouldBlock("pornhub.comevil"))
        assertFalse(blocker.shouldBlock("pornhub"))
    }
}
