package com.purityguard.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("purity_guard", Context.MODE_PRIVATE)

    init {
        val seenVersion = prefs.getInt(KEY_APP_PREFS_VERSION, 0)
        if (seenVersion < CURRENT_APP_PREFS_VERSION) {
            // Safety default after update: require explicit user re-enable.
            val editor = prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putInt(KEY_APP_PREFS_VERSION, CURRENT_APP_PREFS_VERSION)

            // Product requirement: clear user-defined extra sites on the next app update/install migration.
            if (seenVersion < PREFS_VERSION_CLEAR_EXTRA_BLOCKED) {
                editor.remove(KEY_EXTRA_BLOCKED_DOMAINS)
            }

            editor.apply()
        }
    }

    fun isProtectionEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setProtectionEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun getExtraBlockedDomains(): Set<String> = prefs.getStringSet(KEY_EXTRA_BLOCKED_DOMAINS, emptySet()) ?: emptySet()

    fun addExtraBlockedDomain(domain: String) {
        val normalized = normalizeDomainOrNull(domain) ?: return
        val next = getExtraBlockedDomains().toMutableSet()
        next.add(normalized)
        prefs.edit().putStringSet(KEY_EXTRA_BLOCKED_DOMAINS, next).apply()
    }

    fun getInspirationMode(): InspirationMode {
        val raw = prefs.getString(KEY_INSPIRATION_MODE, InspirationMode.NONE.value) ?: InspirationMode.NONE.value
        return InspirationMode.from(raw)
    }

    fun setInspirationMode(mode: InspirationMode) {
        prefs.edit().putString(KEY_INSPIRATION_MODE, mode.value).apply()
    }

    fun getDnsProvider(): DnsProvider {
        val raw = prefs.getString(KEY_DNS_PROVIDER, DnsProvider.CLOUDFLARE.value) ?: DnsProvider.CLOUDFLARE.value
        return DnsProvider.from(raw)
    }

    fun setDnsProvider(provider: DnsProvider) {
        prefs.edit().putString(KEY_DNS_PROVIDER, provider.value).apply()
    }

    fun getDonationUrl(): String = DEFAULT_DONATION_URL

    fun setDonationUrl(url: String) {
        // Donation URL is fixed for end-user builds.
    }

    fun isRedirectEnabled(): Boolean {
        if (!BuildConfig.DEBUG) return false
        return prefs.getBoolean(KEY_REDIRECT_ENABLED, true)
    }

    fun setRedirectEnabled(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_REDIRECT_ENABLED, enabled).apply()
    }

    // Backward-compatible naming used by existing service/UI logic.
    fun isAutoOpenBlockedPageEnabled(): Boolean = isRedirectEnabled()

    fun setAutoOpenBlockedPageEnabled(enabled: Boolean) {
        setRedirectEnabled(enabled)
    }

    fun getRedirectOrder(): List<RedirectMethod> {
        if (!BuildConfig.DEBUG) return emptyList()

        val raw = prefs.getString(KEY_REDIRECT_ORDER, DEFAULT_REDIRECT_ORDER)
            ?.split(',')
            ?.mapNotNull { RedirectMethod.from(it.trim()) }
            ?.distinct()
            .orEmpty()

        return if (raw.isEmpty()) RedirectMethod.entries else raw
    }

    fun setRedirectOrder(methods: List<RedirectMethod>) {
        if (!BuildConfig.DEBUG) return
        val cleaned = methods.distinct().ifEmpty { RedirectMethod.entries }
        prefs.edit().putString(KEY_REDIRECT_ORDER, cleaned.joinToString(",") { it.value }).apply()
    }

    fun isRedirectFallbackEnabled(): Boolean {
        if (!BuildConfig.DEBUG) return false
        return prefs.getBoolean(KEY_REDIRECT_FALLBACK_ENABLED, true)
    }

    fun setRedirectFallbackEnabled(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_REDIRECT_FALLBACK_ENABLED, enabled).apply()
    }

    fun getExternalRedirectUrlTemplate(): String {
        if (!BuildConfig.DEBUG) return DEFAULT_EXTERNAL_REDIRECT_URL_TEMPLATE
        return prefs.getString(KEY_EXTERNAL_REDIRECT_URL_TEMPLATE, DEFAULT_EXTERNAL_REDIRECT_URL_TEMPLATE).orEmpty()
    }

    fun setExternalRedirectUrlTemplate(urlTemplate: String) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putString(KEY_EXTERNAL_REDIRECT_URL_TEMPLATE, urlTemplate.trim()).apply()
    }

    fun isDisableFlowTimerEnabled(): Boolean = prefs.getBoolean(KEY_DISABLE_FLOW_TIMER_ENABLED, true)

    fun setDisableFlowTimerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_FLOW_TIMER_ENABLED, enabled).apply()
    }

    fun isRapidBlockGuardEnabled(): Boolean = prefs.getBoolean(KEY_RAPID_BLOCK_GUARD_ENABLED, true)

    fun setRapidBlockGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RAPID_BLOCK_GUARD_ENABLED, enabled).apply()
    }

    fun getDisableFlowConfig(): DisableFlowConfig {
        val defaultPrimary = 15
        val defaultConfirm = 5

        return DisableFlowConfig(
            waitPrimarySeconds = prefs.getInt(KEY_DISABLE_WAIT_PRIMARY_SECONDS, defaultPrimary).coerceIn(0, 180),
            waitConfirmSeconds = prefs.getInt(KEY_DISABLE_WAIT_CONFIRM_SECONDS, defaultConfirm).coerceIn(0, 180)
        )
    }

    fun setDisableFlowConfig(config: DisableFlowConfig) {
        prefs.edit()
            .putInt(KEY_DISABLE_WAIT_PRIMARY_SECONDS, config.waitPrimarySeconds.coerceIn(0, 180))
            .putInt(KEY_DISABLE_WAIT_CONFIRM_SECONDS, config.waitConfirmSeconds.coerceIn(0, 180))
            .apply()
    }

    fun recordBlockedAlert(domain: String, timestampMs: Long) {
        val next = getAlertsFiredCount() + 1
        prefs.edit()
            .putString(KEY_LAST_BLOCKED_DOMAIN, domain)
            .putLong(KEY_LAST_BLOCKED_AT_MS, timestampMs)
            .putInt(KEY_ALERTS_FIRED_COUNT, next)
            .apply()
    }

    fun getLastBlockedDomain(): String = prefs.getString(KEY_LAST_BLOCKED_DOMAIN, "-") ?: "-"
    fun getLastBlockedAtMs(): Long = prefs.getLong(KEY_LAST_BLOCKED_AT_MS, 0L)
    fun getAlertsFiredCount(): Int = prefs.getInt(KEY_ALERTS_FIRED_COUNT, 0)

    private fun normalizeDomainOrNull(raw: String): String? {
        val line = raw.trim().lowercase().trim('.')
        if (line.isBlank() || !line.matches(Regex("^[a-z0-9.-]+$"))) return null
        val labels = line.split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return null
        if (labels.any { it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null
        return labels.joinToString(".")
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_APP_PREFS_VERSION = "app_prefs_version"
        private const val KEY_EXTRA_BLOCKED_DOMAINS = "extra_blocked_domains"
        private const val KEY_INSPIRATION_MODE = "inspiration_mode"
        private const val KEY_REDIRECT_ENABLED = "redirect_enabled"
        private const val KEY_REDIRECT_ORDER = "redirect_order"
        private const val KEY_REDIRECT_FALLBACK_ENABLED = "redirect_fallback_enabled"
        private const val KEY_EXTERNAL_REDIRECT_URL_TEMPLATE = "external_redirect_url_template"
        private const val KEY_DISABLE_WAIT_PRIMARY_SECONDS = "disable_wait_primary_seconds"
        private const val KEY_DISABLE_WAIT_CONFIRM_SECONDS = "disable_wait_confirm_seconds"
        private const val KEY_DISABLE_FLOW_TIMER_ENABLED = "disable_flow_timer_enabled"
        private const val KEY_RAPID_BLOCK_GUARD_ENABLED = "rapid_block_guard_enabled"
        private const val KEY_LAST_BLOCKED_DOMAIN = "last_blocked_domain"
        private const val KEY_LAST_BLOCKED_AT_MS = "last_blocked_at_ms"
        private const val KEY_ALERTS_FIRED_COUNT = "alerts_fired_count"
        private const val KEY_DNS_PROVIDER = "dns_provider"

        private const val PREFS_VERSION_CLEAR_EXTRA_BLOCKED = 4
        private const val CURRENT_APP_PREFS_VERSION = PREFS_VERSION_CLEAR_EXTRA_BLOCKED

        const val DEFAULT_DONATION_URL = "https://www.buymeacoffee.com/"
        const val BLOCKED_EVENT_COOLDOWN_MS = 12_000L
        const val DEFAULT_EXTERNAL_REDIRECT_URL_TEMPLATE = "https://example.com/blocked?domain={domain}"
        private const val DEFAULT_REDIRECT_ORDER = "activity,localhost,external"
    }
}

enum class InspirationMode(val value: String) {
    NONE("none"),
    BIBLE("bible"),
    QURAN("quran");

    companion object {
        fun from(raw: String): InspirationMode = entries.firstOrNull { it.value == raw } ?: NONE
    }
}

enum class DnsProvider(val value: String, val label: String) {
    CLOUDFLARE("cloudflare", "Cloudflare"),
    CLEANBROWSING("cleanbrowsing", "CleanBrowsing"),
    OPENDNS("opendns", "OpenDNS");

    companion object {
        fun from(raw: String): DnsProvider = entries.firstOrNull { it.value == raw } ?: CLOUDFLARE
    }
}
