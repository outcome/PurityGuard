package com.purityguard.app

import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPageRendererTest {
    @Test
    fun generatesHtmlWithDomain() {
        val html = BlockPageRenderer.html("example.com", InspirationMode.NONE, "")
        assertTrue(html.contains("example.com"))
        assertTrue(html.contains("Access blocked"))
    }

    @Test
    fun includesInspirationWhenEnabled() {
        // Mode BIBLE should produce some text
        val html = BlockPageRenderer.html("example.com", InspirationMode.BIBLE, "")
        assertTrue(html.contains("class=\"inspiration\""))
    }

    @Test
    fun handlesHtmlEscaping() {
        val html = BlockPageRenderer.html("<script>alert(1)</script>.com", InspirationMode.NONE, "")
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(!html.contains("<script>"))
    }
}
