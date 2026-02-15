package com.purityguard.app

/**
 * Redirect targets attempted after a blocked-domain event.
 */
enum class RedirectMethod(val value: String) {
    ACTIVITY("activity"),
    LOCALHOST_PAGE("localhost"),
    EXTERNAL_URL("external");

    companion object {
        fun from(raw: String): RedirectMethod? = entries.firstOrNull { it.value == raw }
    }
}

data class BlockEventPlan(
    val cooldownActive: Boolean,
    val methodsToAttemptInOrder: List<RedirectMethod>,
    val shouldSendPreemptiveFallback: Boolean,
    val shouldSendDisabledFallback: Boolean
)

object BlockEventPolicy {
    fun plan(
        nowMs: Long,
        lastRedirectAtMs: Long,
        cooldownMs: Long,
        redirectEnabled: Boolean,
        redirectOrder: List<RedirectMethod>,
        notificationSent: Boolean
    ): BlockEventPlan {
        if (nowMs - lastRedirectAtMs < cooldownMs) {
            return BlockEventPlan(
                cooldownActive = true,
                methodsToAttemptInOrder = emptyList(),
                shouldSendPreemptiveFallback = false,
                shouldSendDisabledFallback = false
            )
        }

        if (!redirectEnabled || redirectOrder.isEmpty()) {
            return BlockEventPlan(
                cooldownActive = false,
                methodsToAttemptInOrder = emptyList(),
                shouldSendPreemptiveFallback = false,
                shouldSendDisabledFallback = !notificationSent
            )
        }

        return BlockEventPlan(
            cooldownActive = false,
            methodsToAttemptInOrder = redirectOrder,
            shouldSendPreemptiveFallback = !notificationSent,
            shouldSendDisabledFallback = false
        )
    }

    fun shouldNotify(nowMs: Long, lastSentMs: Long, cooldownMs: Long): Boolean {
        return nowMs - lastSentMs >= cooldownMs
    }
}
