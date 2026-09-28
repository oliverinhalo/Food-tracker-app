package dev.foodtracker.core.text

import java.util.Locale

/**
 * Food-label similarity, used to decide whether a cloud detection and a local detection are the
 * same thing. Deliberately not a generic string metric: labels like "grilled chicken breast" and
 * "chicken breast, grilled" must score as the same food, while "chicken breast" and "chicken soup"
 * must not. Token overlap handles word order and extra qualifiers; edit distance catches plurals
 * and small spelling differences within a token.
 */
/**
 * Food-label similarity, shared by the recognition merger (are these two detections the same
 * food?) and the nutrition matcher (is this database row actually the food we asked for?).
 */
object TextSimilarity {

    /** Words that carry no identity and would otherwise inflate overlap scores. */
    private val STOP_WORDS = setOf(
        "a", "an", "and", "of", "with", "in", "on", "the", "fresh", "raw", "plain",
        "cooked", "prepared", "style", "homemade", "serving", "portion", "piece", "pieces",
    )

    fun normalizeTokens(text: String): List<String> = text
        .lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() && it !in STOP_WORDS }
        .map(::singularize)

    private fun singularize(token: String): String = when {
        token.length > 3 && token.endsWith("ies") -> token.dropLast(3) + "y"
        token.length > 3 && token.endsWith("es") && !token.endsWith("ses") -> token.dropLast(2)
        token.length > 3 && token.endsWith("s") && !token.endsWith("ss") -> token.dropLast(1)
        else -> token
    }

    /**
     * Returns 0..1. A token counts as matched if an exact counterpart exists, or if some token on
     * the other side is within one edit -- enough for "tomatoe"/"tomato", not enough to conflate
     * "rice" with "ribs".
     */
    fun similarity(left: String, right: String): Float {
        val a = normalizeTokens(left)
        val b = normalizeTokens(right)
        if (a.isEmpty() || b.isEmpty()) return 0f

        val matched = a.count { tokenA -> b.any { tokenB -> tokensMatch(tokenA, tokenB) } }
        val reverseMatched = b.count { tokenB -> a.any { tokenA -> tokensMatch(tokenA, tokenB) } }

        // Symmetric overlap: scoring only one direction would make "chicken" a perfect match for
        // "chicken caesar salad with croutons".
        return (matched + reverseMatched).toFloat() / (a.size + b.size).toFloat()
    }

    private fun tokensMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (minOf(a.length, b.length) < 4) return false
        return levenshtein(a, b) <= 1
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * Asymmetric match score in 0..1, weighted toward how much of [query] the [candidate] covers.
     *
     * Symmetric overlap is right for asking "are these the same detection?", but wrong for
     * searching a food database, whose entries are long and descriptive. Scored symmetrically,
     * "Chicken breast, roll, oven-roasted" beats "Chicken, broiler or fryers, breast, skinless,
     * boneless, meat only, cooked, grilled" for the query "grilled chicken breast" -- purely for
     * being shorter, despite being a different, processed product. Covering the query is what
     * matters; extra descriptive words are a mild signal, not a heavy penalty.
     */
    fun coverage(query: String, candidate: String, queryWeight: Float = 0.75f): Float {
        val a = normalizeTokens(query)
        val b = normalizeTokens(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0f

        val forward = a.count { tokenA -> b.any { tokenB -> tokensMatch(tokenA, tokenB) } }.toFloat() / a.size
        val reverse = b.count { tokenB -> a.any { tokenA -> tokensMatch(tokenA, tokenB) } }.toFloat() / b.size

        return queryWeight * forward + (1 - queryWeight) * reverse
    }
}
