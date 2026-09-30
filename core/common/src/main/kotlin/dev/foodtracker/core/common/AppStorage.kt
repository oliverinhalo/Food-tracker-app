package dev.foodtracker.core.common

/**
 * Names of the app-private directories that hold meal photos.
 *
 * Shared rather than private to whoever writes them, because "delete all my data" has to be able to
 * empty every one of them: a directory that only its writer knows about is a directory that
 * survives an erase.
 */
object PhotoDirectories {
    /** Working copies of captures, in the cache directory. */
    const val CAPTURES = "captures"

    /** Photos kept alongside a logged meal, in internal storage. */
    const val DIARY = "meal-photos"

    /** Photos waiting for a connection so they can be analysed. */
    const val REANALYSIS_QUEUE = "pending-captures"
}
