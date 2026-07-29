package com.phonesync.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {
    @Test
    fun useAppContext_packageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // debug builds use applicationIdSuffix ".debug"
        assertTrue(
            appContext.packageName == "com.phonesync.app" ||
                appContext.packageName == "com.phonesync.app.debug",
        )
    }

    @Test
    fun appResources_areReachable() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Photo Sync", appContext.getString(com.phonesync.app.R.string.app_name))
    }
}
