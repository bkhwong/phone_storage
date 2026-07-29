package com.phonesync.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashUtilTest {
    @Test
    fun sha256Hex_knownVector() {
        // echo -n "hello" | sha256sum
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(expected, HashUtil.sha256Hex("hello".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun sha256Hex_empty() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, HashUtil.sha256Hex(ByteArray(0)))
    }

    @Test
    fun sha256Hex_differsForDifferentContent() {
        assertNotEquals(
            HashUtil.sha256Hex("a".toByteArray()),
            HashUtil.sha256Hex("b".toByteArray()),
        )
    }

    @Test
    fun clientAssetIds_forMediaStore() {
        assertEquals("ms_42", ClientAssetIds.forMediaStore(42L))
        assertEquals("ms_0", ClientAssetIds.forMediaStore(0L))
    }
}
