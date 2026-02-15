package com.purityguard.app

/**
 * DomainBlocker implements *strict* domain matching:
 * - exact match on the blocked base domain
 * - or any subdomain of a blocked base domain (dot-boundary suffix match)
 *
 * It intentionally does NOT do substring/contains matching (to avoid overblocking).
 *
 * Fail-open posture: unknown/invalid domains are never blocked.
 */
class DomainBlocker(blocklist: Set<String>) {
    private val safeBlocklist: Set<String> = blocklist
        .mapNotNull { normalizeDomainOrNull(it) }
        .toSet()

    // Known Secure DNS bootstrap hosts that browsers may use to bypass local UDP DNS interception.
    // Blocking these hostnames forces fallback to system DNS path (which we control in VPN loop).
    private val dohBootstrapHosts = setOf(
        "dns.google",
        "dns.cloudflare.com",
        "cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "security.cloudflare-dns.com",
        "family.cloudflare-dns.com",
        "dns.quad9.net",
        "dns.adguard-dns.com",
        "dns.nextdns.io",
        "doh.opendns.com"
    )

    fun shouldBlock(domain: String): Boolean {
        val normalized = normalizeDomainOrNull(domain) ?: return false // fail-open

        if (isDohBootstrapDomain(normalized)) return true

        // Strict suffix policy implemented via suffix generation + set lookup.
        // This blocks a base domain and all of its subdomains, without false positives like "notpornhub.com".
        val labels = normalized.split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return false

        // Try all suffixes that are at least 2 labels long.
        // Example: m.pornhub.com -> [m.pornhub.com, pornhub.com]
        for (i in 0..(labels.size - 2)) {
            val suffix = labels.subList(i, labels.size).joinToString(".")
            if (safeBlocklist.contains(suffix)) return true
        }

        return false
    }

    fun isDohBootstrapDomain(domain: String): Boolean {
        if (dohBootstrapHosts.contains(domain)) return true
        return dohBootstrapHosts.any { host -> domain.endsWith(".$host") }
    }

    private fun normalizeDomainOrNull(raw: String): String? {
        val normalized = raw.lowercase().trim('.').trim()
        if (normalized.isBlank()) return null
        if (!normalized.matches(Regex("^[a-z0-9.-]+$"))) return null

        val labels = normalized.split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return null // reject TLD-only / overbroad entries like "com"
        if (labels.any { it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null

        return labels.joinToString(".")
    }
}
