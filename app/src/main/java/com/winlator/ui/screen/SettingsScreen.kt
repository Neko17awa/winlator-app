package com.winlator.ui.screen

import android.app.Activity
import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Bundle
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.winlator.MainActivity
import com.winlator.R
import com.winlator.box64.Box64Preset
import com.winlator.box64.Box64PresetManager
import com.winlator.core.AppUtils
import com.winlator.core.LocaleHelper
import com.winlator.ui.component.SectionCard
import com.winlator.ui.component.WinlatorColorPicker
import com.winlator.ui.component.WinlatorDropdown
import com.winlator.ui.component.WinlatorSlider
import com.winlator.widget.LogView
import com.winlator.xenvironment.RootFSInstaller
import java.io.File

private const val APP_THEME_LIGHT = 0
private const val APP_THEME_DARK = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onTitleChange: (String) -> Unit,
    onNavigateToContainers: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val title = stringResource(R.string.settings)
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    LaunchedEffect(Unit) { onTitleChange(title) }

    // ── Mouse ──
    var cursorSpeed by remember {
        mutableFloatStateOf(preferences.getFloat("cursor_speed", 1.0f) * 100f)
    }
    var cursorScale by remember {
        mutableFloatStateOf(preferences.getFloat("cursor_scale", 1.0f) * 100f)
    }
    var cursorColor by remember {
        mutableIntStateOf(preferences.getInt("cursor_color", 0xffffff))
    }
    var moveCursorToTouchpoint by remember {
        mutableStateOf(preferences.getBoolean("move_cursor_to_touchpoint", false))
    }
    var capturePointerOnExternalMouse by remember {
        mutableStateOf(preferences.getBoolean("capture_pointer_on_external_mouse", true))
    }

    // ── Game Controllers ──
    var preferredInputApi by remember {
        mutableIntStateOf(preferences.getInt("preferred_input_api", 0))
    }
    val gamepadConfigs = remember {
        mutableStateListOf<String>().apply {
            for (i in 0 until 4) add(preferences.getString("gamepad_player$i", "") ?: "")
        }
    }

    // ── Sound ──
    val midiDeviceItems = remember { buildMidiDeviceList(context) }
    var midiInputDeviceIndex by remember {
        mutableIntStateOf(resolveMidiSelectedIndex(
            preferences.getString("midi_input_device", "auto") ?: "auto",
            midiDeviceItems,
        ))
    }
    var soundfontIndex by remember { mutableIntStateOf(0) }
    val soundfontItems = remember { listOf("Default") }

    // ── System ──
    var appTheme by remember {
        mutableIntStateOf(preferences.getInt("app_theme", APP_THEME_DARK))
    }
    val languageItems = remember { listOf("English", "Português", "Русский") }
    val initialLcIndex = remember { LocaleHelper.getLocaleIndex(context) }
    var languageIndex by remember { mutableIntStateOf(initialLcIndex) }
    var openAndroidBrowser by remember {
        mutableStateOf(preferences.getBoolean("open_android_browser_from_wine", true))
    }
    var useAndroidClipboard by remember {
        mutableStateOf(preferences.getBoolean("use_android_clipboard_on_wine", false))
    }

    // ── Advanced ──
    var box64VersionIndex by remember { mutableIntStateOf(0) }
    val box64VersionItems = remember { listOf("Default") }

    val box64Presets = remember { mutableStateListOf<Box64Preset>() }
    LaunchedEffect(Unit) {
        box64Presets.clear()
        box64Presets.addAll(Box64PresetManager.getPresets(context))
    }
    val savedPresetId = remember { preferences.getString("box64_preset", Box64Preset.DEFAULT) ?: Box64Preset.DEFAULT }
    var box64PresetIndex by remember {
        mutableIntStateOf(
            box64Presets.indexOfFirst { it.id == savedPresetId }.coerceAtLeast(0)
        )
    }
    LaunchedEffect(box64Presets.size) {
        box64PresetIndex = box64Presets.indexOfFirst { it.id == savedPresetId }.coerceAtLeast(0)
    }

    var enableWineDebug by remember {
        mutableStateOf(preferences.getBoolean("enable_wine_debug", false))
    }
    val box64LogItems = remember { listOf("Disable", "Minimal", "Trace") }
    var box64LogsIndex by remember {
        mutableIntStateOf(preferences.getInt("box64_logs", 0))
    }
    var saveLogsToFile by remember {
        mutableStateOf(preferences.getBoolean("save_logs_to_file", false))
    }
    val defaultLogPath = remember {
        val parent = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "Winlator",
        )
        File(parent, "logs.txt").path
    }
    var logFilePath by remember {
        mutableStateOf(preferences.getString("log_file", defaultLogPath) ?: defaultLogPath)
    }

    // ── Dialogs ──
    var showReinstallDialog by remember { mutableStateOf(false) }
    var showResetGamepadDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    saveAllPreferences(
                        context = context,
                        preferences = preferences,
                        cursorSpeed = cursorSpeed,
                        cursorScale = cursorScale,
                        cursorColor = cursorColor,
                        moveCursorToTouchpoint = moveCursorToTouchpoint,
                        capturePointerOnExternalMouse = capturePointerOnExternalMouse,
                        preferredInputApi = preferredInputApi,
                        gamepadConfigs = gamepadConfigs,
                        midiDeviceItems = midiDeviceItems,
                        midiInputDeviceIndex = midiInputDeviceIndex,
                        appTheme = appTheme,
                        languageIndex = languageIndex,
                        openAndroidBrowser = openAndroidBrowser,
                        useAndroidClipboard = useAndroidClipboard,
                        box64Presets = box64Presets,
                        box64PresetIndex = box64PresetIndex,
                        enableWineDebug = enableWineDebug,
                        box64LogsIndex = box64LogsIndex,
                        saveLogsToFile = saveLogsToFile,
                        logFilePath = logFilePath,
                        defaultLogPath = defaultLogPath,
                    )
                    val restartNeeded =
                        initialLcIndex != languageIndex ||
                        preferences.getInt("app_theme", APP_THEME_DARK) != appTheme
                    if (restartNeeded && activity != null) {
                        AppUtils.restartActivity(activity)
                    } else {
                        onNavigateToContainers()
                    }
                },
                icon = { Icon(Icons.Default.Done, contentDescription = null) },
                text = { Text(stringResource(R.string.ok)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Mouse ──
            item {
                SectionCard(title = stringResource(R.string.mouse)) {
                    WinlatorSlider(
                        label = stringResource(R.string.cursor_speed),
                        value = cursorSpeed,
                        onValueChange = { cursorSpeed = it },
                        valueRange = 10f..200f,
                        suffix = "%",
                    )
                    Spacer(Modifier.height(8.dp))
                    WinlatorSlider(
                        label = stringResource(R.string.cursor_size),
                        value = cursorScale,
                        onValueChange = { cursorScale = it },
                        valueRange = 100f..200f,
                        steps = ((200 - 100) / 5) - 1,
                        suffix = "%",
                    )
                    Spacer(Modifier.height(12.dp))
                    WinlatorColorPicker(
                        label = "Cursor Color",
                        selectedColor = cursorColor,
                        onColorSelected = { cursorColor = it },
                        palette = listOf(
                            0xffffff, 0x000000, 0x651fff, 0xffea00,
                            0xff9100, 0xf50057, 0x00b0ff, 0x1de9b6,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = stringResource(R.string.move_cursor_to_touchpoint),
                        checked = moveCursorToTouchpoint,
                        onCheckedChange = { moveCursorToTouchpoint = it },
                    )
                    LabeledSwitch(
                        label = stringResource(R.string.capture_pointer_on_external_mouse),
                        checked = capturePointerOnExternalMouse,
                        onCheckedChange = { capturePointerOnExternalMouse = it },
                    )
                }
            }

            // ── Game Controllers ──
            item {
                SectionCard(title = stringResource(R.string.game_controllers)) {
                    WinlatorDropdown(
                        label = stringResource(R.string.preferred_input_api),
                        items = listOf("Auto", "DirectInput", "XInput", "Both"),
                        selectedIndex = preferredInputApi,
                        onSelectedIndexChange = { preferredInputApi = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Gamepad Player Configs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        for (i in 0 until 4) {
                            OutlinedCard(
                                onClick = { /* GamepadPlayerConfigDialog placeholder */ },
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.player)} ${i + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showResetGamepadDialog = true }) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            // ── Sound ──
            item {
                SectionCard(title = stringResource(R.string.sound)) {
                    WinlatorDropdown(
                        label = stringResource(R.string.midi_input_device),
                        items = midiDeviceItems,
                        selectedIndex = midiInputDeviceIndex,
                        onSelectedIndexChange = { midiInputDeviceIndex = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    WinlatorDropdown(
                        label = stringResource(R.string.midi_soundfont),
                        items = soundfontItems,
                        selectedIndex = soundfontIndex,
                        onSelectedIndexChange = { soundfontIndex = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { /* SoundFontTestDialog placeholder */ }) {
                        Text("Sound Font Test")
                    }
                }
            }

            // ── System ──
            item {
                SectionCard(title = stringResource(R.string.system)) {
                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = appTheme == APP_THEME_LIGHT,
                            onClick = { appTheme = APP_THEME_LIGHT },
                            label = { Text(stringResource(R.string.light)) },
                            leadingIcon = if (appTheme == APP_THEME_LIGHT) {
                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier) }
                            } else null,
                            shape = MaterialTheme.shapes.large,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                        FilterChip(
                            selected = appTheme == APP_THEME_DARK,
                            onClick = { appTheme = APP_THEME_DARK },
                            label = { Text(stringResource(R.string.dark)) },
                            leadingIcon = if (appTheme == APP_THEME_DARK) {
                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier) }
                            } else null,
                            shape = MaterialTheme.shapes.large,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    WinlatorDropdown(
                        label = stringResource(R.string.language),
                        items = languageItems,
                        selectedIndex = languageIndex,
                        onSelectedIndexChange = { languageIndex = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = stringResource(R.string.open_android_browser_from_wine),
                        checked = openAndroidBrowser,
                        onCheckedChange = { openAndroidBrowser = it },
                    )
                    LabeledSwitch(
                        label = stringResource(R.string.use_android_clipboard_on_Wine),
                        checked = useAndroidClipboard,
                        onCheckedChange = { useAndroidClipboard = it },
                    )
                }
            }

            // ── Advanced ──
            item {
                SectionCard(title = stringResource(R.string.advanced)) {
                    WinlatorDropdown(
                        label = stringResource(R.string.box64_version),
                        items = box64VersionItems,
                        selectedIndex = box64VersionIndex,
                        onSelectedIndexChange = { box64VersionIndex = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    WinlatorDropdown(
                        label = stringResource(R.string.box64_preset),
                        items = box64Presets.map { it.name },
                        selectedIndex = box64PresetIndex,
                        onSelectedIndexChange = { box64PresetIndex = it },
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalIconButton(
                            onClick = { /* Add preset */ },
                            shape = MaterialTheme.shapes.medium,
                        ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add)) }
                        FilledTonalIconButton(
                            onClick = { /* Edit preset */ },
                            shape = MaterialTheme.shapes.medium,
                        ) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)) }
                        FilledTonalIconButton(
                            onClick = {
                                if (box64Presets.isNotEmpty()) {
                                    val preset = box64Presets[box64PresetIndex]
                                    Box64PresetManager.duplicatePreset(context, preset.id)
                                    box64Presets.clear()
                                    box64Presets.addAll(Box64PresetManager.getPresets(context))
                                    box64PresetIndex = box64Presets.size - 1
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                        ) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate)) }
                        FilledTonalIconButton(
                            onClick = {
                                if (box64Presets.isNotEmpty()) {
                                    val preset = box64Presets[box64PresetIndex]
                                    if (preset.isCustom) {
                                        Box64PresetManager.removePreset(context, preset.id)
                                        box64Presets.clear()
                                        box64Presets.addAll(Box64PresetManager.getPresets(context))
                                        box64PresetIndex = 0
                                    } else {
                                        AppUtils.showToast(context, R.string.you_cannot_remove_this_preset)
                                    }
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                        ) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = stringResource(R.string.enable_wine_debug),
                        checked = enableWineDebug,
                        onCheckedChange = { enableWineDebug = it },
                    )
                    Spacer(Modifier.height(4.dp))
                    WinlatorDropdown(
                        label = stringResource(R.string.box64_logs),
                        items = box64LogItems,
                        selectedIndex = box64LogsIndex,
                        onSelectedIndexChange = { box64LogsIndex = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = stringResource(R.string.save_logs_to_file),
                        checked = saveLogsToFile,
                        onCheckedChange = { saveLogsToFile = it },
                    )
                    AnimatedVisibility(
                        visible = saveLogsToFile,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        OutlinedTextField(
                            value = logFilePath,
                            onValueChange = { logFilePath = it },
                            label = { Text("Log File") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { showReinstallDialog = true },
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.reinstall_system_files))
                    }
                }
            }
        }
    }

    // ── Reinstall confirm dialog ──
    if (showReinstallDialog) {
        AlertDialog(
            onDismissRequest = { showReinstallDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    stringResource(R.string.reinstall_system_files),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = { Text(stringResource(R.string.do_you_want_to_reinstall_system_files)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showReinstallDialog = false
                    val mainActivity = context as? MainActivity
                    if (mainActivity != null) {
                        RootFSInstaller.install(mainActivity)
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showReinstallDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    // ── Reset gamepad configs dialog ──
    if (showResetGamepadDialog) {
        AlertDialog(
            onDismissRequest = { showResetGamepadDialog = false },
            title = { Text(stringResource(R.string.reset), fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.do_you_want_to_reset_configurations)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showResetGamepadDialog = false
                    for (i in 0 until 4) gamepadConfigs[i] = ""
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetGamepadDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun buildMidiDeviceList(context: Context): List<String> {
    val items = mutableListOf("None", "Auto")
    try {
        val mm = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        mm?.devices?.forEach { info ->
            if (info.outputPortCount > 0) {
                val props: Bundle = info.properties
                val name = props.getString(MidiDeviceInfo.PROPERTY_NAME)
                if (!name.isNullOrBlank()) items.add(name)
            }
        }
    } catch (_: Exception) { }
    return items
}

private fun resolveMidiSelectedIndex(savedValue: String, items: List<String>): Int {
    return when (savedValue) {
        "none" -> 0
        "auto" -> 1
        else -> {
            val idx = items.indexOf(savedValue)
            if (idx >= 0) idx else 1
        }
    }
}

private fun saveAllPreferences(
    context: Context,
    preferences: android.content.SharedPreferences,
    cursorSpeed: Float,
    cursorScale: Float,
    cursorColor: Int,
    moveCursorToTouchpoint: Boolean,
    capturePointerOnExternalMouse: Boolean,
    preferredInputApi: Int,
    gamepadConfigs: List<String>,
    midiDeviceItems: List<String>,
    midiInputDeviceIndex: Int,
    appTheme: Int,
    languageIndex: Int,
    openAndroidBrowser: Boolean,
    useAndroidClipboard: Boolean,
    box64Presets: List<Box64Preset>,
    box64PresetIndex: Int,
    enableWineDebug: Boolean,
    box64LogsIndex: Int,
    saveLogsToFile: Boolean,
    logFilePath: String,
    defaultLogPath: String,
) {
    val editor = preferences.edit()

    editor.putFloat("cursor_speed", cursorSpeed / 100f)
    editor.putFloat("cursor_scale", cursorScale / 100f)
    editor.putInt("cursor_color", cursorColor)
    editor.putBoolean("move_cursor_to_touchpoint", moveCursorToTouchpoint)
    editor.putBoolean("capture_pointer_on_external_mouse", capturePointerOnExternalMouse)

    editor.putInt("preferred_input_api", preferredInputApi)
    for (i in 0 until 4) {
        val key = "gamepad_player$i"
        val config = gamepadConfigs.getOrElse(i) { "" }
        if (config.isNotEmpty()) editor.putString(key, config) else editor.remove(key)
    }

    val midiValue = when (midiInputDeviceIndex) {
        0 -> "none"
        1 -> "auto"
        else -> midiDeviceItems.getOrElse(midiInputDeviceIndex) { "auto" }
    }
    editor.putString("midi_input_device", midiValue)

    editor.putInt("app_theme", appTheme)
    editor.putInt("lc_index", languageIndex)
    editor.putBoolean("open_android_browser_from_wine", openAndroidBrowser)
    editor.putBoolean("use_android_clipboard_on_wine", useAndroidClipboard)

    if (box64Presets.isNotEmpty()) {
        editor.putString("box64_preset", box64Presets[box64PresetIndex.coerceIn(box64Presets.indices)].id)
    }
    editor.putBoolean("enable_wine_debug", enableWineDebug)
    editor.putInt("box64_logs", box64LogsIndex)
    editor.putBoolean("save_logs_to_file", saveLogsToFile)

    val trimmedPath = logFilePath.trim()
    if (trimmedPath != defaultLogPath && trimmedPath.isNotEmpty()) {
        editor.putString("log_file", trimmedPath)
    } else {
        editor.remove("log_file")
    }

    editor.commit()
}
