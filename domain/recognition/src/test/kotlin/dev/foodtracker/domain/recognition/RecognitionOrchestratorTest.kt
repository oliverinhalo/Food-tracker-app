package dev.foodtracker.domain.recognition

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.RecognitionEvent
import dev.foodtracker.core.model.RecognitionSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecognitionOrchestratorTest {

    private val image = CapturedImage(byteArrayOf(1, 2, 3), width = 100, height = 100)

    private class FakeLocal(
        override val isAvailable: Boolean,
        private val outcome: RecognitionOutcome,
    ) : LocalFoodRecognizer {
        override suspend fun recognize(image: CapturedImage) = outcome
    }

    private class FakeCloud(private val outcome: RecognitionOutcome) : CloudFoodRecognizer {
        override suspend fun recognize(image: CapturedImage) = outcome
    }

    private class RecordingQueue : ReanalysisQueue {
        val queued = mutableListOf<String>()
        override suspend fun enqueue(captureId: String, image: CapturedImage) {
            queued += captureId
        }
    }

    private fun orchestrator(
        local: LocalFoodRecognizer,
        cloud: CloudFoodRecognizer,
        queue: ReanalysisQueue? = null,
    ) = RecognitionOrchestrator(local, cloud, RecognitionMerger(), queue)

    @Test
    fun `provisional results are emitted before the cloud result`() = runTest {
        val events = orchestrator(
            local = FakeLocal(true, RecognitionOutcome.Success(listOf(item("l1", "rice")))),
            cloud = FakeCloud(
                RecognitionOutcome.Success(listOf(item("c1", "white rice", source = RecognitionSource.CLOUD))),
            ),
        ).recognize("cap", image, RecognitionConfig(cloudEnabled = true)).toList()

        assertThat(events.map { it::class }).containsExactly(
            RecognitionEvent.Provisional::class,
            RecognitionEvent.Refined::class,
        ).inOrder()
    }

    @Test
    fun `an unavailable local model goes straight to the cloud result`() = runTest {
        val events = orchestrator(
            local = FakeLocal(false, RecognitionOutcome.Unavailable(DegradeReason.MODEL_UNAVAILABLE)),
            cloud = FakeCloud(RecognitionOutcome.Success(listOf(item("c1", "rice", source = RecognitionSource.CLOUD)))),
        ).recognize("cap", image, RecognitionConfig(cloudEnabled = true)).toList()

        assertThat(events).hasSize(1)
        assertThat(events.single()).isInstanceOf(RecognitionEvent.Refined::class.java)
    }

    @Test
    fun `an offline capture keeps local results and is queued for re-analysis`() = runTest {
        val queue = RecordingQueue()

        val events = orchestrator(
            local = FakeLocal(true, RecognitionOutcome.Success(listOf(item("l1", "rice")))),
            cloud = FakeCloud(RecognitionOutcome.Unavailable(DegradeReason.OFFLINE)),
            queue = queue,
        ).recognize("cap-1", image, RecognitionConfig(cloudEnabled = true)).toList()

        val degraded = events.last() as RecognitionEvent.Degraded
        assertThat(degraded.reason).isEqualTo(DegradeReason.OFFLINE)
        assertThat(degraded.items).hasSize(1)
        assertThat(queue.queued).containsExactly("cap-1")
    }

    @Test
    fun `a missing key is not queued because retrying cannot help`() = runTest {
        val queue = RecordingQueue()

        orchestrator(
            local = FakeLocal(true, RecognitionOutcome.Success(listOf(item("l1", "rice")))),
            cloud = FakeCloud(RecognitionOutcome.Unavailable(DegradeReason.NO_API_KEY)),
            queue = queue,
        ).recognize("cap-1", image, RecognitionConfig(cloudEnabled = true)).toList()

        assertThat(queue.queued).isEmpty()
    }

    @Test
    fun `local-only mode never calls the cloud and never queues`() = runTest {
        val queue = RecordingQueue()

        val events = orchestrator(
            local = FakeLocal(true, RecognitionOutcome.Success(listOf(item("l1", "rice")))),
            cloud = FakeCloud(RecognitionOutcome.Success(listOf(item("c1", "should not be used")))),
            queue = queue,
        ).recognize("cap", image, RecognitionConfig(false, DegradeReason.LOCAL_ONLY_MODE)).toList()

        assertThat(events.last()).isInstanceOf(RecognitionEvent.Degraded::class.java)
        assertThat((events.last() as RecognitionEvent.Degraded).items.single().name).isEqualTo("rice")
        assertThat(queue.queued).isEmpty()
    }

    @Test
    fun `nothing at all from either pass is a failure, not an empty success`() = runTest {
        val events = orchestrator(
            local = FakeLocal(false, RecognitionOutcome.Unavailable(DegradeReason.MODEL_UNAVAILABLE)),
            cloud = FakeCloud(RecognitionOutcome.Unavailable(DegradeReason.RATE_LIMITED, "slow down")),
        ).recognize("cap", image, RecognitionConfig(cloudEnabled = true)).toList()

        val failed = events.single() as RecognitionEvent.Failed
        assertThat(failed.reason).isEqualTo(DegradeReason.RATE_LIMITED)
        assertThat(failed.message).isEqualTo("slow down")
    }

    @Test
    fun `offline with no local results fails rather than showing an empty sheet`() = runTest {
        val events = orchestrator(
            local = FakeLocal(true, RecognitionOutcome.Success(emptyList())),
            cloud = FakeCloud(RecognitionOutcome.Unavailable(DegradeReason.OFFLINE)),
        ).recognize("cap", image, RecognitionConfig(cloudEnabled = true)).toList()

        assertThat(events.single()).isInstanceOf(RecognitionEvent.Failed::class.java)
    }

    @Test
    fun `recoverable reasons are the ones worth queueing`() {
        assertThat(DegradeReason.OFFLINE.isRecoverable).isTrue()
        assertThat(DegradeReason.RATE_LIMITED.isRecoverable).isTrue()
        assertThat(DegradeReason.NO_API_KEY.isRecoverable).isFalse()
        assertThat(DegradeReason.LOCAL_ONLY_MODE.isRecoverable).isFalse()
    }
}
