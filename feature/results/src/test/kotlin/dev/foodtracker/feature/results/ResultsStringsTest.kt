package dev.foodtracker.feature.results

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.DegradeReason
import org.junit.Test

/**
 * The copy is the only thing the user has to go on when a scan does not work, so it has to match
 * what the app actually does. It once offered on-device results after that pass had been dropped
 * from the build, which left people waiting for an answer that was never coming.
 */
class ResultsStringsTest {

    @Test
    fun `no message offers on-device results`() {
        DegradeReason.entries.forEach { reason ->
            listOf(reason.bannerMessage(hasResults = false), reason.bannerMessage(hasResults = true))
                .forEach { message ->
                    assertThat(message.lowercase()).doesNotContain("on-device")
                    assertThat(message.lowercase()).doesNotContain("on device")
                }
        }
    }

    @Test
    fun `every reason says something`() {
        DegradeReason.entries.forEach { reason ->
            assertThat(reason.bannerMessage()).isNotEmpty()
        }
    }

    @Test
    fun `only the reasons the user can act on are marked actionable`() {
        assertThat(DegradeReason.NO_API_KEY.isUserActionable).isTrue()
        assertThat(DegradeReason.LOCAL_ONLY_MODE.isUserActionable).isTrue()

        // Waiting is the only response to these, so styling them as errors would be alarming.
        assertThat(DegradeReason.OFFLINE.isUserActionable).isFalse()
        assertThat(DegradeReason.RATE_LIMITED.isUserActionable).isFalse()
        assertThat(DegradeReason.MODEL_UNAVAILABLE.isUserActionable).isFalse()
        assertThat(DegradeReason.CLOUD_ERROR.isUserActionable).isFalse()
    }

    @Test
    fun `a retryable failure says the photo is kept`() {
        listOf(
            DegradeReason.OFFLINE,
            DegradeReason.RATE_LIMITED,
            DegradeReason.MODEL_UNAVAILABLE,
            DegradeReason.CLOUD_ERROR,
        ).forEach { reason ->
            assertThat(reason.bannerMessage().lowercase()).contains("this photo")
        }
    }

    @Test
    fun `the reasons that are never retried do not promise a retry`() {
        listOf(DegradeReason.NO_API_KEY, DegradeReason.LOCAL_ONLY_MODE).forEach { reason ->
            assertThat(reason.bannerMessage().lowercase()).doesNotContain("retr")
            assertThat(reason.bannerMessage()).contains("Settings")
        }
    }
}
