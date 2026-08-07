package com.phonesync.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * An InputStream wrapping [data] whose [skip] never advances by more than [maxSkipPerCall]
 * bytes in one call (simulating a real ContentResolver stream that can under-skip), and can
 * optionally always return 0 from [skip] to force the read-and-discard fallback path.
 */
private class ThrottledSkipInputStream(
    data: ByteArray,
    private val maxSkipPerCall: Long,
    private val skipAlwaysZero: Boolean = false,
) : InputStream() {
    private val delegate = ByteArrayInputStream(data)

    override fun read(): Int = delegate.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

    override fun skip(n: Long): Long {
        if (skipAlwaysZero) return 0
        val actual = minOf(n, maxSkipPerCall)
        return delegate.skip(actual)
    }
}

class SkipFullyTest {
    private fun bytesOf(size: Int): ByteArray = ByteArray(size) { (it % 256).toByte() }

    @Test
    fun skipFully_singleCallSkip_worksNormally() {
        val data = bytesOf(100)
        val stream = ByteArrayInputStream(data)
        skipFully(stream, 40)
        assertEquals(data[40].toInt() and 0xFF, stream.read())
    }

    @Test
    fun skipFully_underSkippingStream_stillSkipsExactAmount() {
        val data = bytesOf(1000)
        // skip() can only advance 10 bytes per call, forcing many loop iterations.
        val stream = ThrottledSkipInputStream(data, maxSkipPerCall = 10)
        skipFully(stream, 733)
        assertEquals(data[733].toInt() and 0xFF, stream.read())
    }

    @Test
    fun skipFully_skipAlwaysReturnsZero_fallsBackToReadingAndDiscarding() {
        val data = bytesOf(500)
        val stream = ThrottledSkipInputStream(data, maxSkipPerCall = 0, skipAlwaysZero = true)
        skipFully(stream, 250)
        assertEquals(data[250].toInt() and 0xFF, stream.read())
    }

    @Test
    fun skipFully_zeroByteCount_isNoOp() {
        val data = bytesOf(10)
        val stream = ByteArrayInputStream(data)
        skipFully(stream, 0)
        assertEquals(data[0].toInt() and 0xFF, stream.read())
    }

    @Test
    fun skipFully_streamEndsBeforeRequestedAmount_throwsEof() {
        val data = bytesOf(10)
        val stream = ByteArrayInputStream(data)
        assertThrows(EOFException::class.java) {
            skipFully(stream, 100)
        }
    }
}
