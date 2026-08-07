package com.phonesync.app.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phonesync.app.BuildConfig
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.sync.SyncWorker
import com.phonesync.app.ui.components.BrandMark
import com.phonesync.app.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun PairingScreen(
    repository: PhotoSyncRepository,
    allowCellular: Boolean,
    syncIntervalMinutes: Int,
    onPaired: () -> Unit,
) {
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8787") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lanWarning by remember { derivedStateOf { lanWarningFor(serverUrl) } }

    fun doPair() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                repository.pair(serverUrl.trim(), pin.trim())
                SyncWorker.enqueuePeriodic(context, syncIntervalMinutes, allowCellular)
            }.onSuccess {
                loading = false
                onPaired()
            }.onFailure {
                loading = false
                error = it.message ?: "Pairing failed"
            }
        }
    }

    // Debug-only: adb shell am start ... --es demo_pin 123456 --ez demo_pair true
    // or --es demo_pin 000000 to show the error state without flaky emulator taps.
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val extras = activity.intent?.extras ?: return@LaunchedEffect
        val demoUrl = extras.getString("demo_url")
        val demoPin = extras.getString("demo_pin")
        if (demoUrl != null) serverUrl = demoUrl
        if (demoPin != null) pin = demoPin
        if (extras.getBoolean("demo_pair", false)) {
            val url = (demoUrl ?: serverUrl).trim()
            val p = (demoPin ?: pin).trim()
            if (url.isBlank() || p.isBlank()) return@LaunchedEffect
            loading = true
            error = null
            runCatching {
                repository.pair(url, p)
                SyncWorker.enqueuePeriodic(context, syncIntervalMinutes, allowCellular)
            }.onSuccess {
                loading = false
                onPaired()
            }.onFailure {
                loading = false
                error = it.message ?: "Pairing failed"
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(icon = Icons.Default.PhotoCamera, size = 76)
            Spacer(Modifier.height(22.dp))
            Text(
                "Hello,",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Photo Sync",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pair with your PC on the same Wi-Fi.\nEnter the server URL and PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            SectionCard {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("http://10.0.2.2:8787") },
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                if (lanWarning != null) {
                    Text(
                        lanWarning!!,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Pairing PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = !loading && pin.isNotBlank() && serverUrl.isNotBlank(),
                    onClick = { doPair() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = CircleShape,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            "Pair device",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Network Security Config (res/xml/network_security_config.xml) permits cleartext traffic
 * globally because it can only match domains, not IP/CIDR ranges — and the server URL here is
 * an arbitrary user-entered LAN IP, so there's no way to scope it more tightly there. As
 * defense in depth, warn (non-blocking) if the entered host doesn't look like a private/LAN
 * address, so a user is less likely to accidentally paste in a public address and send
 * pairing credentials over plaintext HTTP across the open internet.
 */
private fun lanWarningFor(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    val host = runCatching { URI(trimmed).host }.getOrNull() ?: return null
    if (looksLikePrivateHost(host)) return null
    return "This doesn't look like a local network address. Only pair with a PC on your own Wi-Fi/LAN."
}

private fun looksLikePrivateHost(host: String): Boolean {
    if (host.equals("localhost", ignoreCase = true)) return true
    if (host.endsWith(".local", ignoreCase = true)) return true
    if (host == "10.0.2.2") return true // Android emulator alias for host loopback

    val octets = host.split(".").mapNotNull { it.toIntOrNull() }
    if (octets.size == 4 && octets.all { it in 0..255 }) {
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 169 && octets[1] == 254 -> true // link-local
            octets[0] == 127 -> true // loopback
            else -> false
        }
    }
    return false
}
