package com.purityguard.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.URL

class BlocklistUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val urls = listOf(
            "https://raw.githubusercontent.com/blocklistproject/Lists/master/porn.txt",
            "https://raw.githubusercontent.com/4skinSkywalker/Anti-Porn-HOSTS-File/master/HOSTS.txt"
        )
        return try {
            val merged = mutableSetOf<String>()
            urls.forEach { u ->
                val text = URL(u).readText()
                text.lineSequence()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .forEach { line ->
                        val domain = line.substringAfter("0.0.0.0 ", line)
                            .substringAfter("127.0.0.1 ", line)
                            .trim()
                        if (domain.matches(Regex("^[a-z0-9.-]+$"))) merged.add(domain)
                    }
            }
            applicationContext.openFileOutput("dynamic_blocklist.txt", Context.MODE_PRIVATE)
                .bufferedWriter().use { writer ->
                    merged.sorted().forEach { writer.appendLine(it) }
                }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
