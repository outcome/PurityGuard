package com.purityguard.app

/**
 * PacketParsers: minimal IP( v4/v6 ) + UDP + DNS helpers for the VPN tunnel.
 *
 * Design goals:
 * - DNS-only interception (UDP/53) for both IPv4 and IPv6 packets.
 * - Fail-open: if anything looks unfamiliar or malformed, return null so callers ALLOW.
 * - Keep parsing conservative: we don't attempt to support every IPv6 extension header variant.
 */
object PacketParsers {

    /** Parsed representation of a UDP DNS query as seen on the VPN TUN interface. */
    data class DnsQueryPacket(
        val domain: String?,
        val queryType: Int,
        val rawDnsPayload: ByteArray,
        val ipVersion: Int,
        val ipHeaderLen: Int,
        val udpHeaderOffset: Int
    )

    fun parseDnsQuery(ipPacket: ByteArray, length: Int): DnsQueryPacket? {
        if (length < 1) return null
        val version = (ipPacket[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseDnsQueryV4(ipPacket, length)
            6 -> parseDnsQueryV6(ipPacket, length)
            else -> null
        }
    }

    private fun parseDnsQueryV4(ipPacket: ByteArray, length: Int): DnsQueryPacket? {
        if (length < 20) return null
        val ihl = (ipPacket[0].toInt() and 0x0F) * 4
        if (ihl < 20) return null
        if (length < ihl + 8) return null
        val protocol = ipPacket[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP

        val udpOffset = ihl
        val dstPort = ((ipPacket[udpOffset + 2].toInt() and 0xFF) shl 8) or (ipPacket[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLen = ((ipPacket[udpOffset + 4].toInt() and 0xFF) shl 8) or (ipPacket[udpOffset + 5].toInt() and 0xFF)
        val dnsOffset = udpOffset + 8
        val dnsLen = (udpLen - 8).coerceAtLeast(0)
        if (dnsLen < 12) return null
        if (dnsOffset + dnsLen > length) return null

        val dns = ipPacket.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        val (domain, qType) = parseQuestion(dns)
        return DnsQueryPacket(domain, qType, dns, ipVersion = 4, ipHeaderLen = ihl, udpHeaderOffset = udpOffset)
    }

    /**
     * IPv6 notes:
     * - Supports common extension-header chains (hop-by-hop, routing, destination options, fragment).
     * - If we cannot safely walk the chain, return null (caller fail-open behavior).
     */
    private fun parseDnsQueryV6(ipPacket: ByteArray, length: Int): DnsQueryPacket? {
        if (length < 40 + 8) return null

        val udpOffset = findIpv6UdpHeaderOffset(ipPacket, length) ?: return null
        if (udpOffset + 8 > length) return null

        val dstPort = ((ipPacket[udpOffset + 2].toInt() and 0xFF) shl 8) or (ipPacket[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLen = ((ipPacket[udpOffset + 4].toInt() and 0xFF) shl 8) or (ipPacket[udpOffset + 5].toInt() and 0xFF)
        val dnsOffset = udpOffset + 8
        val dnsLen = (udpLen - 8).coerceAtLeast(0)
        if (dnsLen < 12) return null
        if (dnsOffset + dnsLen > length) return null

        val dns = ipPacket.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        val (domain, qType) = parseQuestion(dns)
        return DnsQueryPacket(domain, qType, dns, ipVersion = 6, ipHeaderLen = udpOffset, udpHeaderOffset = udpOffset)
    }

    private fun findIpv6UdpHeaderOffset(ipPacket: ByteArray, length: Int): Int? {
        var nextHeader = ipPacket[6].toInt() and 0xFF
        var offset = 40

        while (true) {
            when (nextHeader) {
                17 -> return offset // UDP
                0, 43, 60 -> {
                    if (offset + 2 > length) return null
                    val extLen = ((ipPacket[offset + 1].toInt() and 0xFF) + 1) * 8
                    if (extLen <= 0 || offset + extLen > length) return null
                    nextHeader = ipPacket[offset].toInt() and 0xFF
                    offset += extLen
                }
                44 -> {
                    // Fragment header is fixed 8 bytes.
                    if (offset + 8 > length) return null
                    nextHeader = ipPacket[offset].toInt() and 0xFF
                    offset += 8
                }
                else -> return null
            }
        }
    }

    private fun parseQuestion(dns: ByteArray): Pair<String?, Int> {
        // DNS header is 12 bytes.
        if (dns.size < 12) return null to 0
        var i = 12
        val labels = mutableListOf<String>()
        // Parse first question QNAME only.
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) break

            // If compression pointer appears, treat as unparseable (fail-open).
            if ((len and 0xC0) != 0) return null to 0
            if (len > 63) return null to 0
            if (i + 1 + len > dns.size) return null to 0

            val label = dns.copyOfRange(i + 1, i + 1 + len).toString(Charsets.US_ASCII)
            labels += label
            i += 1 + len
        }
        
        // After QNAME (ending with 0 byte at i), we need 4 bytes: QTYPE (2) + QCLASS (2).
        // i points to the 0 byte.
        val qTypeIndex = i + 1
        if (qTypeIndex + 4 > dns.size) return null to 0
        
        val qType = ((dns[qTypeIndex].toInt() and 0xFF) shl 8) or (dns[qTypeIndex + 1].toInt() and 0xFF)

        if (labels.isEmpty()) return null to qType
        return labels.joinToString(".") to qType
    }

    /**
     * Build an IP+UDP response packet suitable for writing back to the VPN TUN.
     * We keep checksums at 0 for simplicity.
     */
    fun buildUdpIpResponse(requestIpPacket: ByteArray, requestLen: Int, dnsPayload: ByteArray): ByteArray {
        val version = (requestIpPacket[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> buildUdpIpResponseV4(requestIpPacket, requestLen, dnsPayload)
            6 -> buildUdpIpResponseV6(requestIpPacket, requestLen, dnsPayload)
            else -> dnsPayload // should never happen; caller should have parsed first
        }
    }

    private fun buildUdpIpResponseV4(requestIpPacket: ByteArray, requestLen: Int, dnsPayload: ByteArray): ByteArray {
        val ihl = (requestIpPacket[0].toInt() and 0x0F) * 4
        val udpHeader = 8
        val totalLen = ihl + udpHeader + dnsPayload.size
        val out = ByteArray(totalLen)

        System.arraycopy(requestIpPacket, 0, out, 0, ihl + udpHeader)

        // swap IP src/dst
        for (i in 0..3) {
            out[12 + i] = requestIpPacket[16 + i]
            out[16 + i] = requestIpPacket[12 + i]
        }
        // swap UDP src/dst
        out[ihl] = requestIpPacket[ihl + 2]
        out[ihl + 1] = requestIpPacket[ihl + 3]
        out[ihl + 2] = requestIpPacket[ihl]
        out[ihl + 3] = requestIpPacket[ihl + 1]

        // total length
        out[2] = ((totalLen ushr 8) and 0xFF).toByte()
        out[3] = (totalLen and 0xFF).toByte()

        // UDP length
        val udpLen = udpHeader + dnsPayload.size
        out[ihl + 4] = ((udpLen ushr 8) and 0xFF).toByte()
        out[ihl + 5] = (udpLen and 0xFF).toByte()

        // IP ID: Use a different ID to avoid being treated as a duplicate/fragment by some stacks
        val oldId = ((requestIpPacket[4].toInt() and 0xFF) shl 8) or (requestIpPacket[5].toInt() and 0xFF)
        val newId = (oldId + 1) and 0xFFFF
        out[4] = (newId ushr 8).toByte()
        out[5] = (newId and 0xFF).toByte()

        // Compute IPv4 Checksum
        out[10] = 0; out[11] = 0
        val cksum = computeChecksum(out, 0, ihl)
        out[10] = (cksum ushr 8).toByte()
        out[11] = (cksum and 0xFF).toByte()

        // UDP Checksum: Keep at 0 for IPv4 (optional)
        out[ihl + 6] = 0; out[ihl + 7] = 0

        System.arraycopy(dnsPayload, 0, out, ihl + udpHeader, dnsPayload.size)
        return out
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        var len = length
        while (len > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            len -= 2
        }
        if (len > 0) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun buildUdpIpResponseV6(requestIpPacket: ByteArray, requestLen: Int, dnsPayload: ByteArray): ByteArray {
        // Use the actual offsets from the parsed packet instead of hardcoded 40
        val version = (requestIpPacket[0].toInt() ushr 4) and 0x0F
        // Safety check: if we're here and it's not v6, something is wrong
        if (version != 6) return dnsPayload

        // We need to know where the UDP header was in the request
        // Since we don't have the DnsQueryPacket object here easily without changing API,
        // we re-find the offset or we use a smarter copy.
        // For stability, let's re-calculate it or find a safe way.
        
        // Let's re-read the header chain to find the actual UDP offset
        var nextHeader = requestIpPacket[6].toInt() and 0xFF
        var udpOffset = 40
        while (udpOffset < requestLen) {
            if (nextHeader == 17) break // UDP found
            if (nextHeader == 0 || nextHeader == 43 || nextHeader == 60) {
                val extLen = ((requestIpPacket[udpOffset + 1].toInt() and 0xFF) + 1) * 8
                nextHeader = requestIpPacket[udpOffset].toInt() and 0xFF
                udpOffset += extLen
            } else if (nextHeader == 44) {
                nextHeader = requestIpPacket[udpOffset].toInt() and 0xFF
                udpOffset += 8
            } else {
                return dnsPayload // Unknown header, fail-open
            }
        }

        val udpHeader = 8
        val payloadLen = udpHeader + dnsPayload.size
        val totalLen = udpOffset + payloadLen
        val out = ByteArray(totalLen)

        // Copy everything up to the end of the UDP header
        System.arraycopy(requestIpPacket, 0, out, 0, udpOffset + udpHeader)

        // Swap IPv6 src/dst (fixed 16 bytes each, starting at offset 8 and 24)
        for (i in 0 until 16) {
            out[8 + i] = requestIpPacket[24 + i]
            out[24 + i] = requestIpPacket[8 + i]
        }

        // IPv6 payload length (bytes 4..5) covers everything AFTER the 40-byte fixed header
        val ipv6PayloadLen = totalLen - 40
        out[4] = ((ipv6PayloadLen ushr 8) and 0xFF).toByte()
        out[5] = (ipv6PayloadLen and 0xFF).toByte()

        // swap UDP src/dst
        out[udpOffset] = requestIpPacket[udpOffset + 2]
        out[udpOffset + 1] = requestIpPacket[udpOffset + 3]
        out[udpOffset + 2] = requestIpPacket[udpOffset]
        out[udpOffset + 3] = requestIpPacket[udpOffset + 1]

        // UDP length
        out[udpOffset + 4] = ((payloadLen ushr 8) and 0xFF).toByte()
        out[udpOffset + 5] = (payloadLen and 0xFF).toByte()

        System.arraycopy(dnsPayload, 0, out, udpOffset + udpHeader, dnsPayload.size)

        // Compute IPv6 UDP Checksum (Mandatory)
        out[udpOffset + 6] = 0; out[udpOffset + 7] = 0
        val cksum = computeUdpChecksumV6(out, udpOffset, payloadLen)
        out[udpOffset + 6] = (cksum ushr 8).toByte()
        out[udpOffset + 7] = (cksum and 0xFF).toByte()

        return out
    }

    private fun computeUdpChecksumV6(packet: ByteArray, udpOffset: Int, udpLen: Int): Int {
        var sum = 0
        // Pseudo-header: Src IP (16 bytes)
        for (i in 0 until 8) {
            sum += ((packet[24 + i * 2].toInt() and 0xFF) shl 8) or (packet[24 + i * 2 + 1].toInt() and 0xFF)
        }
        // Pseudo-header: Dst IP (16 bytes)
        for (i in 0 until 8) {
            sum += ((packet[8 + i * 2].toInt() and 0xFF) shl 8) or (packet[8 + i * 2 + 1].toInt() and 0xFF)
        }
        // Pseudo-header: UDP Length (4 bytes)
        sum += (udpLen ushr 16) and 0xFFFF
        sum += udpLen and 0xFFFF
        // Pseudo-header: Next Header (17 for UDP)
        sum += 17

        // UDP Header + Payload
        var i = udpOffset
        var len = udpLen
        while (len > 1) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
            len -= 2
        }
        if (len > 0) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val res = (sum.inv()) and 0xFFFF
        return if (res == 0) 0xFFFF else res
    }
}
