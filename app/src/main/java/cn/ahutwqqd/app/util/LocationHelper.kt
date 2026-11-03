package cn.ahutwqqd.app.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 一次性取当前坐标，取完即弃，不做后台定位。
 *
 * 策略：先用 5 分钟内的缓存位置（快），否则请求一次当前位置（准）。
 */
object LocationHelper {

    private const val FRESH_MS = 5 * 60 * 1000L

    suspend fun current(context: Context): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val cached = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

        if (cached != null && System.currentTimeMillis() - cached.time < FRESH_MS) {
            return@withContext cached.latitude to cached.longitude
        }

        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.GPS_PROVIDER
                else -> LocationManager.NETWORK_PROVIDER
            }
            withTimeoutOrNull(12_000) { requestOnce(context, manager, provider) }
        } else {
            null
        }

        val result = fresh ?: cached
        result?.let { it.latitude to it.longitude }
    }

    private suspend fun requestOnce(
        context: Context,
        manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        try {
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (e: SecurityException) {
            continuation.resume(null)
        }
        continuation.invokeOnCancellation { signal.cancel() }
    }
}
