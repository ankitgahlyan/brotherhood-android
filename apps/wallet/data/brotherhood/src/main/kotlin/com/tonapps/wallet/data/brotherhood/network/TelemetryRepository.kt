package com.tonapps.wallet.data.brotherhood.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class RpcProvider(val wireName: String, val maxRps: Int, val maxConcurrent: Int) {
    TONCENTER_V3("toncenter-v3", maxRps = 8, maxConcurrent = 3),
    TONAPI("tonapi", maxRps = 5, maxConcurrent = 2),
    TONHUB_V4("tonhub-v4", maxRps = 10, maxConcurrent = 4),
}

data class RpcTelemetryEvent(
    val id: Long,
    val timestampMs: Long,
    val provider: RpcProvider,
    val endpoint: String,
    val method: String,
    val addressCount: Int,
    val statusCode: Int,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
)

data class ProviderTelemetryMetrics(
    val provider: RpcProvider,
    val totalRequests: Long = 0L,
    val successCount: Long = 0L,
    val errorCount: Long = 0L,
    val rateLimit429Count: Long = 0L,
    val totalAddressesHydrated: Long = 0L,
    val avgLatencyMs: Long = 0L,
    val inFlightCount: Int = 0,
)

data class TelemetrySnapshot(
    val events: List<RpcTelemetryEvent> = emptyList(),
    val providerMetrics: Map<RpcProvider, ProviderTelemetryMetrics> = RpcProvider.entries.associateWith {
        ProviderTelemetryMetrics(provider = it)
    },
    val cacheHits: Long = 0L,
    val cacheMisses: Long = 0L,
    val batchHydrationCount: Long = 0L,
)

class TelemetryRepository(context: Context? = null) {

    private val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val eventIdGenerator = AtomicLong(1L)

    private val _isDeveloperModeEnabled = MutableStateFlow(
        prefs?.getBoolean(KEY_DEV_MODE_ENABLED, false) ?: false
    )
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    private val _isTestnetEnabled = MutableStateFlow(
        prefs?.getBoolean(KEY_TESTNET_ENABLED, true) ?: true
    )
    val isTestnetEnabled: StateFlow<Boolean> = _isTestnetEnabled.asStateFlow()

    private val _isAutoFundEnabled = MutableStateFlow(
        prefs?.getBoolean(KEY_AUTO_FUND_ENABLED, true) ?: true
    )
    val isAutoFundEnabled: StateFlow<Boolean> = _isAutoFundEnabled.asStateFlow()

    private val _snapshot = MutableStateFlow(TelemetrySnapshot())
    val snapshot: StateFlow<TelemetrySnapshot> = _snapshot.asStateFlow()

    private var secretTapCounter = 0
    private var lastTapTimestampMs = 0L

    /**
     * Registers a tap on the "brotherhood" version label in Settings.
     * 7 rapid taps unlock the Floating Developer Bubble.
     * Returns the remaining taps needed (0 when unlocked).
     */
    fun registerSecretVersionTap(nowMs: Long = System.currentTimeMillis()): Int {
        if (nowMs - lastTapTimestampMs > TAP_RESET_WINDOW_MS) {
            secretTapCounter = 0
        }
        lastTapTimestampMs = nowMs
        secretTapCounter++
        if (secretTapCounter >= REQUIRED_TAPS_TO_UNLOCK) {
            secretTapCounter = 0
            setDeveloperModeEnabled(true)
            return 0
        }
        return REQUIRED_TAPS_TO_UNLOCK - secretTapCounter
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DEV_MODE_ENABLED, enabled)?.apply()
        _isDeveloperModeEnabled.value = enabled
    }

    fun setTestnetEnabled(testnet: Boolean) {
        prefs?.edit()?.putBoolean(KEY_TESTNET_ENABLED, testnet)?.apply()
        _isTestnetEnabled.value = testnet
    }

    fun setAutoFundEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_FUND_ENABLED, enabled)?.apply()
        _isAutoFundEnabled.value = enabled
    }

    fun onRequestStarted(provider: RpcProvider) {
        _snapshot.update { current ->
            val currentMetric = current.providerMetrics[provider] ?: ProviderTelemetryMetrics(provider)
            val updatedMap = current.providerMetrics.toMutableMap()
            updatedMap[provider] = currentMetric.copy(
                inFlightCount = currentMetric.inFlightCount + 1,
            )
            current.copy(providerMetrics = updatedMap)
        }
    }

    fun recordRpcEvent(
        provider: RpcProvider,
        endpoint: String,
        method: String = "GET",
        addressCount: Int = 1,
        statusCode: Int,
        latencyMs: Long,
        isSuccess: Boolean,
        errorMessage: String? = null,
        isBatchHydration: Boolean = false,
    ) {
        val event = RpcTelemetryEvent(
            id = eventIdGenerator.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            provider = provider,
            endpoint = endpoint,
            method = method,
            addressCount = addressCount,
            statusCode = statusCode,
            latencyMs = latencyMs,
            isSuccess = isSuccess,
            errorMessage = errorMessage,
        )
        _snapshot.update { current ->
            val prevMetric = current.providerMetrics[provider] ?: ProviderTelemetryMetrics(provider)
            val nextTotal = prevMetric.totalRequests + 1L
            val nextAvgLatency = if (prevMetric.totalRequests == 0L) {
                latencyMs
            } else {
                ((prevMetric.avgLatencyMs * prevMetric.totalRequests) + latencyMs) / nextTotal
            }
            val nextMetric = prevMetric.copy(
                totalRequests = nextTotal,
                successCount = if (isSuccess) {
                    prevMetric.successCount + 1L
                } else {
                    prevMetric.successCount
                },
                errorCount = if (!isSuccess) {
                    prevMetric.errorCount + 1L
                } else {
                    prevMetric.errorCount
                },
                rateLimit429Count = if (statusCode == 429) {
                    prevMetric.rateLimit429Count + 1L
                } else {
                    prevMetric.rateLimit429Count
                },
                totalAddressesHydrated = prevMetric.totalAddressesHydrated + addressCount.toLong(),
                avgLatencyMs = nextAvgLatency,
                inFlightCount = (prevMetric.inFlightCount - 1).coerceAtLeast(0),
            )
            val updatedMap = current.providerMetrics.toMutableMap()
            updatedMap[provider] = nextMetric

            val updatedEvents = (listOf(event) + current.events).take(MAX_RING_BUFFER_EVENTS)
            current.copy(
                events = updatedEvents,
                providerMetrics = updatedMap,
                batchHydrationCount = if (isBatchHydration) {
                    current.batchHydrationCount + 1L
                } else {
                    current.batchHydrationCount
                },
            )
        }
    }

    fun recordCacheHit(count: Int = 1) {
        if (count <= 0) {
            return
        }
        _snapshot.update { current ->
            current.copy(cacheHits = current.cacheHits + count.toLong())
        }
    }

    fun recordCacheMiss(count: Int = 1) {
        if (count <= 0) {
            return
        }
        _snapshot.update { current ->
            current.copy(cacheMisses = current.cacheMisses + count.toLong())
        }
    }

    fun clearTelemetry() {
        _snapshot.value = TelemetrySnapshot()
    }

    companion object {
        private const val PREFS_NAME = "brotherhood_dev_prefs"
        private const val KEY_DEV_MODE_ENABLED = "dev_mode_enabled"
        private const val KEY_TESTNET_ENABLED = "testnet_enabled"
        private const val KEY_AUTO_FUND_ENABLED = "auto_fund_enabled"
        private const val REQUIRED_TAPS_TO_UNLOCK = 7
        private const val TAP_RESET_WINDOW_MS = 2_500L
        const val MAX_RING_BUFFER_EVENTS = 200
    }
}
