package com.purityguard.app

object BlockPageRenderer {
    fun html(domain: String, inspirationMode: InspirationMode, donationUrl: String): String {
        val safeDomain = escapeHtml(domain)
        val inspiration = inspirationHtml(inspirationMode)

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Blocked by PurityGuard</title>
              <style>
                :root { color-scheme: dark; }
                * { box-sizing: border-box; }
                body {
                  margin:0;
                  min-height:100vh;
                  display:grid;
                  place-items:center;
                  background: radial-gradient(circle at 20% 0%, #151515 0%, #090909 36%, #000 100%);
                  color:#f5f5f5;
                  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
                }
                .card {
                  width:min(94vw,620px);
                  border:1px solid #242424;
                  border-radius:18px;
                  padding:30px;
                  background:linear-gradient(180deg,#0f0f0f 0%,#070707 100%);
                  box-shadow: 0 18px 46px rgba(0,0,0,.55);
                }
                .logo { width:68px; height:68px; margin-bottom:12px; }
                h1 { margin:0 0 8px; font-size:30px; letter-spacing:.01em; }
                p { margin:0 0 14px; color:#cbcbcb; line-height:1.55; }
                .domain {
                  display:inline-block;
                  margin:4px 0 18px;
                  font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
                  color:#fff;
                  background:#121212;
                  border:1px solid #2f2f2f;
                  border-radius:999px;
                  padding:8px 14px;
                }
                .hint { margin-top:14px; font-size:13px; color:#8f8f8f; }
                .inspiration {
                  margin-top:16px;
                  padding:14px;
                  border-radius:12px;
                  border:1px solid #2b2b2b;
                  background:#0f0f0f;
                }
                .inspiration .title { font-size:12px; letter-spacing:.08em; color:#acacac; text-transform:uppercase; margin-bottom:8px; }
                .inspiration p { margin:0; white-space:normal; overflow-wrap:anywhere; word-break:break-word; line-height:1.55; }
              </style>
            </head>
            <body>
              <main class="card">
                <img class="logo" src="/assets/purityguardlogo.png" alt="PurityGuard logo" />
                <h1>Access blocked</h1>
                <p>This domain is blocked by your PurityGuard protection settings.</p>
                <div class="domain">$safeDomain</div>
                $inspiration
                <p class="hint">Local page generated on-device. No external resources loaded.</p>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun inspirationHtml(mode: InspirationMode): String {
        val verse = MotivationVerses.pick(mode) ?: return ""
        val safeVerse = escapeHtml(verse)
        return """
            <section class="inspiration">
              <div class="title">Inspiration</div>
              <p>$safeVerse</p>
            </section>
        """.trimIndent()
    }

    private fun donationHtml(donationUrl: String): String {
        val safeUrl = escapeAttribute(donationUrl.trim().ifBlank { SettingsStore.DEFAULT_DONATION_URL })
        return """
            <section class="donation">
              <a href="$safeUrl" target="_blank" rel="noopener noreferrer" aria-label="Buy Me a Coffee">
                <img src="/assets/buymeacoffeelogo.png" alt="Buy Me a Coffee" />
              </a>
            </section>
        """.trimIndent()
    }

    private fun escapeHtml(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private fun escapeAttribute(input: String): String = escapeHtml(input)
}
