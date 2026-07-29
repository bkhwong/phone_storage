package com.phonesync.app.ui.nav

sealed class AppRoute(val route: String) {
    data object Pairing : AppRoute("pairing")
    data object Home : AppRoute("home")
    data object Archive : AppRoute("archive")
    data object Browse : AppRoute("browse")
    data object Migration : AppRoute("migration")
    data object Settings : AppRoute("settings")
    data object Battery : AppRoute("battery")
}
