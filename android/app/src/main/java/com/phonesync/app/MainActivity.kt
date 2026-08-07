package com.phonesync.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.phonesync.app.ui.archive.ArchiveScreen
import com.phonesync.app.ui.battery.BatteryGuidanceScreen
import com.phonesync.app.ui.browse.BrowseScreen
import com.phonesync.app.ui.migration.MigrationScreen
import com.phonesync.app.ui.nav.AppRoute
import com.phonesync.app.ui.pairing.PairingScreen
import com.phonesync.app.ui.settings.SettingsScreen
import com.phonesync.app.ui.status.StatusScreen
import com.phonesync.app.ui.theme.PhotoSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PhotoSyncApp
        setContent {
            PhotoSyncTheme {
                // Status/nav bar icon contrast must follow whichever mode the dynamic Material
                // You theme resolved to (isSystemInDarkTheme()), not a hardcoded value — otherwise
                // e.g. light mode would render dark status bar icons on a light status bar.
                val darkTheme = isSystemInDarkTheme()
                val view = LocalView.current
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PhotoSyncRoot(app)
                }
            }
        }
    }
}

@Composable
private fun PhotoSyncRoot(app: PhotoSyncApp) {
    val prefs = app.container.prefs
    val repository = app.container.repository
    val navController = rememberNavController()
    val token by prefs.deviceToken.collectAsStateWithLifecycle(null)
    val interval by prefs.syncIntervalMinutes.collectAsStateWithLifecycle(60)
    val cellular by prefs.allowCellular.collectAsStateWithLifecycle(false)
    val batterySeen by prefs.batteryGuidanceSeen.collectAsStateWithLifecycle(false)
    val paired = !token.isNullOrBlank()
    val context = LocalContext.current
    var permissionsReady by remember { mutableStateOf(hasMediaPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Notifications are best-effort; only the media permissions are required to proceed,
        // and they must ALL be granted (a partial grant is not "ready").
        permissionsReady = hasMediaPermissions(context)
    }

    LaunchedEffect(Unit) {
        if (!permissionsReady) {
            // Request every needed permission in a single launch — issuing a second launch
            // call on the same launcher while the first is in flight cancels it.
            val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
            permissionLauncher.launch(requiredPermissions() + notificationPermission)
        }
    }

    LaunchedEffect(paired, permissionsReady) {
        if (paired && permissionsReady) {
            runCatching { repository.scanAndReconcileLocal() }
        }
    }

    val start = if (paired) AppRoute.Home.route else AppRoute.Pairing.route

    NavHost(navController = navController, startDestination = start) {
        composable(AppRoute.Pairing.route) {
            PairingScreen(
                repository = repository,
                allowCellular = cellular,
                syncIntervalMinutes = interval,
                onPaired = {
                    navController.navigate(AppRoute.Home.route) {
                        popUpTo(AppRoute.Pairing.route) { inclusive = true }
                    }
                    if (!batterySeen) {
                        navController.navigate(AppRoute.Battery.route)
                    }
                },
            )
        }
        composable(AppRoute.Home.route) {
            StatusScreen(
                repository = repository,
                prefs = prefs,
                onArchive = { navController.navigate(AppRoute.Archive.route) },
                onBrowse = { navController.navigate(AppRoute.Browse.route) },
                onMigration = { navController.navigate(AppRoute.Migration.route) },
                onSettings = { navController.navigate(AppRoute.Settings.route) },
            )
        }
        composable(AppRoute.Archive.route) {
            ArchiveScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Browse.route) {
            BrowseScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Migration.route) {
            MigrationScreen(
                repository = repository,
                prefs = prefs,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                repository = repository,
                prefs = prefs,
                onBatteryGuidance = { navController.navigate(AppRoute.Battery.route) },
                onUnpaired = {
                    navController.navigate(AppRoute.Pairing.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Battery.route) {
            BatteryGuidanceScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasMediaPermissions(context: android.content.Context): Boolean {
    return requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
