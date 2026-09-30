package dev.foodtracker.feature.results

import dev.foodtracker.core.model.DegradeReason

/**
 * User-facing copy for a degraded pipeline. Each message says what happened AND what happens next,
 * because a bare "rate limited" reads as a dead end when the photo is in fact saved and will be
 * retried.
 *
 * These deliberately do not mention on-device results. The local classifier was built and then
 * dropped, on size: it added seventy-odd megabytes of native libraries to a three-megabyte app.
 * Copy that offers results the app cannot produce is worse than no copy, because the user waits
 * for something that is never coming.
 */
internal fun DegradeReason.bannerMessage(hasResults: Boolean = false): String = when (this) {
    DegradeReason.NO_API_KEY ->
        "No Gemini key set. Add a free one in Settings to identify food from a photo."
    DegradeReason.OFFLINE -> if (hasResults) {
        "You're offline. This photo will be re-analysed automatically when you're back online."
    } else {
        "You're offline. This photo is saved and will be analysed as soon as you have a connection."
    }
    DegradeReason.RATE_LIMITED ->
        "Free-tier limit reached. This photo is saved and will be retried shortly."
    DegradeReason.MODEL_UNAVAILABLE ->
        "The cloud model is busy right now. This photo is saved and will be retried in the background."
    DegradeReason.LOCAL_ONLY_MODE ->
        "Local-only mode is on, so photos aren't sent anywhere to be identified. " +
            "Add the food yourself, or turn it off in Settings."
    DegradeReason.CLOUD_ERROR ->
        "Cloud analysis failed. This photo is saved and will be retried -- or add the food yourself."
}

/** Whether the banner should read as an error or as an informational note. */
internal val DegradeReason.isUserActionable: Boolean
    get() = this == DegradeReason.NO_API_KEY || this == DegradeReason.LOCAL_ONLY_MODE
