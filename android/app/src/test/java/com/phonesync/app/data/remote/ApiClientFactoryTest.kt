package com.phonesync.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiClientFactoryTest {

    @Test
    fun normalizeBaseUrl_addsHttpScheme_whenMissing() {
        assertEquals("http://192.168.1.42:8787", normalizeBaseUrl("192.168.1.42:8787"))
    }

    @Test
    fun normalizeBaseUrl_preservesHttps() {
        assertEquals("https://example.com", normalizeBaseUrl("https://example.com"))
    }

    @Test
    fun normalizeBaseUrl_preservesExplicitHttp() {
        assertEquals("http://example.com", normalizeBaseUrl("http://example.com"))
    }

    @Test
    fun normalizeBaseUrl_isCaseInsensitiveForScheme() {
        assertEquals("HTTPS://example.com", normalizeBaseUrl("HTTPS://example.com"))
    }

    @Test
    fun normalizeBaseUrl_trimsWhitespace() {
        assertEquals("http://10.0.0.5:8787", normalizeBaseUrl("  10.0.0.5:8787  "))
    }

    @Test
    fun normalizeBaseUrl_stripsTrailingSlash() {
        assertEquals("http://10.0.0.5:8787", normalizeBaseUrl("http://10.0.0.5:8787/"))
    }

    @Test
    fun normalizeBaseUrl_stripsMultipleTrailingSlashes() {
        assertEquals("http://10.0.0.5:8787", normalizeBaseUrl("http://10.0.0.5:8787///"))
    }

    @Test
    fun normalizeBaseUrl_blankInput_staysBlank() {
        assertEquals("", normalizeBaseUrl("   "))
    }
}
