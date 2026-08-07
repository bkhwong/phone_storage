package com.phonesync.app.data.remote

import com.phonesync.app.BuildConfig
import com.phonesync.app.data.prefs.SecurePrefs
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** Normalizes a user-entered server address into a well-formed `http(s)://host:port` base URL. */
fun normalizeBaseUrl(raw: String): String {
    var trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed = "http://$trimmed"
    }
    return trimmed.trimEnd('/')
}

/**
 * Builds (and caches) a [PhotoSyncApi] pointed at the currently-paired server. The device
 * token is read fresh from [prefs] on every request via an interceptor, so re-pairing or
 * unpairing takes effect immediately without needing to rebuild the client.
 */
class ApiClientFactory(private val prefs: SecurePrefs) {

    @Volatile private var cachedBaseUrl: String? = null

    @Volatile private var cachedApi: PhotoSyncApi? = null

    /** Client for the currently-paired server. Throws if not paired. */
    fun create(): PhotoSyncApi {
        val baseUrl = prefs.currentBaseUrl()?.takeIf { it.isNotBlank() }
            ?: error("Not paired: no server address configured")
        return createFor(baseUrl)
    }

    /** Client for an explicit server address — used during pairing, before a token exists. */
    fun createFor(baseUrl: String): PhotoSyncApi {
        val normalized = normalizeBaseUrl(baseUrl)
        cachedApi?.let { if (cachedBaseUrl == normalized) return it }
        val api = buildApi(normalized)
        cachedBaseUrl = normalized
        cachedApi = api
        return api
    }

    /** Drops the cached client, e.g. after unpairing so a stale base URL can't leak. */
    fun reset() {
        cachedApi = null
        cachedBaseUrl = null
    }

    private fun buildApi(baseUrl: String): PhotoSyncApi {
        val authInterceptor = Interceptor { chain ->
            val token = prefs.currentToken()
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder().header(AUTH_HEADER, token).build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(clientBuilder.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(PhotoSyncApi::class.java)
    }

    companion object {
        const val AUTH_HEADER = "X-Device-Token"
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        val TEXT_PLAIN = "text/plain".toMediaType()
    }
}
