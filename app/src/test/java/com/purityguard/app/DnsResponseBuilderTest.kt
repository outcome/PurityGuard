package com.purityguard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResponseBuilderTest {
    @Test
    fun buildsNxdomainForAQuery() {
        val query = dnsQuery("example.com", qType = 1)
        val response = DnsResponseBuilder.buildBlockedResponse(query)

        // QR bit set, NXDOMAIN (3)
        assertEquals((0x81).toByte(), response[2])
        assertEquals((0x03).toByte(), response[3])

        // ANCOUNT = 0
        assertEquals(0x00.toByte(), response[6])
        assertEquals(0x00.toByte(), response[7])
    }

    @Test
    fun buildsNxdomainForAAAAQuery() {
        val query = dnsQuery("example.com", qType = 28) // AAAA
        val response = DnsResponseBuilder.buildBlockedResponse(query)

        // QR bit set, NXDOMAIN (3)
        assertEquals((0x81).toByte(), response[2])
        assertEquals((0x03).toByte(), response[3])

        // ANCOUNT = 0
        assertEquals(0x00.toByte(), response[6])
        assertEquals(0x00.toByte(), response[7])
    }

    private fun dnsQuery(domain: String, qType: Int): ByteArray {
        val labels = domain.split('.')
        val qnameBytes = mutableListOf<Byte>()
        labels.forEach { label ->
            qnameBytes += label.length.toByte()
            qnameBytes += label.toByteArray(Charsets.US_ASCII).toList()
        }
        qnameBytes += 0x00

        val out = mutableListOf<Byte>()
        out += 0x12
        out += 0x34
        out += 0x01
        out += 0x00
        out += 0x00
        out += 0x01
        out += 0x00
        out += 0x00
        out += 0x00
        out += 0x00
        out += 0x00
        out += 0x00
        out += qnameBytes
        out += ((qType ushr 8) and 0xFF).toByte()
        out += (qType and 0xFF).toByte()
        out += 0x00
        out += 0x01

        return out.toByteArray()
    }
}
