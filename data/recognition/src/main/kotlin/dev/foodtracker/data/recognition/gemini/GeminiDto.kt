package dev.foodtracker.data.recognition.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
    val systemInstruction: Content? = null,
)

@Serializable
internal data class Content(
    val parts: List<Part>,
    val role: String? = null,
)

@Serializable
internal data class Part(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: InlineData? = null,
)

@Serializable
internal data class InlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)

@Serializable
internal data class GenerationConfig(
    val responseMimeType: String,
    val responseSchema: SchemaNode,
    val temperature: Double? = null,
    val thinkingConfig: ThinkingConfig? = null,
)

@Serializable
internal data class ThinkingConfig(
    val thinkingBudget: Int,
)

@Serializable
internal data class SchemaNode(
    val type: String,
    val properties: Map<String, SchemaNode>? = null,
    val items: SchemaNode? = null,
    val required: List<String>? = null,
    val description: String? = null,
    val enum: List<String>? = null,
)

@Serializable
internal data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null,
    val error: ApiError? = null,
)

@Serializable
internal data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class PromptFeedback(
    val blockReason: String? = null,
)

@Serializable
internal data class ApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

/** One food as the model is asked to describe it. Mirrors [FoodDetectionSchema]. */
@Serializable
internal data class GeminiFoodItem(
    val name: String = "",
    val confidence: Double = 0.0,
    val estimatedGrams: Double = 0.0,
    val householdUnit: String? = null,
    val cookingMethod: String? = null,
    val alternatives: List<GeminiAlternative> = emptyList(),
    val variantQuestion: String? = null,
    val variants: List<GeminiAlternative> = emptyList(),
    val boundingBox: GeminiBox? = null,
)

@Serializable
internal data class GeminiAlternative(
    val name: String = "",
    val confidence: Double = 0.0,
)

@Serializable
internal data class GeminiBox(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
)
