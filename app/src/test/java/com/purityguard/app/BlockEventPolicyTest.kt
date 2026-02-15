package com.purityguard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEventPolicyTest {

    @Test
    fun cooldownSuppressesRedirectsAndFallbacks() {
        val plan = BlockEventPolicy.plan(
            nowMs = 10_000,
            lastRedirectAtMs = 9_500,
            cooldownMs = 12_000,
            redirectEnabled = true,
            redirectOrder = listOf(RedirectMethod.ACTIVITY),
            notificationSent = false
        )

        assertTrue(plan.cooldownActive)
        assertTrue(plan.methodsToAttemptInOrder.isEmpty())
        assertFalse(plan.shouldSendPreemptiveFallback)
        assertFalse(plan.shouldSendDisabledFallback)
    }

    @Test
    fun enabledRedirectPreservesConfiguredOrderAndPreemptiveFallbackWhenNotificationMissing() {
        val order = listOf(RedirectMethod.LOCALHOST_PAGE, RedirectMethod.EXTERNAL_URL, RedirectMethod.ACTIVITY)
        val plan = BlockEventPolicy.plan(
            nowMs = 30_000,
            lastRedirectAtMs = 0,
            cooldownMs = 12_000,
            redirectEnabled = true,
            redirectOrder = order,
            notificationSent = false
        )

        assertFalse(plan.cooldownActive)
        assertEquals(order, plan.methodsToAttemptInOrder)
        assertTrue(plan.shouldSendPreemptiveFallback)
        assertFalse(plan.shouldSendDisabledFallback)
    }

    @Test
    fun disabledRedirectRequestsDisabledFallbackIfNoNotification() {
        val plan = BlockEventPolicy.plan(
            nowMs = 30_000,
            lastRedirectAtMs = 0,
            cooldownMs = 12_000,
            redirectEnabled = false,
            redirectOrder = listOf(RedirectMethod.ACTIVITY),
            notificationSent = false
        )

        assertTrue(plan.methodsToAttemptInOrder.isEmpty())
        assertTrue(plan.shouldSendDisabledFallback)
    }
}
