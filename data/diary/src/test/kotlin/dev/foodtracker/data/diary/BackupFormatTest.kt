package dev.foodtracker.data.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backup is only worth writing if it can be read back. These cover the round trip and the two
 * ways a file arrives unreadable: it is not ours, or it is from a version we do not understand.
 */
class BackupFormatTest {

    private val meal = BackupMeal(
        id = "meal-1",
        mealType = "LUNCH",
        epochDay = 20_000,
        loggedAtMillis = 1_700_000_000_000,
        items = listOf(
            BackupItem(
                name = "Chicken breast, grilled",
                brand = null,
                foodKey = "chicken breast grilled",
                amount = 150.0,
                unit = "GRAM",
                grams = 150.0,
                calories = 248.0,
                proteinGrams = 46.5,
                carbsGrams = 0.0,
                fatGrams = 5.4,
            ),
        ),
    )

    @Test
    fun `a backup survives the round trip intact`() {
        val original = BackupFile(exportedAtMillis = 1_700_000_000_000, meals = listOf(meal))

        val restored = BackupFormat.decode(BackupFormat.encode(original)).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun `numbers are not rounded on the way through`() {
        val precise = meal.copy(items = listOf(meal.items.first().copy(grams = 137.5, calories = 227.3)))

        val restored = BackupFormat
            .decode(BackupFormat.encode(BackupFile(exportedAtMillis = 0, meals = listOf(precise))))
            .getOrThrow()

        assertEquals(137.5, restored.meals.first().items.first().grams, 0.0)
        assertEquals(227.3, restored.meals.first().items.first().calories, 0.0)
    }

    @Test
    fun `a file that is not a backup is refused with a readable reason`() {
        val failure = BackupFormat.decode("{\"hello\":true}").exceptionOrNull()

        assertTrue(failure is BackupError)
        assertEquals("That file isn't a Food Tracker backup.", failure?.message)
    }

    @Test
    fun `a photo of a cat is refused rather than crashing`() {
        assertTrue(BackupFormat.decode("not json at all").isFailure)
    }

    @Test
    fun `a newer backup is refused rather than half-read`() {
        val fromTheFuture = BackupFormat
            .encode(BackupFile(version = 99, exportedAtMillis = 0, meals = emptyList()))

        val failure = BackupFormat.decode(fromTheFuture).exceptionOrNull()

        assertEquals(
            "That backup was made by a newer version of the app. Update and try again.",
            failure?.message,
        )
    }

    @Test
    fun `an older backup still reads, so an export is never orphaned`() {
        val old = """{"version":1,"exportedAtMillis":1,"meals":[],"somethingWeDropped":42}"""

        assertTrue(BackupFormat.decode(old).isSuccess)
    }
}
