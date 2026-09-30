package dev.foodtracker.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Named haptics so the whole app agrees on what each gesture feels like. Steppers and sliders get
 * the light tick; confirming a log and deleting an item get the heavier one.
 */
class Haptics internal constructor(
    private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    private val enabled: Boolean,
) {
    fun tick() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/**
 * Whether haptics are on, provided from the app root so every component honours the setting
 * without each one having to reach for the settings repository.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}
