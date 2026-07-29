package com.phonesync.app.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phonesync.app.BuildConfig
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.sync.SyncWorker
import com.phonesync.app.ui.components.BrandMark
import com.phonesync.app.ui.components.GlassPanel
import com.phonesync.app.ui.components.GlassScene
import com.phonesync.app.ui.theme.JakeGray
import com.phonesync.app.ui.theme.JakeInk
import com.phonesync.app.ui.theme.JakeWhite
import com.phonesync.app.ui.theme.JakeYellow
import kotlinx.coroutines.launch

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

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = JakeYellow.copy(alpha = 0.7f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
        focusedLabelColor = JakeYellow,
        unfocusedLabelColor = JakeGray,
        cursorColor = JakeYellow,
        focusedTextColor = JakeWhite,
        unfocusedTextColor = JakeWhite,
        focusedContainerColor = Color.White.copy(alpha = 0.06f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
    )

    GlassScene {
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
                "HELLO,",
                style = MaterialTheme.typography.titleMedium,
                color = JakeWhite,
            )
            Text(
                "PHOTO SYNC.",
                style = MaterialTheme.typography.headlineLarge,
                color = JakeYellow,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "PAIR WITH YOUR PC ON THE SAME WI‑FI.\nENTER THE SERVER URL AND PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = JakeGray,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            GlassPanel {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("http://10.0.2.2:8787") },
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Pairing PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors,
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
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JakeYellow,
                        contentColor = JakeInk,
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledContentColor = JakeGray,
                    ),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.dp,
                            color = JakeInk,
                        )
                    } else {
                        Text(
                            "PAIR DEVICE",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
