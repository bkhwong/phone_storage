package com.phonesync.app.ui.pairing

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PairingViewModelTest {

    // ---- looksLikePrivateHost ---------------------------------------------------------

    @Test
    fun looksLikePrivateHost_class10_isPrivate() {
        assertTrue(looksLikePrivateHost("10.1.2.3"))
    }

    @Test
    fun looksLikePrivateHost_class192_168_isPrivate() {
        assertTrue(looksLikePrivateHost("192.168.1.42"))
    }

    @Test
    fun looksLikePrivateHost_class172_16to31_isPrivate() {
        assertTrue(looksLikePrivateHost("172.16.0.1"))
        assertTrue(looksLikePrivateHost("172.31.255.255"))
    }

    @Test
    fun looksLikePrivateHost_class172_outsideRange_isNotPrivate() {
        assertTrue(!looksLikePrivateHost("172.32.0.1"))
        assertTrue(!looksLikePrivateHost("172.15.0.1"))
    }

    @Test
    fun looksLikePrivateHost_loopback_isPrivate() {
        assertTrue(looksLikePrivateHost("127.0.0.1"))
    }

    @Test
    fun looksLikePrivateHost_linkLocal_isPrivate() {
        assertTrue(looksLikePrivateHost("169.254.1.1"))
    }

    @Test
    fun looksLikePrivateHost_emulatorAlias_isPrivate() {
        assertTrue(looksLikePrivateHost("10.0.2.2"))
    }

    @Test
    fun looksLikePrivateHost_dotLocal_isPrivate() {
        assertTrue(looksLikePrivateHost("my-pc.local"))
    }

    @Test
    fun looksLikePrivateHost_publicIp_isNotPrivate() {
        assertTrue(!looksLikePrivateHost("8.8.8.8"))
    }

    @Test
    fun looksLikePrivateHost_publicDomain_isNotPrivate() {
        assertTrue(!looksLikePrivateHost("example.com"))
    }

    // ---- lanWarningFor ------------------------------------------------------------------

    @Test
    fun lanWarningFor_blank_isNull() {
        assertNull(lanWarningFor(""))
        assertNull(lanWarningFor("   "))
    }

    @Test
    fun lanWarningFor_privateAddress_isNull() {
        assertNull(lanWarningFor("192.168.1.50:8787"))
        assertNull(lanWarningFor("http://192.168.1.50:8787"))
    }

    @Test
    fun lanWarningFor_publicAddress_warns() {
        assertTrue(lanWarningFor("8.8.8.8") != null)
        assertTrue(lanWarningFor("http://example.com") != null)
    }

    // ---- PairingUiState.canSubmit ---------------------------------------------------------

    @Test
    fun canSubmit_requiresUrlAndPin() {
        assertTrue(!PairingUiState(serverUrl = "", pin = "123456").canSubmit)
        assertTrue(!PairingUiState(serverUrl = "1.2.3.4", pin = "").canSubmit)
        assertTrue(PairingUiState(serverUrl = "1.2.3.4", pin = "123456").canSubmit)
    }

    @Test
    fun canSubmit_falseWhileLoading() {
        assertTrue(!PairingUiState(serverUrl = "1.2.3.4", pin = "123456", loading = true).canSubmit)
    }

    // ---- friendlyPairError ----------------------------------------------------------------

    @Test
    fun friendlyPairError_401_mapsToWrongPin() {
        val error = httpException(401)
        assertEquals(
            "Incorrect PIN. Check the code shown on your PC and try again.",
            friendlyPairError(error),
        )
    }

    @Test
    fun friendlyPairError_429_mapsToRateLimit() {
        val error = httpException(429)
        assertTrue(friendlyPairError(error).contains("Too many attempts"))
    }

    @Test
    fun friendlyPairError_409_mapsToAlreadyPaired() {
        val error = httpException(409)
        assertTrue(friendlyPairError(error).contains("already been paired"))
    }

    @Test
    fun friendlyPairError_genericException_usesMessage() {
        val error = IllegalStateException("boom")
        assertEquals("boom", friendlyPairError(error))
    }

    private fun httpException(code: Int): HttpException {
        val body = "".toResponseBody(null)
        return HttpException(Response.error<Any>(code, body))
    }
}
