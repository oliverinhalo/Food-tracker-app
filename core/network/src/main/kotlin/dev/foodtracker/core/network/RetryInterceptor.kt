package dev.foodtracker.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

/**
 * Retries transient failures with exponential backoff plus jitter.
 *
 * Free-tier Gemini returns 429 when the per-minute quota is spent and 503 when the model itself is
 * saturated; both are worth a short retry, but only a few times and only while the total delay
 * stays inside a window the user will tolerate staring at a spinner. `Retry-After`, when present,
 * always wins over our own schedule. Anything longer than [maxRetryAfterSeconds] is treated as
 * "not worth waiting for" and surfaced to the caller so it can fall back to local results.
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 500,
    private val maxRetryAfterSeconds: Long = 8,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastError: IOException? = null
        var attempt = 0

        while (attempt < maxAttempts) {
            if (attempt > 0) {
                sleeper(backoffMillis(attempt))
            }

            val response = try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                // Network-level failure: retry unless the call was cancelled.
                if (chain.call().isCanceled()) throw e
                lastError = e
                attempt++
                continue
            }

            if (!response.isRetryable()) return response

            val retryAfter = response.retryAfterSeconds()
            if (retryAfter != null && retryAfter > maxRetryAfterSeconds) {
                // Server told us to wait longer than a user will tolerate; let the caller degrade.
                return response
            }

            attempt++
            if (attempt >= maxAttempts) return response

            response.close()
            if (retryAfter != null) {
                sleeper(retryAfter * 1_000)
            }
        }

        throw lastError ?: IOException("Request failed after $maxAttempts attempts")
    }

    private fun backoffMillis(attempt: Int): Long {
        val exponential = baseDelayMillis * 2.0.pow(attempt - 1).toLong()
        // Full jitter: avoids a thundering herd when several captures retry at once.
        return Random.nextLong(baseDelayMillis, exponential.coerceAtLeast(baseDelayMillis) + 1)
    }

    private fun Response.isRetryable(): Boolean = code == 429 || code == 503 || code == 502 || code == 504

    private fun Response.retryAfterSeconds(): Long? =
        header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
}
