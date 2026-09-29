package dev.foodtracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foodtracker.core.datastore.UnitSystem
import dev.foodtracker.core.datastore.UserSettings

object SettingsTestTags {
    const val API_KEY_FIELD = "settings_api_key_field"
    const val USDA_KEY_FIELD = "settings_usda_key_field"
    const val API_KEY_SAVE = "settings_api_key_save"
    const val LOCAL_ONLY_SWITCH = "settings_local_only"
    const val CALORIE_GOAL_FIELD = "settings_calorie_goal"
    const val PROTEIN_GOAL_FIELD = "settings_protein_goal"
    const val CARBS_GOAL_FIELD = "settings_carbs_goal"
    const val FAT_GOAL_FIELD = "settings_fat_goal"
}

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        settings = settings,
        onSaveApiKey = viewModel::setApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onSaveUsdaKey = viewModel::setUsdaKey,
        onClearUsdaKey = viewModel::clearUsdaKey,
        onCalorieGoalChange = viewModel::setCalorieGoal,
        onMacroGoalsChange = viewModel::setMacroGoals,
        onUnitSystemChange = viewModel::setUnitSystem,
        onLocalOnlyChange = viewModel::setLocalOnlyMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    settings: UserSettings,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveUsdaKey: (String) -> Unit,
    onClearUsdaKey: () -> Unit,
    onCalorieGoalChange: (Int) -> Unit,
    onMacroGoalsChange: (Int, Int, Int) -> Unit,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onLocalOnlyChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        KeySection(
            title = "Gemini API key",
            explanation = if (settings.hasApiKey) {
                "A key is saved. It is stored encrypted on this device and never leaves it except to call Google's API."
            } else {
                "Add a free Google AI Studio key to get multi-item detection and portion estimates."
            },
            hasKey = settings.hasApiKey,
            testTag = SettingsTestTags.API_KEY_FIELD,
            onSave = onSaveApiKey,
            onClear = onClearApiKey,
        )

        KeySection(
            title = "USDA FoodData Central key",
            explanation = if (settings.hasUsdaKey) {
                "A key is saved. USDA covers generic, unbranded foods; without it only branded products resolve."
            } else {
                "Optional but recommended: a free USDA key is what gives generic foods like rice or broccoli " +
                    "accurate calories. Without it the app falls back to branded products, which match poorly."
            },
            hasKey = settings.hasUsdaKey,
            testTag = SettingsTestTags.USDA_KEY_FIELD,
            onSave = onSaveUsdaKey,
            onClear = onClearUsdaKey,
        )

        GoalSection(
            settings = settings,
            onGoalChange = onCalorieGoalChange,
            onMacroGoalsChange = onMacroGoalsChange,
        )

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Units", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    UnitSystem.entries.forEachIndexed { index, system ->
                        SegmentedButton(
                            selected = settings.unitSystem == system,
                            onClick = { onUnitSystemChange(system) },
                            shape = SegmentedButtonDefaults.itemShape(index, UnitSystem.entries.size),
                        ) {
                            Text(if (system == UnitSystem.METRIC) "Metric" else "Imperial")
                        }
                    }
                }

                HorizontalDivider()

                ToggleRow(
                    title = "Local-only mode",
                    subtitle = "Never send photos to the cloud. On-device results only.",
                    checked = settings.localOnlyMode,
                    onCheckedChange = onLocalOnlyChange,
                    testTag = SettingsTestTags.LOCAL_ONLY_SWITCH,
                )

                ToggleRow(
                    title = "Dynamic colour",
                    subtitle = "Match the app's palette to your wallpaper.",
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }
    }
}

@Composable
private fun KeySection(
    title: String,
    explanation: String,
    hasKey: Boolean,
    testTag: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(if (hasKey) "Replace key" else "Paste key") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (visible) "Hide key" else "Show key",
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag(SettingsTestTags.API_KEY_SAVE),
                ) {
                    Text("Save")
                }
                if (hasKey) {
                    TextButton(onClick = onClear) { Text("Remove key") }
                }
            }
        }
    }
}

@Composable
private fun GoalSection(
    settings: UserSettings,
    onGoalChange: (Int) -> Unit,
    onMacroGoalsChange: (Int, Int, Int) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Daily goals", style = MaterialTheme.typography.titleMedium)

            NumberField(
                label = "Calories",
                value = settings.dailyCalorieGoal,
                suffix = "kcal",
                testTag = SettingsTestTags.CALORIE_GOAL_FIELD,
                onValueChange = onGoalChange,
            )

            Text(
                text = "Macro targets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = "Protein",
                    value = settings.proteinGoalGrams,
                    suffix = "g",
                    testTag = SettingsTestTags.PROTEIN_GOAL_FIELD,
                    modifier = Modifier.weight(1f),
                    onValueChange = { onMacroGoalsChange(it, settings.carbsGoalGrams, settings.fatGoalGrams) },
                )
                NumberField(
                    label = "Carbs",
                    value = settings.carbsGoalGrams,
                    suffix = "g",
                    testTag = SettingsTestTags.CARBS_GOAL_FIELD,
                    modifier = Modifier.weight(1f),
                    onValueChange = { onMacroGoalsChange(settings.proteinGoalGrams, it, settings.fatGoalGrams) },
                )
                NumberField(
                    label = "Fat",
                    value = settings.fatGoalGrams,
                    suffix = "g",
                    testTag = SettingsTestTags.FAT_GOAL_FIELD,
                    modifier = Modifier.weight(1f),
                    onValueChange = { onMacroGoalsChange(settings.proteinGoalGrams, settings.carbsGoalGrams, it) },
                )
            }

            // Macros and the calorie goal are set independently, so they can disagree. Saying so is
            // more useful than silently rewriting whichever the user touched last.
            val macroCalories = settings.proteinGoalGrams * 4 + settings.carbsGoalGrams * 4 + settings.fatGoalGrams * 9
            val drift = macroCalories - settings.dailyCalorieGoal
            if (kotlin.math.abs(drift) > settings.dailyCalorieGoal * 0.1) {
                Text(
                    text = "Your macro targets come to $macroCalories kcal, " +
                        (if (drift > 0) "$drift above" else "${-drift} below") +
                        " your calorie goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    suffix: String,
    testTag: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on `value` so an external change is picked up, but held locally so the field does not
    // fight the user mid-edit when they clear it to retype.
    var draft by remember(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = draft,
        onValueChange = { input ->
            draft = input.filter(Char::isDigit).take(5)
            draft.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        suffix = { Text(suffix) },
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        )
    }
}
