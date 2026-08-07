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
        assertEquals("ms_image_42", ClientAssetIds.forMediaStore(MediaKind.IMAGE, 42L))
        assertEquals("ms_video_0", ClientAssetIds.forMediaStore(MediaKind.VIDEO, 0L))
    }

    @Test
    fun clientAssetIds_forMediaStore_sameIdDifferentKindDoesNotCollide() {
        // MediaStore.Images and MediaStore.Video `_ID` columns are independent numeric
        // spaces, so the same numeric id can legitimately identify two different assets.
        // The client asset id must stay distinct across kinds to avoid dropping one of them.
        val imageId = ClientAssetIds.forMediaStore(MediaKind.IMAGE, 7L)
        val videoId = ClientAssetIds.forMediaStore(MediaKind.VIDEO, 7L)
        assertNotEquals(imageId, videoId)
    }
}
