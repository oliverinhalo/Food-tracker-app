package dev.foodtracker.data.recognition.gemini

/**
 * Preference order for the hosted model.
 *
 * The free tier hands out 503 UNAVAILABLE on the newest models far more often than it rate-limits
 * you, so a single hardcoded model name makes the feature look broken at busy times. We walk down
 * this list on 503/404 and remember nothing: the next capture starts at the top again, because
 * availability comes back within minutes.
 */
internal object GeminiModels {

    val FALLBACK_CHAIN: List<String> = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.5-flash",
        "gemini-3-flash-preview",
    )

    const val DEFAULT = "gemini-3.8-flash"

    /** Models to try, starting from the user's configured choice if they set one. */
    fun chainFrom(preferred: String?): List<String> {
        if (preferred.isNullOrBlank()) return FALLBACK_CHAIN
        return listOf(preferred) + FALLBACK_CHAIN.filterNot { it == preferred }
    }
}
