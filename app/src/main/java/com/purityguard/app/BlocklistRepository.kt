package com.purityguard.app

import android.content.Context
import android.util.Log

class BlocklistRepository(private val context: Context) {
    fun loadMergedList(): Set<String> {
        val merged = linkedSetOf<String>()

        // Base packaged blocklist.
        context.assets.open("blocklist_domains.txt").bufferedReader().useLines { lines ->
            lines.mapNotNull { sanitizeEntry(it) }.forEach { merged.add(it) }
        }

        // Optional user-provided bundled delta list (if present in assets).
        try {
            context.assets.open("user_blocklist_domains.txt").bufferedReader().useLines { lines ->
                lines.mapNotNull { sanitizeEntry(it) }.forEach { merged.add(it) }
            }
        } catch (_: Exception) {
            // Optional file not required.
        }

        // Dynamic file updates.
        val dynamic = try {
            context.openFileInput("dynamic_blocklist.txt").bufferedReader().useLines { lines ->
                lines.mapNotNull { sanitizeEntry(it) }.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        merged.addAll(dynamic)

        // User-added blocked domains from settings are debug-only while product policy is finalized.
        val extra = if (BuildConfig.DEBUG) {
            SettingsStore(context).getExtraBlockedDomains().mapNotNull { sanitizeEntry(it) }
        } else {
            emptyList()
        }
        merged.addAll(extra)

        Log.i("PurityGuard", "Blocklist loaded merged_count=${merged.size} (dynamic=${dynamic.size}, extra=${extra.size}, debug=${BuildConfig.DEBUG})")
        return merged
    }

    private fun sanitizeEntry(raw: String): String? {
        val line = raw.trim().lowercase().trim('.')
        if (line.isBlank() || line.startsWith("#")) return null
        if (!line.matches(Regex("^[a-z0-9.-]+$"))) return null

        val labels = line.split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return null
        if (labels.any { it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null

        return labels.joinToString(".")
    }
}
