package com.purityguard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketParsersTest {
    @Test
    fun parseDnsQuery_extractsDomain() {
        val dns = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x07, 'x'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'd'.code.toByte(), 'e'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00, 0x00, 0x01, 0x00, 0x01
        )

        val ihl = 20
        val ip = ByteArray(ihl + 8 + dns.size)
        ip[0] = 0x45
        ip[9] = 17 // UDP
        ip[12] = 10; ip[13] = 8; ip[14] = 0; ip[15] = 2
        ip[16] = 1; ip[17] = 1; ip[18] = 1; ip[19] = 1
        ip[20] = 0x30; ip[21] = 0x39 // src port 12345
        ip[22] = 0x00; ip[23] = 0x35 // dst port 53
        val udpLen = 8 + dns.size
        ip[24] = ((udpLen ushr 8) and 0xFF).toByte()
        ip[25] = (udpLen and 0xFF).toByte()
        dns.copyInto(ip, 28)

        val parsed = PacketParsers.parseDnsQuery(ip, ip.size)
        assertNotNull(parsed)
        assertEquals("xvideos.com", parsed!!.domain)
        assertEquals(4, parsed.ipVersion)
    }

    @Test
    fun parseDnsQuery_failOpen_returnsPacketEvenIfQnameUnparseable() {
        // DNS header + a broken QNAME (length says 10 but we only provide 2 bytes)
        val dns = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x0A, 'b'.code.toByte(), 'a'.code.toByte(),
            0x00, 0x00, 0x01, 0x00, 0x01
        )

        val ihl = 20
        val ip = ByteArray(ihl + 8 + dns.size)
        ip[0] = 0x45
        ip[9] = 17 // UDP
        ip[12] = 10; ip[13] = 8; ip[14] = 0; ip[15] = 2
        ip[16] = 1; ip[17] = 1; ip[18] = 1; ip[19] = 1
        ip[20] = 0x30; ip[21] = 0x39
        ip[22] = 0x00; ip[23] = 0x35
        val udpLen = 8 + dns.size
        ip[24] = ((udpLen ushr 8) and 0xFF).toByte()
        ip[25] = (udpLen and 0xFF).toByte()
        dns.copyInto(ip, 28)

        val parsed = PacketParsers.parseDnsQuery(ip, ip.size)
        assertNotNull(parsed)
        assertNull(parsed!!.domain)
        assertEquals(dns.size, parsed.rawDnsPayload.size)
    }

    @Test
    fun parseDnsQuery_ipv6WithHopByHopHeader_isStillParsed() {
        val dns = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x03, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            0x06, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00, 0x00, 0x01, 0x00, 0x01
        )

        val hopByHop = byteArrayOf(
            17, // next header = UDP
            0,  // hdr ext len = 0 => 8 bytes total
            0, 0, 0, 0, 0, 0
        )

        val udpLen = 8 + dns.size
        val packet = ByteArray(40 + hopByHop.size + udpLen)
        packet[0] = 0x60 // IPv6
        packet[6] = 0 // next header = hop-by-hop options
        val payloadLen = hopByHop.size + udpLen
        packet[4] = ((payloadLen ushr 8) and 0xFF).toByte()
        packet[5] = (payloadLen and 0xFF).toByte()

        hopByHop.copyInto(packet, 40)
        val udpOffset = 48
        packet[udpOffset] = 0x30; packet[udpOffset + 1] = 0x39 // src port 12345
        packet[udpOffset + 2] = 0x00; packet[udpOffset + 3] = 0x35 // dst port 53
        packet[udpOffset + 4] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLen and 0xFF).toByte()

        dns.copyInto(packet, udpOffset + 8)

        val parsed = PacketParsers.parseDnsQuery(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(6, parsed!!.ipVersion)
        assertEquals("www.google.com", parsed.domain)
        assertTrue(parsed.udpHeaderOffset > 40)
    }
}
