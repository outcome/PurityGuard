package com.purityguard.app

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.LinkedHashSet

object DnsCapturePlanner {
    private val cleanBrowsingResolvers = listOf(
        InetAddress.getByName("185.228.168.168"),
        InetAddress.getByName("185.228.169.168"),
        InetAddress.getByName("2a0d:2a00:1::"),
        InetAddress.getByName("2a0d:2a00:2::")
    )

    private val cloudflareFamilyResolvers = listOf(
        InetAddress.getByName("1.1.1.3"),
        InetAddress.getByName("1.0.0.3"),
        InetAddress.getByName("2606:4700:4700::1113"),
        InetAddress.getByName("2606:4700:4700::1003")
    )

    private val openDnsFamilyResolvers = listOf(
        InetAddress.getByName("208.67.222.123"),
        InetAddress.getByName("208.67.220.123"),
        InetAddress.getByName("2620:0:ccc::2"),
        InetAddress.getByName("2620:0:ccd::2")
    )

    private val debugFallbackResolvers = listOf(
        InetAddress.getByName("1.1.1.1"),
        InetAddress.getByName("8.8.8.8"),
        InetAddress.getByName("2606:4700:4700::1111"),
        InetAddress.getByName("2001:4860:4860::8888")
    )

    fun getResolversForProvider(provider: DnsProvider): List<InetAddress> {
        return when (provider) {
            DnsProvider.CLEANBROWSING -> cleanBrowsingResolvers
            DnsProvider.CLOUDFLARE -> cloudflareFamilyResolvers
            DnsProvider.OPENDNS -> openDnsFamilyResolvers
        }
    }

    fun buildCaptureTargets(systemDnsServers: List<InetAddress>, allowDebugFallbacks: Boolean, provider: DnsProvider): List<InetAddress> {
        val merged = LinkedHashSet<InetAddress>()
        getResolversForProvider(provider).forEach { merged.add(it) }

        if (allowDebugFallbacks) {
            systemDnsServers.forEach { merged.add(it) }
            debugFallbackResolvers.forEach { merged.add(it) }
        }

        return merged.toList()
    }

    fun buildForwardingTargets(systemDnsServers: List<InetAddress>, allowDebugFallbacks: Boolean, provider: DnsProvider): List<InetAddress> {
        val merged = LinkedHashSet<InetAddress>()
        getResolversForProvider(provider).forEach { merged.add(it) }

        if (allowDebugFallbacks) {
            systemDnsServers.forEach { merged.add(it) }
            debugFallbackResolvers.forEach { merged.add(it) }
        }

        return merged.toList()
    }

    fun routePrefixLength(address: InetAddress): Int {
        return when (address) {
            is Inet4Address -> 32
            is Inet6Address -> 128
            else -> 32
        }
    }

    fun hasIpv6(servers: List<InetAddress>): Boolean = servers.any { it is Inet6Address }
}
