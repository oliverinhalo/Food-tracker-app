package dev.foodtracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foodtracker.core.datastore.GeminiModelChoice
import dev.foodtracker.core.datastore.ImageQuality
import dev.foodtracker.core.datastore.ThemeMode
import dev.foodtracker.core.datastore.UnitSystem
import dev.foodtracker.core.datastore.UserSettings

object SettingsTestTags {
    const val API_KEY_FIELD = "settings_api_key_field"
    const val USDA_KEY_FIELD = "settings_usda_key_field"
    const val API_KEY_SAVE = "settings_api_key_save"
    const val LOCAL_ONLY_SWITCH = "settings_local_only"
    const val CALORIE_GOAL_FIELD = "settings_calorie_goal"
    const val REVEAL_KEY = "settings_reveal_key"
    const val REVEALED_KEY = "settings_revealed_key"
    const val PROTEIN_GOAL_FIELD = "settings_protein_goal"
    const val CARBS_GOAL_FIELD = "settings_carbs_goal"
    const val FAT_GOAL_FIELD = "settings_fat_goal"
    const val THEME_MODE = "settings_theme_mode"
    const val HAPTICS_SWITCH = "settings_haptics"
    const val IMAGE_QUALITY = "settings_image_quality"
    const val GEMINI_MODEL = "settings_gemini_model"
    const val REANALYSE_SWITCH = "settings_reanalyse"
    const val EXPORT = "settings_export"
    const val IMPORT = "settings_import"
    const val DELETE_ALL = "settings_delete_all"
    const val DELETE_CONFIRM = "settings_delete_confirm"
    const val DATA_TASK_MESSAGE = "settings_data_task_message"
    const val PRIVACY_POLICY = "settings_privacy_policy"
}

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val dataTask by viewModel.dataTask.collectAsStateWithLifecycle()
    val revealedGeminiKey by viewModel.revealedGeminiKey.collectAsStateWithLifecycle()
    val revealedUsdaKey by viewModel.revealedUsdaKey.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The system picker owns the file, not the app: no storage permission is asked for, and the
    // user decides where their diary lands.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.export { payload ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
                    ?: error("no output stream for $uri")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.import {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }
        }
    }

    SettingsScreen(
        settings = settings,
        dataTask = dataTask,
        onSaveApiKey = viewModel::setApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onSaveUsdaKey = viewModel::setUsdaKey,
        onClearUsdaKey = viewModel::clearUsdaKey,
        revealedGeminiKey = revealedGeminiKey,
        revealedUsdaKey = revealedUsdaKey,
        onRevealGeminiKey = { viewModel.revealGeminiKey() },
        onHideGeminiKey = viewModel::hideGeminiKey,
        onRevealUsdaKey = { viewModel.revealUsdaKey() },
        onHideUsdaKey = viewModel::hideUsdaKey,
        onCalorieGoalChange = viewModel::setCalorieGoal,
        onMacroGoalsChange = viewModel::setMacroGoals,
        onUnitSystemChange = viewModel::setUnitSystem,
        onLocalOnlyChange = viewModel::setLocalOnlyMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onThemeModeChange = viewModel::setThemeMode,
        onHapticsChange = viewModel::setHaptics,
        onImageQualityChange = viewModel::setImageQuality,
        onGeminiModelChange = viewModel::setGeminiModel,
        onReanalyseChange = viewModel::setReanalyseQueuedPhotos,
        onExport = { exportLauncher.launch(defaultBackupName()) },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onDeleteEverything = viewModel::deleteEverything,
        onDismissDataTask = viewModel::dismissDataTask,
        modifier = modifier,
    )
}

/** Dated, so successive exports sit alongside each other rather than overwriting. */
private fun defaultBackupName(): String {
    val today = java.time.LocalDate.now()
    return "food-tracker-$today.json"
}

@Composable
internal fun SettingsScreen(
    settings: UserSettings,
    dataTask: DataTaskState,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveUsdaKey: (String) -> Unit,
    onClearUsdaKey: () -> Unit,
    revealedGeminiKey: String?,
    revealedUsdaKey: String?,
    onRevealGeminiKey: () -> Unit,
    onHideGeminiKey: () -> Unit,
    onRevealUsdaKey: () -> Unit,
    onHideUsdaKey: () -> Unit,
    onCalorieGoalChange: (Int) -> Unit,
    onMacroGoalsChange: (Int, Int, Int) -> Unit,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onLocalOnlyChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onImageQualityChange: (ImageQuality) -> Unit,
    onGeminiModelChange: (GeminiModelChoice) -> Unit,
    onReanalyseChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteEverything: () -> Unit,
    onDismissDataTask: () -> Unit,
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
            revealed = revealedGeminiKey,
            onSave = onSaveApiKey,
            onClear = onClearApiKey,
            onReveal = onRevealGeminiKey,
            onHide = onHideGeminiKey,
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
            revealed = revealedUsdaKey,
            onSave = onSaveUsdaKey,
            onClear = onClearUsdaKey,
            onReveal = onRevealUsdaKey,
            onHide = onHideUsdaKey,
        )

        GoalSection(
            settings = settings,
            onGoalChange = onCalorieGoalChange,
            onMacroGoalsChange = onMacroGoalsChange,
        )

        SectionCard("Appearance") {
            LabelledRow("Theme") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.testTag(SettingsTestTags.THEME_MODE)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            ToggleRow(
                title = "Dynamic colour",
                subtitle = "Match the app's palette to your wallpaper.",
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )

            ToggleRow(
                title = "Haptics",
                subtitle = "A small buzz when a meal is logged or a portion snaps to a step.",
                checked = settings.hapticsEnabled,
                onCheckedChange = onHapticsChange,
                testTag = SettingsTestTags.HAPTICS_SWITCH,
            )

            HorizontalDivider()

            LabelledRow("Units") {
                SingleChoiceSegmentedButtonRow {
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
            }
        }

        SectionCard("Scanning") {
            Text(
                text = "Photo quality",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag(SettingsTestTags.IMAGE_QUALITY),
            ) {
                ImageQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = settings.imageQuality == quality,
                        onClick = { onImageQualityChange(quality) },
                        label = { Text(quality.label) },
                    )
                }
            }
            Text(
                text = "Higher detail uploads more data and takes longer on a slow connection; " +
                    "it rarely changes what the recogniser sees.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            ModelPicker(selected = settings.geminiModel, onSelect = onGeminiModelChange)

            HorizontalDivider()

            ToggleRow(
                title = "Retry offline scans",
                subtitle = "Photos taken without a connection are analysed once you are back online.",
                checked = settings.reanalyseQueuedPhotos,
                onCheckedChange = onReanalyseChange,
                testTag = SettingsTestTags.REANALYSE_SWITCH,
            )

            ToggleRow(
                title = "Local-only mode",
                subtitle = "Never send photos to the cloud. On-device results only.",
                checked = settings.localOnlyMode,
                onCheckedChange = onLocalOnlyChange,
                testTag = SettingsTestTags.LOCAL_ONLY_SWITCH,
            )
        }

        DataSection(
            dataTask = dataTask,
            onExport = onExport,
            onImport = onImport,
            onDeleteEverything = onDeleteEverything,
            onDismissDataTask = onDismissDataTask,
        )

        AboutSection()
    }
}

/**
 * What the app does with your information, reachable from inside the app rather than only from a
 * store listing -- which is both what Play asks for and where someone would actually look.
 */
@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current

    SectionCard("About") {
        Text(
            text = "Your diary and photos stay on this device. A meal photo is sent to Google's " +
                "Gemini API only while a scan is running, and Local-only mode stops even that.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
            modifier = Modifier.testTag(SettingsTestTags.PRIVACY_POLICY),
        ) {
            Text("Privacy policy")
        }
    }
}

private const val PRIVACY_POLICY_URL =
    "https://github.com/oliverinhalo/food-tracker-app/blob/main/PRIVACY.md"

/** A titled card, since every section below the keys is one. */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LabelledRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(selected: GeminiModelChoice, onSelect: (GeminiModelChoice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(SettingsTestTags.GEMINI_MODEL),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GeminiModelChoice.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Take it with you, put it back, or erase it.
 *
 * An uninstall, a lost phone or a change of signing key wipes app-private storage with no warning,
 * and months of logging is not something to lose to any of those. Erase is here because a person
 * is entitled to remove what an app holds about them, and because Play requires the path to exist.
 */
@Composable
private fun DataSection(
    dataTask: DataTaskState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteEverything: () -> Unit,
    onDismissDataTask: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val working = dataTask is DataTaskState.Working

    SectionCard("Your data") {
        Text(
            text = "An export holds your meals and their numbers. Photos and API keys are left out: " +
                "photos dominate the size, and a key does not belong in a file you email yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onExport,
                enabled = !working,
                modifier = Modifier.testTag(SettingsTestTags.EXPORT),
            ) {
                Text("Export")
            }
            OutlinedButton(
                onClick = onImport,
                enabled = !working,
                modifier = Modifier.testTag(SettingsTestTags.IMPORT),
            ) {
                Text("Import")
            }
        }

        when (dataTask) {
            is DataTaskState.Working -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text("Working…", style = MaterialTheme.typography.bodySmall)
            }

            is DataTaskState.Done, is DataTaskState.Failed -> {
                val message = (dataTask as? DataTaskState.Done)?.message
                    ?: (dataTask as DataTaskState.Failed).message
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dataTask is DataTaskState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(SettingsTestTags.DATA_TASK_MESSAGE),
                    )
                    TextButton(onClick = onDismissDataTask) { Text("OK") }
                }
            }

            DataTaskState.Idle -> Unit
        }

        HorizontalDivider()

        TextButton(
            onClick = { confirming = true },
            enabled = !working,
            modifier = Modifier.testTag(SettingsTestTags.DELETE_ALL),
        ) {
            Text("Delete all my data", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "This removes every meal, photo, saved food, learned portion and API key from " +
                        "this device. It cannot be undone. Export first if you want a copy.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDeleteEverything()
                    },
                    modifier = Modifier.testTag(SettingsTestTags.DELETE_CONFIRM),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun KeySection(
    title: String,
    explanation: String,
    hasKey: Boolean,
    testTag: String,
    revealed: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

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
                    TextButton(
                        onClick = onReveal,
                        modifier = Modifier.testTag(SettingsTestTags.REVEAL_KEY),
                    ) {
                        Text("Show saved key")
                    }
                    TextButton(onClick = onClear) { Text("Remove") }
                }
            }

            revealed?.let { key ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectionContainer {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(SettingsTestTags.REVEALED_KEY),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(key)) }) {
                            Text("Copy")
                        }
                        TextButton(onClick = onHide) { Text("Hide") }
                    }
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
