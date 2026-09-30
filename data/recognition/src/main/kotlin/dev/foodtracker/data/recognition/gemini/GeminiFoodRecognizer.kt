package dev.foodtracker.data.recognition.gemini

import android.util.Base64
import dev.foodtracker.core.common.di.IoDispatcher
import dev.foodtracker.core.datastore.SecureKeyStore
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.FoodAlternative
import dev.foodtracker.core.model.FoodVariant
import dev.foodtracker.core.model.NormalizedBox
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.core.network.NetworkMonitor
import dev.foodtracker.domain.nutrition.AmbiguousFoods
import dev.foodtracker.domain.recognition.CapturedImage
import dev.foodtracker.domain.recognition.CloudFoodRecognizer
import dev.foodtracker.domain.recognition.RecognitionOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiFoodRecognizer @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val secureKeyStore: SecureKeyStore,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CloudFoodRecognizer {

    override suspend fun recognize(image: CapturedImage): RecognitionOutcome = withContext(ioDispatcher) {
        val apiKey = secureKeyStore.geminiApiKey()
            ?: return@withContext RecognitionOutcome.Unavailable(DegradeReason.NO_API_KEY)

        if (!networkMonitor.isCurrentlyOnline()) {
            return@withContext RecognitionOutcome.Unavailable(DegradeReason.OFFLINE)
        }

        val payload = buildRequestBody(image)
        var lastFailure: RecognitionOutcome.Unavailable? = null

        val preferred = settingsRepository.settings.first().geminiModel.modelId
        for (model in GeminiModels.chainFrom(preferred)) {
            when (val outcome = callModel(model, payload, apiKey)) {
                is RecognitionOutcome.Success -> return@withContext outcome
                is RecognitionOutcome.Unavailable -> {
                    lastFailure = outcome
                    // Only a busy or missing model is worth retrying against a different one.
                    // A rate limit is per-key and a bad key is a bad key, so stop immediately.
                    if (outcome.reason != DegradeReason.MODEL_UNAVAILABLE) {
                        return@withContext outcome
                    }
                }
            }
        }

        lastFailure ?: RecognitionOutcome.Unavailable(DegradeReason.CLOUD_ERROR)
    }

    private fun callModel(model: String, payload: String, apiKey: String): RecognitionOutcome {
        val request = Request.Builder()
            .url(endpointFor(model))
            // Header rather than a ?key= query parameter: keeps the secret out of URLs, which end
            // up in logs, crash reports and OkHttp's own event listeners.
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> parseSuccess(body)
                    response.code == 429 -> RecognitionOutcome.Unavailable(
                        DegradeReason.RATE_LIMITED,
                        "Free-tier limit reached. Using on-device results for now.",
                    )
                    response.code == 503 || response.code == 404 -> RecognitionOutcome.Unavailable(
                        DegradeReason.MODEL_UNAVAILABLE,
                        errorMessage(body),
                    )
                    response.code == 400 || response.code == 401 || response.code == 403 ->
                        RecognitionOutcome.Unavailable(DegradeReason.NO_API_KEY, errorMessage(body))
                    else -> RecognitionOutcome.Unavailable(DegradeReason.CLOUD_ERROR, errorMessage(body))
                }
            }
        } catch (e: IOException) {
            RecognitionOutcome.Unavailable(DegradeReason.OFFLINE, e.message)
        }
    }

    private fun parseSuccess(body: String): RecognitionOutcome {
        val response = runCatching { json.decodeFromString<GenerateContentResponse>(body) }
            .getOrElse { return RecognitionOutcome.Unavailable(DegradeReason.CLOUD_ERROR, "Malformed response") }

        response.promptFeedback?.blockReason?.let {
            return RecognitionOutcome.Unavailable(DegradeReason.CLOUD_ERROR, "Blocked: $it")
        }

        val text = response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.text }
            ?: return RecognitionOutcome.Unavailable(DegradeReason.CLOUD_ERROR, "Empty response")

        val items = runCatching { json.decodeFromString<List<GeminiFoodItem>>(text) }
            .getOrElse { return RecognitionOutcome.Unavailable(DegradeReason.CLOUD_ERROR, "Unparseable items") }

        return RecognitionOutcome.Success(items.mapNotNull { it.toDetectedItem() })
    }

    private fun errorMessage(body: String): String? =
        runCatching { json.decodeFromString<GenerateContentResponse>(body).error?.message }.getOrNull()

    private fun buildRequestBody(image: CapturedImage): String {
        val request = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = FoodDetectionSchema.SYSTEM_INSTRUCTION.trim()))),
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(
                            inlineData = InlineData(
                                mimeType = "image/jpeg",
                                data = Base64.encodeToString(image.bytes, Base64.NO_WRAP),
                            ),
                        ),
                        Part(text = FoodDetectionSchema.PROMPT),
                    ),
                ),
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = FoodDetectionSchema.schema,
                temperature = 0.2,
                // Recognition is a perception task, not a reasoning one. Thinking roughly triples
                // latency here for no measurable accuracy gain.
                thinkingConfig = ThinkingConfig(thinkingBudget = 0),
            ),
        )
        return json.encodeToString(GenerateContentRequest.serializer(), request)
    }

    private fun endpointFor(model: String): HttpUrl =
        "$BASE_URL/v1beta/models/$model:generateContent".toHttpUrl()

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun GeminiFoodItem.toDetectedItem(): DetectedItem? {
    if (name.isBlank()) return null
    val grams = estimatedGrams.takeIf { it.isFinite() && it > 0 } ?: return null

    val trimmedName = name.trim()

    // The model answers this well when asked, but it is not always reachable and older responses
    // predate the question, so the curated table stands in.
    val modelVariants = variants
        .filter { it.name.isNotBlank() }
        .map { FoodVariant(it.name.trim(), it.confidence.coerceIn(0.0, 1.0).toFloat()) }
    val resolvedVariants = modelVariants.ifEmpty { AmbiguousFoods.variantsFor(trimmedName) }
    val resolvedQuestion = variantQuestion?.trim()?.takeIf { it.isNotBlank() }
        ?: AmbiguousFoods.questionFor(trimmedName)

    return DetectedItem(
        id = UUID.randomUUID().toString(),
        name = trimmedName,
        confidence = confidence.coerceIn(0.0, 1.0).toFloat(),
        portion = Portion.ofGrams(grams, householdDescription = householdUnit?.trim()?.takeIf { it.isNotBlank() }),
        source = RecognitionSource.CLOUD,
        cookingMethod = cookingMethod?.trim()?.takeIf { it.isNotBlank() },
        alternatives = alternatives
            .filter { it.name.isNotBlank() }
            .map { FoodAlternative(it.name.trim(), it.confidence.coerceIn(0.0, 1.0).toFloat()) },
        variants = resolvedVariants,
        // Only ask when there is something to choose between; a question with no answers is worse
        // than no question.
        variantQuestion = resolvedQuestion.takeIf { resolvedVariants.isNotEmpty() },
        boundingBox = boundingBox?.toNormalizedBox(),
    )
}

private fun GeminiBox.toNormalizedBox(): NormalizedBox? {
    val l = left.coerceIn(0.0, 1.0).toFloat()
    val t = top.coerceIn(0.0, 1.0).toFloat()
    val r = right.coerceIn(0.0, 1.0).toFloat()
    val b = bottom.coerceIn(0.0, 1.0).toFloat()
    // A degenerate box is worse than none: it would score 0 IoU against everything and silently
    // block name-based matching in the merger.
    if (r <= l || b <= t) return null
    return NormalizedBox(l, t, r, b)
}
