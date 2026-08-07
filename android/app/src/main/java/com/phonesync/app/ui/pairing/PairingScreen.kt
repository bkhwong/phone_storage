package com.phonesync.app.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonesync.app.BuildConfig
import com.phonesync.app.ui.PhotoSyncViewModelFactory
import com.phonesync.app.ui.components.BrandMark
import com.phonesync.app.ui.components.PillButton
import com.phonesync.app.ui.components.SectionCard

@Composable
fun PairingScreen(
    factory: PhotoSyncViewModelFactory,
    onPaired: () -> Unit,
) {
    val viewModel: PairingViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Debug-only: adb shell am start ... --es demo_pin 123456 --ez demo_pair true
    // or --es demo_pin 000000 to show the error state without flaky emulator taps.
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val extras = activity.intent?.extras ?: return@LaunchedEffect
        val demoUrl = extras.getString("demo_url")
        val demoPin = extras.getString("demo_pin")
        if (demoUrl != null || demoPin != null) viewModel.prefill(demoUrl, demoPin)
        if (extras.getBoolean("demo_pair", false)) {
            viewModel.pair(onPaired)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(icon = Icons.Default.CloudSync, size = 84)
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome to",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Photo Sync",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Pair with your PC on the same Wi-Fi to start backing up photos and videos automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            SectionCard {
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = viewModel::onServerUrlChange,
                    label = { Text("PC address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.42:8787") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                if (state.lanWarning != null) {
                    InlineNotice(icon = Icons.Default.WarningAmber, text = state.lanWarning!!, tone = NoticeTone.Warning)
                }
                OutlinedTextField(
                    value = state.pin,
                    onValueChange = viewModel::onPinChange,
                    label = { Text("Pairing PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                if (state.error != null) {
                    InlineNotice(icon = Icons.Default.ErrorOutline, text = state.error!!, tone = NoticeTone.Error)
                }
                Spacer(Modifier.height(4.dp))
                PillButton(
                    text = "Pair device",
                    onClick = { viewModel.pair(onPaired) },
                    enabled = state.canSubmit,
                    loading = state.loading,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Find this on your PC: open Photo Sync Server and look for \"Pairing PIN\" and the address shown under \"Connect from your phone\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private enum class NoticeTone { Warning, Error }

@Composable
private fun InlineNotice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tone: NoticeTone) {
    val color = when (tone) {
        NoticeTone.Warning -> MaterialTheme.colorScheme.tertiary
        NoticeTone.Error -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}
