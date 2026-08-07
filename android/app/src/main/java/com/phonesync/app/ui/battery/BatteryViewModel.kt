package com.phonesync.app.ui.battery

import androidx.lifecycle.ViewModel
import com.phonesync.app.data.prefs.SecurePrefs

class BatteryViewModel(private val prefs: SecurePrefs) : ViewModel() {
    fun markSeen() = prefs.setBatteryGuidanceSeen(true)
}
