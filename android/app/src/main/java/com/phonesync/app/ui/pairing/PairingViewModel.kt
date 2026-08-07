package com.phonesync.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonesync.app.data.repository.PhotoSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

data class PairingUiState(
    val serverUrl: String = "",
    val pin: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val lanWarning: String? = null,
) {
    val canSubmit: Boolean get() = !loading && serverUrl.isNotBlank() && pin.isNotBlank()
}

class PairingViewModel(private val repository: PhotoSyncRepository) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) {
        _state.update { it.copy(serverUrl = value, lanWarning = lanWarningFor(value), error = null) }
    }

    fun onPinChange(value: String) {
        _state.update { it.copy(pin = value, error = null) }
    }

    fun prefill(serverUrl: String?, pin: String?) {
        _state.update {
            it.copy(
                serverUrl = serverUrl ?: it.serverUrl,
                pin = pin ?: it.pin,
                lanWarning = lanWarningFor(serverUrl ?: it.serverUrl),
            )
        }
    }

    fun pair(onSuccess: () -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.pair(current.serverUrl.trim(), current.pin.trim()) }
                .onSuccess {
                    _state.update { it.copy(loading = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = friendlyPairError(e)) }
                }
        }
    }
}

/**
 * The Network Security Config (res/xml/network_security_config.xml) permits cleartext traffic
 * globally because it can only match domains, not IP/CIDR ranges — and the server URL here is
 * an arbitrary user-entered LAN address, so there's no way to scope it more tightly there. As
 * defense in depth, warn (non-blocking) if the entered host doesn't look like a private/LAN
 * address, so a user is less likely to accidentally send pairing credentials to a public
 * address over plaintext HTTP.
 */
fun lanWarningFor(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    val host = runCatching { URI(candidate).host }.getOrNull() ?: return null
    if (looksLikePrivateHost(host)) return null
    return "This doesn't look like a local network address. Only pair with a PC on your own Wi-Fi/LAN."
}

fun looksLikePrivateHost(host: String): Boolean {
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

fun friendlyPairError(e: Throwable): String = when {
    e is HttpException -> when (e.code()) {
        401 -> "Incorrect PIN. Check the code shown on your PC and try again."
        429 -> "Too many attempts. Wait a few minutes before trying again."
        409 -> "That PC has already been paired. Ask it to re-enable pairing or rotate the PIN."
        else -> "The server rejected the request (HTTP ${e.code()})."
    }
    e is UnknownHostException -> "Couldn't find that address. Double-check the IP or hostname."
    e is ConnectException || e is SocketTimeoutException ->
        "Couldn't reach that address. Make sure the PC is on and on the same Wi-Fi."
    else -> e.message ?: "Pairing failed. Please try again."
}
