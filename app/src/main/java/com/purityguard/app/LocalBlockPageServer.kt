package com.purityguard.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class LocalBlockPageServer(
    private val context: Context,
    private val port: Int = 80,
    private val inspirationModeProvider: () -> InspirationMode = { InspirationMode.NONE },
    private val donationUrlProvider: () -> String = { "" }
) {
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    fun start() {
        if (running) return
        running = true

        serverThread = thread(name = "purity-block-page", isDaemon = true) {
            try {
                serverSocket = ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
                while (running) {
                    val client = try { serverSocket?.accept() } catch (_: Exception) { null } ?: continue
                    handleClient(client)
                }
            } catch (_: Exception) {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
    }

    private fun handleClient(client: Socket) {
        thread(name = "purity-block-page-client", isDaemon = true) {
            client.use { socket ->
                socket.soTimeout = 1500
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))

                val requestLine = reader.readLine() ?: return@use
                var hostHeader: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    if (line.startsWith("Host:", ignoreCase = true)) hostHeader = line.substringAfter(':').trim()
                }

                val path = requestLine.split(' ').getOrNull(1).orEmpty()
                when {
                    path.startsWith("/assets/purityguardlogo.png") -> writeAsset(socket, "purityguardlogo.png")
                    path.startsWith("/assets/buymeacoffeelogo.png") -> writeAsset(socket, "buymeacoffeelogo.png")
                    else -> writeHtml(socket, path, hostHeader)
                }
            }
        }
    }

    private fun writeHtml(socket: Socket, path: String, hostHeader: String?) {
        val blockedDomain = extractDomain(path, hostHeader)
        val html = BlockPageRenderer.html(blockedDomain, inspirationModeProvider(), donationUrlProvider())
        val body = html.toByteArray(StandardCharsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("Pragma: no-cache\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${body.size}\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        socket.getOutputStream().write(response)
        socket.getOutputStream().write(body)
        socket.getOutputStream().flush()
    }

    private fun writeAsset(socket: Socket, assetName: String) {
        val body = try { context.assets.open(assetName).use { it.readBytes() } } catch (_: Exception) { ByteArray(0) }
        if (body.isEmpty()) {
            socket.getOutputStream().write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            return
        }
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: image/png\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${body.size}\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        socket.getOutputStream().write(header)
        socket.getOutputStream().write(body)
        socket.getOutputStream().flush()
    }

    private fun extractDomain(path: String, hostHeader: String?): String {
        val fromQuery = path.substringAfter("?", "")
            .split('&')
            .mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == "domain") parts[1] else null
            }
            .firstOrNull()
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }

        val fromHost = hostHeader?.substringBefore(':')?.takeUnless { it == "127.0.0.1" || it.equals("localhost", true) }
        return fromQuery ?: fromHost ?: "this domain"
    }
}
