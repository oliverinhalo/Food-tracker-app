package dev.foodtracker.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Named haptics so the whole app agrees on what each gesture feels like. Steppers and sliders get
 * the light tick; confirming a log and deleting an item get the heavier one.
 */
class Haptics internal constructor(
    private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
