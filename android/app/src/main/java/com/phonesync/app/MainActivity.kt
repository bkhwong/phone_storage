package com.phonesync.app

import android.Manifest
import android.app.Activity
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.archive.ArchiveScreen
import com.phonesync.app.ui.battery.BatteryGuidanceScreen
import com.phonesync.app.ui.browse.BrowseScreen
import com.phonesync.app.ui.migration.MigrationScreen
import com.phonesync.app.ui.nav.AppRoute
import com.phonesync.app.ui.pairing.PairingScreen
import com.phonesync.app.ui.permissions.PermissionGateScreen
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
    val factory = remember { PhotoSyncViewModelFactory(app.container) }
    val navController = rememberNavController()
    val token by prefs.deviceToken.collectAsStateWithLifecycle(null)
    val batterySeen by prefs.batteryGuidanceSeen.collectAsStateWithLifecycle(false)
    val paired = !token.isNullOrBlank()
    val context = LocalContext.current
    var permissionsReady by remember { mutableStateOf(hasMediaPermissions(context)) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Notifications are best-effort; only the media permissions are required to proceed,
        // and they must ALL be granted (a partial grant is not "ready").
        hasRequestedPermission = true
        permissionsReady = hasMediaPermissions(context)
    }

    fun requestPermissions() {
        val notificationPermission = if (needsNotificationPermissionRequest(context)) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        // Request every needed permission in a single launch — issuing a second launch call
        // on the same launcher while the first is in flight cancels it.
        permissionLauncher.launch(requiredPermissions() + notificationPermission)
    }

    LaunchedEffect(Unit) {
        if (!permissionsReady) requestPermissions()
    }

    LaunchedEffect(paired, permissionsReady) {
        if (paired && permissionsReady) {
            runCatching { app.container.repository.scanAndReconcileLocal() }
        }
    }

    if (!permissionsReady) {
        val activity = context as? Activity
        val permanentlyDenied = hasRequestedPermission &&
            activity != null &&
            requiredPermissions().none { activity.shouldShowRequestPermissionRationale(it) }
        PermissionGateScreen(
            permanentlyDenied = permanentlyDenied,
            onRequestPermission = { requestPermissions() },
        )
        return
    }

    val start = if (paired) AppRoute.Home.route else AppRoute.Pairing.route

    NavHost(navController = navController, startDestination = start) {
        composable(AppRoute.Pairing.route) {
            PairingScreen(
                factory = factory,
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
                factory = factory,
                onArchive = { navController.navigate(AppRoute.Archive.route) },
                onBrowse = { navController.navigate(AppRoute.Browse.route) },
                onMigration = { navController.navigate(AppRoute.Migration.route) },
                onSettings = { navController.navigate(AppRoute.Settings.route) },
            )
        }
        composable(AppRoute.Archive.route) {
            ArchiveScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Browse.route) {
            BrowseScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Migration.route) {
            MigrationScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                factory = factory,
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
                factory = factory,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun requiredPermissions(): Array<String> {
    return when {
        // Android 14+ "Selected photos" partial access: requesting all three together is
        // the documented pattern for surfacing the system's "Allow all / Select photos and
        // videos / Don't allow" three-way choice instead of only a binary grant/deny.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasMediaPermissions(context: android.content.Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // A user who chose "Select photos and videos..." only grants
        // READ_MEDIA_VISUAL_USER_SELECTED, not the full READ_MEDIA_IMAGES/VIDEO pair —
        // MediaStoreScanner will transparently only see that selected subset, which is
        // correct behavior, not a denial to gate the whole app on.
        val fullAccess = granted(Manifest.permission.READ_MEDIA_IMAGES) &&
            granted(Manifest.permission.READ_MEDIA_VIDEO)
        val partialAccess = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return fullAccess || partialAccess
    }
    return requiredPermissions().all(::granted)
}

private fun needsNotificationPermissionRequest(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
}
