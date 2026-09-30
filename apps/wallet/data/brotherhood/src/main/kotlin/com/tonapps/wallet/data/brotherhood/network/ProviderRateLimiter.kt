package com.tonapps.wallet.data.brotherhood.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max

class RateLimitedHttpException(
    val statusCode: Int,
    val retryAfterMs: Long? = null,
    override val message: String,
) : Exception(message)

/**
 * Per-provider token-bucket and concurrency rate limiter matching web `rateLimiter.ts`.
 * Enforces RPS pacing, maximum concurrent in-flight calls, and automatic 429 backoff.
 */
class ProviderRateLimiter(
    private val telemetryRepository: TelemetryRepository,
) {
    private class BucketState(
        val semaphore: Semaphore,
        val minIntervalMs: Long,
    ) {
        val mutex = Mutex()
        var nextAllowedAtMs: Long = 0L
        var cooldownUntilMs: Long = 0L
    }

    private val buckets: Map<RpcProvider, BucketState> = RpcProvider.entries.associateWith { provider ->
        val minInterval = max(1L, 1_000L / provider.maxRps.toLong())
        BucketState(
            semaphore = Semaphore(provider.maxConcurrent),
            minIntervalMs = minInterval,
        )
    }

    suspend fun <T> withRateLimit(
        provider: RpcProvider,
        maxRetries: Int = 3,
        block: suspend () -> T,
    ): T {
        val bucket = buckets.getValue(provider)
        var attempt = 0
        while (true) {
            acquireSlot(bucket)
            telemetryRepository.onRequestStarted(provider)
            try {
                return block()
            } catch (e: RateLimitedHttpException) {
                attempt++
                if (e.statusCode == 429 && attempt <= maxRetries) {
                    val backoffMs = e.retryAfterMs ?: (BASE_429_BACKOFF_MS * (1L shl (attempt - 1)))
                    applyCooldown(bucket, backoffMs)
                    delay(backoffMs)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun acquireSlot(bucket: BucketState) {
        bucket.semaphore.withPermit {
            val waitMs = bucket.mutex.withLock {
                val now = System.currentTimeMillis()
                val earliest = max(now, max(bucket.nextAllowedAtMs, bucket.cooldownUntilMs))
                bucket.nextAllowedAtMs = earliest + bucket.minIntervalMs
                earliest - now
            }
            if (waitMs > 0L) {
                delay(waitMs)
            }
        }
    }

    private suspend fun applyCooldown(bucket: BucketState, cooldownMs: Long) {
        bucket.mutex.withLock {
            val target = System.currentTimeMillis() + cooldownMs
            if (target > bucket.cooldownUntilMs) {
                bucket.cooldownUntilMs = target
            }
        }
    }

    companion object {
        private const val BASE_429_BACKOFF_MS = 1_200L
    }
}
