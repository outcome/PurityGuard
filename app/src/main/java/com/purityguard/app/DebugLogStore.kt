package com.purityguard.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object DebugLogStore {
    private const val MAX_ENTRIES = 180
    private val entries = CopyOnWriteArrayList<String>()

    @Synchronized
    fun add(marker: String, details: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$ts [$marker] $details"
        android.util.Log.i("PurityGuard", "DEBUG_STORE: $line")
        entries.add(line)
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    fun snapshot(): List<String> = entries.toList()

    fun buildReport(): String = buildString {
        appendLine("PurityGuard Debug Report")
        appendLine("entries=${entries.size}")
        appendLine("---")
        snapshot().forEach { appendLine(it) }
    }
}
