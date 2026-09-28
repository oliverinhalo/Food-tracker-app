package dev.foodtracker.domain.recognition

import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.FoodAlternative
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.core.text.TextSimilarity

/**
 * Folds a later, more trustworthy pass of detections into the list already on screen.
 *
 * The constraints that shape this:
 *  - The sheet is already visible when the cloud result lands, so item identity must be stable.
 *    A matched item keeps the *existing* id, otherwise LazyColumn re-keys the row and the card
 *    visibly flashes and loses its expanded/edited state.
 *  - Anything the user has touched outranks any recogniser. If they renamed an item or changed a
 *    portion, a late cloud response must not silently undo that.
 *  - The cloud pass sees the whole plate, so it is authoritative about what is *absent*: a local
 *    guess that the cloud pass overlaps but disagrees with is replaced, not kept alongside.
 */
class RecognitionMerger(
    private val nameSimilarityThreshold: Float = 0.5f,
    private val boxIouThreshold: Float = 0.4f,
    private val keepUnmatchedLocalAboveConfidence: Float = 0.6f,
) {

    fun merge(existing: List<DetectedItem>, incoming: List<DetectedItem>): List<DetectedItem> {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return incoming

        val consumedExisting = mutableSetOf<String>()
        val merged = mutableListOf<DetectedItem>()

        for (candidate in incoming) {
            val match = bestMatch(candidate, existing, consumedExisting)
            if (match == null) {
                merged += candidate
                continue
            }

            consumedExisting += match.id
            merged += combine(existing = match, incoming = candidate)
        }

        // Local-only detections the new pass never mentioned. Keep the confident ones (the cloud
        // model does miss small items like a side of butter), drop the rest as noise.
        val leftovers = existing.filter { it.id !in consumedExisting }
            .filter { it.source == RecognitionSource.USER || it.confidence >= keepUnmatchedLocalAboveConfidence }
            .filterNot { leftover -> incoming.any { overlapsSpatially(leftover, it) } && leftover.source != RecognitionSource.USER }

        return merged + leftovers
    }

    private fun combine(existing: DetectedItem, incoming: DetectedItem): DetectedItem {
        // A user edit is final: take nothing but genuinely additive metadata from the recogniser.
        if (existing.source == RecognitionSource.USER) {
            return existing.copy(
                alternatives = mergeAlternatives(existing, incoming),
            )
        }

        // Otherwise the higher-trust source wins the identity fields, and we keep the stable id.
        val winner = if (incoming.source.trustRank >= existing.source.trustRank) incoming else existing
        val loser = if (winner === incoming) existing else incoming

        return winner.copy(
            id = existing.id,
            alternatives = mergeAlternatives(winner, loser),
            boundingBox = winner.boundingBox ?: loser.boundingBox,
            cookingMethod = winner.cookingMethod ?: loser.cookingMethod,
            nutrientsPer100g = winner.nutrientsPer100g ?: loser.nutrientsPer100g,
            brand = winner.brand ?: loser.brand,
            foodId = winner.foodId ?: loser.foodId,
        )
    }

    /**
     * The loser's own label becomes an alternative, so a local guess the cloud overrode is still
     * one tap away in the "Change item" dropdown.
     */
    private fun mergeAlternatives(winner: DetectedItem, loser: DetectedItem): List<FoodAlternative> {
        val winnerKey = TextSimilarity.normalizeTokens(winner.name).joinToString(" ")
        val candidates = buildList {
            addAll(winner.alternatives)
            // The overridden label is itself a plausible alternative, ranked by its own confidence.
            add(FoodAlternative(name = loser.name, confidence = loser.confidence))
            addAll(loser.alternatives)
        }

        return candidates
            .asSequence()
            .filter { it.name.isNotBlank() }
            .distinctBy { TextSimilarity.normalizeTokens(it.name).joinToString(" ") }
            .filterNot { TextSimilarity.normalizeTokens(it.name).joinToString(" ") == winnerKey }
            .sortedByDescending { it.confidence }
            .take(MAX_ALTERNATIVES)
            .toList()
    }

    private fun overlapsSpatially(a: DetectedItem, b: DetectedItem): Boolean {
        val boxA = a.boundingBox ?: return false
        val boxB = b.boundingBox ?: return false
        return boxA.iou(boxB) >= boxIouThreshold
    }

    private fun bestMatch(
        candidate: DetectedItem,
        pool: List<DetectedItem>,
        consumed: Set<String>,
    ): DetectedItem? {
        var best: DetectedItem? = null
        var bestScore = 0f

        for (item in pool) {
            if (item.id in consumed) continue
            val score = matchScore(candidate, item)
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best
    }

    /**
     * Combined score in 0..1. Boxes are the stronger signal when both sides have them, but a good
     * name match alone is enough -- the on-device classifier often has no box at all.
     */
    private fun matchScore(a: DetectedItem, b: DetectedItem): Float {
        val nameScore = TextSimilarity.similarity(a.name, b.name)
        val boxA = a.boundingBox
        val boxB = b.boundingBox

        if (boxA != null && boxB != null) {
            val iou = boxA.iou(boxB)
            if (iou >= boxIouThreshold) {
                return maxOf(iou, nameScore)
            }
            // Boxes exist and clearly point at different regions of the plate: two different foods
            // even if they happen to share a word ("green salad" vs "green beans").
            if (iou < 0.1f) return 0f
        }

        return if (nameScore >= nameSimilarityThreshold) nameScore else 0f
    }

    private companion object {
        /** The dropdown shows a handful; more turns a quick correction into a scroll. */
        const val MAX_ALTERNATIVES = 6
    }
}
