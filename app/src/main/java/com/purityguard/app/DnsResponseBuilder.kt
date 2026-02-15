package com.purityguard.app

object DnsResponseBuilder {
    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28

    fun buildBlockedResponse(query: ByteArray): ByteArray {
        if (query.size < 12) return query

        // Reliability hardening: return NXDOMAIN for blocked domains instead of loopback answers.
        // This avoids browser "half-load" behavior where preconnected sockets briefly render content
        // before redirect UX catches up.
        return buildNxdomain(query)
    }

    private fun buildBlockedResponseA(query: ByteArray, questionEnd: Int): ByteArray {
        val response = mutableListOf<Byte>()
        // Header
        response += query[0]
        response += query[1]
        response += (query[2].toInt() or 0x80).toByte() // QR = response
        response += (query[3].toInt() and 0xF0).toByte() // RCODE = 0

        response += 0x00.toByte(); response += 0x01.toByte() // QDCOUNT
        response += 0x00.toByte(); response += 0x01.toByte() // ANCOUNT
        response += 0x00.toByte(); response += 0x00.toByte() // NSCOUNT
        response += 0x00.toByte(); response += 0x00.toByte() // ARCOUNT

        for (i in 12 until questionEnd) response += query[i]

        // Answer
        response += 0xC0.toByte(); response += 0x0C.toByte()
        response += 0x00.toByte(); response += 0x01.toByte() // TYPE = A
        response += 0x00.toByte(); response += 0x01.toByte() // CLASS = IN
        response += 0x00.toByte(); response += 0x00.toByte(); response += 0x00.toByte(); response += 0x1E.toByte() // TTL 30s
        response += 0x00.toByte(); response += 0x04.toByte() // LEN 4
        response += 127.toByte(); response += 0.toByte(); response += 0.toByte(); response += 1.toByte() // 127.0.0.1

        return response.toByteArray()
    }

    private fun buildBlockedResponseAAAA(query: ByteArray, questionEnd: Int): ByteArray {
        val response = mutableListOf<Byte>()
        // Header
        response += query[0]
        response += query[1]
        response += (query[2].toInt() or 0x80).toByte()
        response += (query[3].toInt() and 0xF0).toByte()

        response += 0x00.toByte(); response += 0x01.toByte()
        response += 0x00.toByte(); response += 0x01.toByte()
        response += 0x00.toByte(); response += 0x00.toByte()
        response += 0x00.toByte(); response += 0x00.toByte()

        for (i in 12 until questionEnd) response += query[i]

        // Answer
        response += 0xC0.toByte(); response += 0x0C.toByte()
        response += 0x00.toByte(); response += 0x1C.toByte() // TYPE = AAAA (28)
        response += 0x00.toByte(); response += 0x01.toByte() // CLASS = IN
        response += 0x00.toByte(); response += 0x00.toByte(); response += 0x00.toByte(); response += 0x1E.toByte()
        response += 0x00.toByte(); response += 0x10.toByte() // LEN 16
        // ::1 (16 bytes)
        for (i in 0..14) response += 0.toByte()
        response += 1.toByte()

        return response.toByteArray()
    }

    fun buildServfailResponse(query: ByteArray): ByteArray {
        val out = query.clone()
        if (out.size >= 4) {
            out[2] = (out[2].toInt() or 0x80).toByte()
            out[3] = (out[3].toInt() and 0xF0 or 0x02).toByte()
        }
        if (out.size >= 12) {
            out[6] = 0; out[7] = 0; out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0
        }
        return out
    }

    fun buildNxdomain(query: ByteArray): ByteArray {
        val out = query.clone()
        if (out.size >= 4) {
            out[2] = (out[2].toInt() or 0x80).toByte()
            out[3] = (out[3].toInt() and 0xF0 or 0x03).toByte()
        }
        if (out.size >= 12) {
            out[6] = 0; out[7] = 0; out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0
        }
        return out
    }

    private fun findQuestionEnd(query: ByteArray): Int? {
        var i = 12
        while (i < query.size) {
            val len = query[i].toInt() and 0xFF
            i += 1
            if (len == 0) return if (i + 4 <= query.size) i + 4 else null
            if (i + len > query.size) return null
            i += len
        }
        return null
    }
}
