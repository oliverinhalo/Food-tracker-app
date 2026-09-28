package dev.foodtracker.feature.results

import dev.foodtracker.core.model.DegradeReason

/**
 * User-facing copy for a degraded pipeline. Each message says what happened AND what the app did
 * about it, because "rate limited" on its own reads as a failure when the results on screen are
 * still usable.
 */
internal fun DegradeReason.bannerMessage(): String = when (this) {
    DegradeReason.NO_API_KEY ->
        "No Gemini key set. Add one in Settings for multi-item detection and portion estimates."
    DegradeReason.OFFLINE ->
        "You're offline. Showing on-device results -- this photo will be re-analysed automatically."
    DegradeReason.RATE_LIMITED ->
        "Free-tier limit reached. Showing on-device results; we'll retry this photo shortly."
    DegradeReason.MODEL_UNAVAILABLE ->
        "The cloud model is busy right now. Showing on-device results and retrying in the background."
    DegradeReason.LOCAL_ONLY_MODE ->
        "Local-only mode is on. Showing on-device results."
    DegradeReason.CLOUD_ERROR ->
        "Cloud analysis failed. Showing on-device results -- you can still edit and log this meal."
}

/** Whether the banner should read as an error or as an informational note. */
internal val DegradeReason.isUserActionable: Boolean
    get() = this == DegradeReason.NO_API_KEY
