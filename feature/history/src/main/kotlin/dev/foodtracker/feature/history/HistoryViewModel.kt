package dev.foodtracker.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.data.diary.DiaryRepository
import dev.foodtracker.data.diary.LoggedMeal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** How far back the trend chart looks. */
enum class HistoryRange(val label: String, val days: Long) {
    WEEK("7 days", 7),
    FORTNIGHT("14 days", 14),
    MONTH("30 days", 30),
}

data class DayPoint(
    val date: LocalDate,
    val calories: Int,
    val nutrients: Nutrients,
) {
    val hasData: Boolean get() = calories > 0
}

data class HistoryUiState(
    val range: HistoryRange = HistoryRange.WEEK,
    val selectedDate: LocalDate = LocalDate.now(),
    val goal: Int = 2000,
    val points: List<DayPoint> = emptyList(),
    val selectedMeals: List<LoggedMeal> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** Days with anything logged; averaging over untracked days would flatter the numbers. */
    private val logged: List<DayPoint> get() = points.filter { it.hasData }

    val averageCalories: Int
        get() = if (logged.isEmpty()) 0 else logged.sumOf { it.calories } / logged.size

    val daysLogged: Int get() = logged.size

    val daysOnTarget: Int
        get() = logged.count { it.calories <= goal }

    val maxCalories: Int
        get() = maxOf(points.maxOfOrNull { it.calories } ?: 0, goal)
}

/** The inputs that define one view of history; a named type since `combine` maxes out at Triple. */
private data class Window(
    val range: HistoryRange,
    val date: LocalDate,
    val goal: Int,
    val today: LocalDate,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val range = MutableStateFlow(HistoryRange.WEEK)
    private val selectedDate = MutableStateFlow(timeProvider.today())

    val uiState: StateFlow<HistoryUiState> = combine(
        range,
        selectedDate,
        settingsRepository.settings,
        timeProvider.todayFlow(),
    ) { range, date, settings, today -> Window(range, date, settings.dailyCalorieGoal, today) }
        .flatMapLatest { (range, date, goal, today) ->
            val from = today.minusDays(range.days - 1)

            combine(
                diaryRepository.totalsBetween(from, today),
                diaryRepository.mealsFor(date),
            ) { totals, meals ->
                HistoryUiState(
                    range = range,
                    selectedDate = date,
                    goal = goal,
                    // Every day in the window appears, including empty ones -- a gap in the chart
                    // is information, and skipping them would silently compress the axis.
                    points = (0 until range.days).map { offset ->
                        val day = from.plusDays(offset)
                        val nutrients = totals[day] ?: Nutrients.ZERO
                        DayPoint(day, nutrients.calories.roundToInt(), nutrients)
                    },
                    selectedMeals = meals,
                    isLoading = false,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun selectRange(value: HistoryRange) {
        range.value = value
    }

    fun selectDate(value: LocalDate) {
        selectedDate.value = value
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch { diaryRepository.deleteMeal(mealId) }
    }
}
