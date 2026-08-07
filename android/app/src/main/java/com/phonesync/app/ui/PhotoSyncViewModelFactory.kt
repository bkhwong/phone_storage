package com.phonesync.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.phonesync.app.AppContainer
import com.phonesync.app.ui.archive.ArchiveViewModel
import com.phonesync.app.ui.battery.BatteryViewModel
import com.phonesync.app.ui.browse.BrowseViewModel
import com.phonesync.app.ui.migration.MigrationViewModel
import com.phonesync.app.ui.pairing.PairingViewModel
import com.phonesync.app.ui.settings.SettingsViewModel
import com.phonesync.app.ui.status.StatusViewModel

/**
 * Small hand-rolled factory instead of Hilt/Koin, consistent with [AppContainer]'s existing
 * manual service-locator style DI — every screen's ViewModel is built from the same singletons.
 */
class PhotoSyncViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when (modelClass) {
            StatusViewModel::class.java -> StatusViewModel(container.repository)
            ArchiveViewModel::class.java -> ArchiveViewModel(container.repository)
            BrowseViewModel::class.java -> BrowseViewModel(container.repository)
            MigrationViewModel::class.java -> MigrationViewModel(container.repository, container.prefs)
            SettingsViewModel::class.java -> SettingsViewModel(container.repository, container.prefs)
            PairingViewModel::class.java -> PairingViewModel(container.repository)
            BatteryViewModel::class.java -> BatteryViewModel(container.prefs)
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
        return vm as T
    }
}
