package com.winlator.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winlator.R
import com.winlator.box64.Box64Preset
import com.winlator.box64.Box64PresetManager
import com.winlator.container.AudioDrivers
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.container.DXWrappers
import com.winlator.container.GraphicsDrivers
import com.winlator.core.KeyValueSet
import com.winlator.core.StringUtils
import com.winlator.core.WineInfo
import com.winlator.core.WineInstaller
import com.winlator.core.WineRegistryEditor
import com.winlator.core.WineThemeManager
import com.winlator.core.WineUtils
import com.winlator.ui.component.CpuListSelector
import com.winlator.ui.component.EnvVarsEditor
import com.winlator.ui.component.SectionCard
import com.winlator.ui.component.TaggedSelector
import com.winlator.ui.component.WinlatorDropdown
import com.winlator.ui.component.WinlatorSlider
import com.winlator.widget.FrameRating
import com.winlator.win32.MSLogFont
import com.winlator.win32.WinVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private val TAB_TITLES = listOf(
    R.string.wine_configuration,
    R.string.win_components,
    R.string.environment_variables,
    R.string.drives,
    R.string.advanced,
)

private val VULKAN_DRIVERS = listOf("Turnip", "Vortek")
private val OPENGL_DRIVERS = listOf("Zink", "VirGL", "Gladio")
private val VULKAN_IDS = listOf(GraphicsDrivers.TURNIP, GraphicsDrivers.VORTEK)
private val OPENGL_IDS = listOf(GraphicsDrivers.ZINK, GraphicsDrivers.VIRGL, GraphicsDrivers.GLADIO)

private val DX_D3D9_11 = listOf("WineD3D", "DXVK")
private val DX_D3D12 = listOf("VKD3D")
private val DX_D3D9_11_IDS = listOf(DXWrappers.WINED3D, DXWrappers.DXVK)
private val DX_D3D12_IDS = listOf(DXWrappers.VKD3D)

private val DRIVE_LETTERS = (0 until Container.MAX_DRIVE_LETTERS.toInt()).map { "${('D' + it)}:" }
private val MOUSE_WARP_VALUES = arrayOf("disable", "enable", "force")

private data class DriveEntry(val letter: String, val path: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerDetailScreen(
    containerId: Int,
    onTitleChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditMode = containerId > 0

    val title = stringResource(if (isEditMode) R.string.edit_container else R.string.new_container)
    LaunchedEffect(Unit) { onTitleChange(title) }

    val manager = remember { ContainerManager(context) }
    val container = remember { if (isEditMode) manager.getContainerById(containerId) else null }
    val wineInfos = remember { WineInstaller.getInstalledWineInfos(context) }

    // ── Basic info state ──
    var name by remember {
        mutableStateOf(container?.name ?: ("Container-${manager.nextContainerId}"))
    }

    val screenSizeEntries = stringArrayResource(R.array.screen_size_entries).toList()
    val initScreenSize = container?.screenSize ?: Container.DEFAULT_SCREEN_SIZE
    val initScreenIdx = remember {
        val idx = screenSizeEntries.indexOfFirst {
            StringUtils.parseIdentifier(it) == initScreenSize
        }
        if (idx >= 0) idx else 0
    }
    var screenSizeIdx by remember { mutableIntStateOf(initScreenIdx) }
    var customWidth by remember {
        mutableStateOf(if (initScreenIdx == 0) initScreenSize.substringBefore("x") else "1280")
    }
    var customHeight by remember {
        mutableStateOf(if (initScreenIdx == 0) initScreenSize.substringAfter("x") else "720")
    }

    // Wine version
    var wineVersionIdx by remember {
        val initIdx = if (isEditMode && container != null) {
            val wineId = container.wineVersion
            wineInfos.indexOfFirst { it.identifier() == wineId }.coerceAtLeast(0)
        } else 0
        mutableIntStateOf(initIdx)
    }

    // Graphics driver
    val initDriverIds = remember {
        GraphicsDrivers.parseIdentifiers(
            container?.graphicsDriver ?: GraphicsDrivers.getDefaultDriver(context)
        )
    }
    var vulkanIdx by remember {
        mutableIntStateOf(VULKAN_IDS.indexOf(initDriverIds[0]).coerceAtLeast(0))
    }
    var openglIdx by remember {
        mutableIntStateOf(OPENGL_IDS.indexOf(initDriverIds[1]).coerceAtLeast(0))
    }

    // DX wrapper
    val initDxWrapper = container?.getDXWrapper() ?: Container.DEFAULT_DXWRAPPER
    var dxD3d911Idx by remember {
        mutableIntStateOf(DX_D3D9_11_IDS.indexOf(initDxWrapper).coerceAtLeast(0))
    }
    var dxD3d12Idx by remember {
        mutableIntStateOf(DX_D3D12_IDS.indexOf(initDxWrapper).coerceAtLeast(0))
    }

    // Audio driver
    val audioDriverEntries = stringArrayResource(R.array.audio_driver_entries).toList()
    var audioDriverIdx by remember {
        val initAudio = container?.audioDriver ?: Container.DEFAULT_AUDIO_DRIVER
        val idx = when (initAudio) {
            AudioDrivers.PULSEAUDIO -> 1
            else -> 0
        }
        mutableIntStateOf(idx)
    }

    // HUD mode
    val hudModeEntries = stringArrayResource(R.array.hud_mode_entries).toList()
    var hudModeIdx by remember {
        mutableIntStateOf(container?.let { it.getHUDMode().toInt() } ?: FrameRating.Mode.DISABLED.ordinal)
    }

    // ── Tab 1: Wine Configuration state ──
    val initThemeInfo = remember {
        WineThemeManager.ThemeInfo(container?.desktopTheme ?: WineThemeManager.DEFAULT_DESKTOP_THEME)
    }
    var desktopThemeIdx by remember {
        mutableIntStateOf(initThemeInfo.theme.ordinal)
    }
    val bgTypeEntries = stringArrayResource(R.array.desktop_background_type_entries).toList()
    var bgTypeIdx by remember {
        mutableIntStateOf(initThemeInfo.backgroundType.ordinal)
    }

    val systemFontEntries = stringArrayResource(R.array.system_font_entries).toList()
    var systemFontIdx by remember { mutableIntStateOf(0) }
    var logPixels by remember { mutableFloatStateOf(96f) }

    val mouseWarpEntries = listOf(
        stringResource(R.string.disable),
        stringResource(R.string.enable),
        stringResource(R.string.force),
    )
    var mouseWarpIdx by remember { mutableIntStateOf(0) }

    // Read registry values for wine configuration tab
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (isEditMode && container != null) {
                val userRegFile = File(container.rootDir, ".wine/user.reg")
                try {
                    WineRegistryEditor(userRegFile).use { reg ->
                        val fontBytes = reg.getHexValues(
                            "Control Panel\\Desktop\\WindowMetrics", "CaptionFont"
                        )
                        val faceName = MSLogFont().fromByteArray(fontBytes).faceName
                        val fontIdx = systemFontEntries.indexOfFirst {
                            it.equals(faceName, ignoreCase = true)
                        }
                        if (fontIdx >= 0) systemFontIdx = fontIdx

                        val dpi = reg.getDwordValue(
                            "Control Panel\\Desktop", "LogPixels", 96
                        )
                        logPixels = dpi.toFloat()

                        val warp = reg.getStringValue(
                            "Software\\Wine\\DirectInput", "MouseWarpOverride", "disable"
                        )
                        mouseWarpIdx = MOUSE_WARP_VALUES.indexOf(warp).coerceAtLeast(0)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    // ── Tab 2: Win Components state ──
    val initWincomponents = container?.winComponents ?: Container.DEFAULT_WINCOMPONENTS
    val winComponentList = remember {
        val result = mutableListOf<Pair<String, Int>>()
        for (kv in KeyValueSet(initWincomponents)) {
            result.add(kv[0] to (kv[1].toIntOrNull() ?: 0).coerceIn(0, 1))
        }
        result
    }
    val winComponentValues = remember {
        mutableStateListOf(*winComponentList.map { it.second }.toTypedArray())
    }
    val winComponentNames = remember { winComponentList.map { it.first } }
    val winComponentEntries = stringArrayResource(R.array.wincomponent_entries).toList()

    // ── Tab 3: Env Vars state ──
    var envVars by remember {
        mutableStateOf(container?.envVars ?: Container.DEFAULT_ENV_VARS)
    }

    // ── Tab 4: Drives state ──
    val driveEntries = remember {
        val list = mutableStateListOf<DriveEntry>()
        val initDrives = container?.drives ?: Container.DEFAULT_DRIVES
        for (drive in Container.drivesIterator(initDrives)) {
            list.add(DriveEntry(drive.letter, drive.path))
        }
        list
    }

    // ── Tab 5: Advanced state ──
    val box64Presets = remember { Box64PresetManager.getPresets(context) }
    var box64PresetIdx by remember {
        val initPreset = container?.box64Preset ?: Box64Preset.DEFAULT
        val idx = box64Presets.indexOfFirst { it.id == initPreset }.coerceAtLeast(0)
        mutableIntStateOf(idx)
    }

    val startupEntries = stringArrayResource(R.array.startup_selection_entries).toList()
    var startupIdx by remember {
        mutableIntStateOf(
            container?.startupSelection?.toInt() ?: Container.STARTUP_SELECTION_ESSENTIAL.toInt()
        )
    }

    val winVersions = remember { WinVersions.getWinVersions() }
    var winVersionIdx by remember { mutableIntStateOf(-1) }
    var winVersionLoaded by remember { mutableStateOf(false) }

    var cpuList by remember {
        mutableStateOf(
            container?.getCPUList(true) ?: Container.getFallbackCPUList()
        )
    }
    var cpuListWoW64 by remember {
        mutableStateOf(
            container?.getCPUListWoW64(true) ?: Container.getFallbackCPUListWoW64()
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // ── Helpers ──
    fun buildScreenSize(): String {
        return if (screenSizeIdx == 0) {
            val w = customWidth.trim()
            val h = customHeight.trim()
            if (w.matches(Regex("[0-9]+")) && h.matches(Regex("[0-9]+")) &&
                w.toInt() % 2 == 0 && h.toInt() % 2 == 0
            ) "${w}x${h}" else Container.DEFAULT_SCREEN_SIZE
        } else {
            StringUtils.parseIdentifier(screenSizeEntries[screenSizeIdx])
        }
    }

    fun buildGraphicsDriver(): String = "${VULKAN_IDS[vulkanIdx]},${OPENGL_IDS[openglIdx]}"

    fun buildDxWrapper(): String = if (dxD3d911Idx < DX_D3D9_11_IDS.size) {
        DX_D3D9_11_IDS[dxD3d911Idx]
    } else DX_D3D12_IDS[dxD3d12Idx]

    fun buildAudioDriverId(): String = when (audioDriverIdx) {
        1 -> AudioDrivers.PULSEAUDIO
        else -> AudioDrivers.ALSA
    }

    fun buildWinComponents(): String = winComponentNames.mapIndexed { i, n ->
        "$n=${winComponentValues[i]}"
    }.joinToString(",")

    fun buildDrives(): String = driveEntries.joinToString("") { "${it.letter}:${it.path}" }

    fun buildDesktopTheme(): String {
        val theme = WineThemeManager.Theme.entries[desktopThemeIdx]
        val bgType = WineThemeManager.BackgroundType.entries[bgTypeIdx]
        return "$theme,$bgType,#0277bd"
    }

    fun onConfirm() {
        scope.launch(Dispatchers.IO) {
            if (isEditMode && container != null) {
                container.name = name
                container.screenSize = buildScreenSize()
                container.envVars = envVars
                container.setCPUList(cpuList)
                container.setCPUListWoW64(cpuListWoW64)
                container.graphicsDriver = buildGraphicsDriver()
                container.setDXWrapper(buildDxWrapper())
                container.audioDriver = buildAudioDriverId()
                container.winComponents = buildWinComponents()
                container.drives = buildDrives()
                container.setHUDMode(hudModeIdx.toByte())
                container.startupSelection = startupIdx.toByte()
                container.box64Preset = box64Presets[box64PresetIdx].id
                container.desktopTheme = buildDesktopTheme()
                container.saveData()

                saveWineRegistryKeys(container, systemFontEntries[systemFontIdx], logPixels.toInt(), mouseWarpIdx, winVersionIdx, winVersionLoaded)
                withContext(Dispatchers.Main) { onNavigateBack() }
            } else {
                val data = JSONObject().apply {
                    put("name", name)
                    put("screenSize", buildScreenSize())
                    put("envVars", envVars)
                    put("cpuList", cpuList)
                    put("cpuListWoW64", cpuListWoW64)
                    put("graphicsDriver", buildGraphicsDriver())
                    put("dxwrapper", buildDxWrapper())
                    put("audioDriver", buildAudioDriverId())
                    put("wincomponents", buildWinComponents())
                    put("drives", buildDrives())
                    put("hudMode", hudModeIdx)
                    put("startupSelection", startupIdx)
                    put("box64Preset", box64Presets[box64PresetIdx].id)
                    put("desktopTheme", buildDesktopTheme())
                    if (wineInfos.size > 1) {
                        put("wineVersion", wineInfos[wineVersionIdx].identifier())
                    }
                }
                manager.createContainerAsync(data) { created ->
                    if (created != null) {
                        saveWineRegistryKeys(created, systemFontEntries[systemFontIdx], logPixels.toInt(), mouseWarpIdx, winVersionIdx, winVersionLoaded)
                    }
                    onNavigateBack()
                }
            }
        }
    }

    // ── UI ──
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onConfirm() },
                icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                text = { Text(stringResource(R.string.ok)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Basic Info Section ──
            SectionCard(
                title = stringResource(R.string.container),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.container)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                WinlatorDropdown(
                    label = stringResource(R.string.screen_size),
                    items = screenSizeEntries,
                    selectedIndex = screenSizeIdx,
                    onSelectedIndexChange = { screenSizeIdx = it },
                )
                if (screenSizeIdx == 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = customWidth,
                            onValueChange = { customWidth = it },
                            label = { Text("Width") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = { customHeight = it },
                            label = { Text("Height") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (wineInfos.size > 1) {
                    WinlatorDropdown(
                        label = stringResource(R.string.wine_version),
                        items = wineInfos.map { it.toString() },
                        selectedIndex = wineVersionIdx,
                        onSelectedIndexChange = { if (!isEditMode) wineVersionIdx = it },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                TaggedSelector(
                    label = stringResource(R.string.graphics_driver) + " (Vulkan)",
                    items = VULKAN_DRIVERS,
                    selectedIndex = vulkanIdx,
                    onItemSelected = { vulkanIdx = it },
                )
                Spacer(Modifier.height(8.dp))
                TaggedSelector(
                    label = stringResource(R.string.graphics_driver) + " (OpenGL)",
                    items = OPENGL_DRIVERS,
                    selectedIndex = openglIdx,
                    onItemSelected = { openglIdx = it },
                )
                Spacer(Modifier.height(12.dp))

                TaggedSelector(
                    label = stringResource(R.string.dxwrapper) + " (D3D9-11)",
                    items = DX_D3D9_11,
                    selectedIndex = dxD3d911Idx,
                    onItemSelected = { dxD3d911Idx = it },
                )
                Spacer(Modifier.height(8.dp))
                TaggedSelector(
                    label = stringResource(R.string.dxwrapper) + " (D3D12)",
                    items = DX_D3D12,
                    selectedIndex = dxD3d12Idx,
                    onItemSelected = { dxD3d12Idx = it },
                )
                Spacer(Modifier.height(12.dp))

                WinlatorDropdown(
                    label = stringResource(R.string.audio_driver),
                    items = audioDriverEntries,
                    selectedIndex = audioDriverIdx,
                    onSelectedIndexChange = { audioDriverIdx = it },
                )
                Spacer(Modifier.height(12.dp))

                WinlatorDropdown(
                    label = stringResource(R.string.hud_mode),
                    items = hudModeEntries,
                    selectedIndex = hudModeIdx,
                    onSelectedIndexChange = { hudModeIdx = it },
                )
            }

            // ── Tab Row ──
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                TAB_TITLES.forEachIndexed { index, resId ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 4 && !winVersionLoaded) {
                                winVersionLoaded = true
                                if (isEditMode && container != null) {
                                    scope.launch(Dispatchers.IO) {
                                        val pos = loadWinVersionFromRegistry(container, winVersions)
                                        withContext(Dispatchers.Main) { winVersionIdx = pos }
                                    }
                                } else {
                                    val defaultIdx = winVersions.indexOfFirst {
                                        it.version == WinVersions.DEFAULT_VERSION
                                    }.coerceAtLeast(0)
                                    winVersionIdx = defaultIdx
                                }
                            }
                        },
                        text = {
                            Text(
                                stringResource(resId),
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // ── Tab Content ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .animateContentSize(),
            ) {
                when (selectedTab) {
                    0 -> WineConfigurationTab(
                        desktopThemeIdx = desktopThemeIdx,
                        onDesktopThemeChange = { desktopThemeIdx = it },
                        bgTypeEntries = bgTypeEntries,
                        bgTypeIdx = bgTypeIdx,
                        onBgTypeChange = { bgTypeIdx = it },
                        systemFontEntries = systemFontEntries,
                        systemFontIdx = systemFontIdx,
                        onSystemFontChange = { systemFontIdx = it },
                        logPixels = logPixels,
                        onLogPixelsChange = { logPixels = it },
                        mouseWarpEntries = mouseWarpEntries,
                        mouseWarpIdx = mouseWarpIdx,
                        onMouseWarpChange = { mouseWarpIdx = it },
                    )
                    1 -> WinComponentsTab(
                        names = winComponentNames,
                        values = winComponentValues,
                        entries = winComponentEntries,
                        context = context,
                    )
                    2 -> EnvVarsTab(
                        envVars = envVars,
                        onEnvVarsChanged = { envVars = it },
                    )
                    3 -> DrivesTab(
                        entries = driveEntries,
                    )
                    4 -> AdvancedTab(
                        box64Presets = box64Presets.map { it.name },
                        box64PresetIdx = box64PresetIdx,
                        onBox64PresetChange = { box64PresetIdx = it },
                        startupEntries = startupEntries,
                        startupIdx = startupIdx,
                        onStartupChange = { startupIdx = it },
                        winVersions = winVersions.map { it.description },
                        winVersionIdx = winVersionIdx,
                        onWinVersionChange = { winVersionIdx = it },
                        cpuList = cpuList,
                        onCpuListChange = { cpuList = it },
                        cpuListWoW64 = cpuListWoW64,
                        onCpuListWoW64Change = { cpuListWoW64 = it },
                    )
                }
            }

            Spacer(Modifier.height(88.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Tab 1: Wine Configuration
// ═══════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WineConfigurationTab(
    desktopThemeIdx: Int,
    onDesktopThemeChange: (Int) -> Unit,
    bgTypeEntries: List<String>,
    bgTypeIdx: Int,
    onBgTypeChange: (Int) -> Unit,
    systemFontEntries: List<String>,
    systemFontIdx: Int,
    onSystemFontChange: (Int) -> Unit,
    logPixels: Float,
    onLogPixelsChange: (Float) -> Unit,
    mouseWarpEntries: List<String>,
    mouseWarpIdx: Int,
    onMouseWarpChange: (Int) -> Unit,
) {
    val themeLabels = listOf("Light", "Dark")

    SectionCard(title = stringResource(R.string.wine_configuration)) {
        Text(
            text = "Desktop Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            themeLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeLabels.size),
                    onClick = { onDesktopThemeChange(index) },
                    selected = desktopThemeIdx == index,
                ) {
                    Text(label)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        WinlatorDropdown(
            label = "Desktop Background Type",
            items = bgTypeEntries,
            selectedIndex = bgTypeIdx,
            onSelectedIndexChange = onBgTypeChange,
        )

        Spacer(Modifier.height(12.dp))

        WinlatorDropdown(
            label = stringResource(R.string.system_font),
            items = systemFontEntries,
            selectedIndex = systemFontIdx,
            onSelectedIndexChange = onSystemFontChange,
        )

        Spacer(Modifier.height(12.dp))

        WinlatorSlider(
            label = stringResource(R.string.dpi_font_size),
            value = logPixels,
            onValueChange = onLogPixelsChange,
            valueRange = 72f..192f,
            steps = (192 - 72) / 4 - 1,
        )

        Spacer(Modifier.height(12.dp))

        WinlatorDropdown(
            label = stringResource(R.string.mouse_warp_override),
            items = mouseWarpEntries,
            selectedIndex = mouseWarpIdx,
            onSelectedIndexChange = onMouseWarpChange,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Tab 2: Win Components
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun WinComponentsTab(
    names: List<String>,
    values: MutableList<Int>,
    entries: List<String>,
    context: android.content.Context,
) {
    val directXNames = names.filter { it.startsWith("direct") || it.startsWith("x") }
    val generalNames = names.filter { !it.startsWith("direct") && !it.startsWith("x") }

    SectionCard(title = "DirectX") {
        directXNames.forEach { componentName ->
            val idx = names.indexOf(componentName)
            if (idx >= 0) {
                WinComponentRow(
                    displayName = StringUtils.getString(context, componentName) ?: componentName,
                    entries = entries,
                    selectedIndex = values[idx],
                    onSelectedIndexChange = { values[idx] = it },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    SectionCard(title = "General") {
        generalNames.forEach { componentName ->
            val idx = names.indexOf(componentName)
            if (idx >= 0) {
                WinComponentRow(
                    displayName = StringUtils.getString(context, componentName) ?: componentName,
                    entries = entries,
                    selectedIndex = values[idx],
                    onSelectedIndexChange = { values[idx] = it },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WinComponentRow(
    displayName: String,
    entries: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        WinlatorDropdown(
            label = "",
            items = entries,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = Modifier.weight(1.2f),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Tab 3: Env Vars
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun EnvVarsTab(
    envVars: String,
    onEnvVarsChanged: (String) -> Unit,
) {
    SectionCard(title = stringResource(R.string.environment_variables)) {
        EnvVarsEditor(
            envVars = envVars,
            onEnvVarsChanged = onEnvVarsChanged,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Tab 4: Drives
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun DrivesTab(
    entries: MutableList<DriveEntry>,
) {
    SectionCard(title = stringResource(R.string.drives)) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.no_items_to_display),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        entries.forEachIndexed { index, entry ->
            ElevatedCard(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                ) {
                    val letterIdx = DRIVE_LETTERS.indexOf("${entry.letter}:")
                        .coerceAtLeast(0)
                    WinlatorDropdown(
                        label = "",
                        items = DRIVE_LETTERS,
                        selectedIndex = letterIdx,
                        onSelectedIndexChange = { newIdx ->
                            val newLetter = DRIVE_LETTERS[newIdx].removeSuffix(":")
                            entries[index] = entry.copy(letter = newLetter)
                        },
                        modifier = Modifier.width(90.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = entry.path,
                        onValueChange = { newPath ->
                            entries[index] = entry.copy(path = newPath)
                        },
                        label = { Text("Path") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { entries.removeAt(index) }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        FilledTonalButton(
            onClick = {
                if (entries.size < Container.MAX_DRIVE_LETTERS) {
                    val nextLetter = DRIVE_LETTERS.getOrElse(entries.size) { "D:" }
                        .removeSuffix(":")
                    entries.add(DriveEntry(nextLetter, ""))
                }
            },
            enabled = entries.size < Container.MAX_DRIVE_LETTERS,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Drive")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Tab 5: Advanced
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun AdvancedTab(
    box64Presets: List<String>,
    box64PresetIdx: Int,
    onBox64PresetChange: (Int) -> Unit,
    startupEntries: List<String>,
    startupIdx: Int,
    onStartupChange: (Int) -> Unit,
    winVersions: List<String>,
    winVersionIdx: Int,
    onWinVersionChange: (Int) -> Unit,
    cpuList: String,
    onCpuListChange: (String) -> Unit,
    cpuListWoW64: String,
    onCpuListWoW64Change: (String) -> Unit,
) {
    SectionCard(title = stringResource(R.string.advanced)) {
        WinlatorDropdown(
            label = stringResource(R.string.box64_preset),
            items = box64Presets,
            selectedIndex = box64PresetIdx,
            onSelectedIndexChange = onBox64PresetChange,
        )

        Spacer(Modifier.height(12.dp))

        WinlatorDropdown(
            label = stringResource(R.string.startup_selection),
            items = startupEntries,
            selectedIndex = startupIdx,
            onSelectedIndexChange = onStartupChange,
        )

        Spacer(Modifier.height(12.dp))

        if (winVersionIdx >= 0) {
            WinlatorDropdown(
                label = "Windows Version",
                items = winVersions,
                selectedIndex = winVersionIdx,
                onSelectedIndexChange = onWinVersionChange,
            )
        } else {
            Text(
                text = "Loading Windows versions...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        CpuListSelector(
            label = stringResource(R.string.processor_affinity),
            checkedCpus = cpuList,
            onCpuListChanged = onCpuListChange,
        )

        Spacer(Modifier.height(12.dp))

        CpuListSelector(
            label = stringResource(R.string.processor_affinity_32_bit_apps),
            checkedCpus = cpuListWoW64,
            onCpuListChanged = onCpuListWoW64Change,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Utility functions
// ═══════════════════════════════════════════════════════════════════════════

private fun loadWinVersionFromRegistry(
    container: Container,
    winVersions: Array<WinVersions.WinVersion>,
): Int {
    val systemRegFile = File(container.rootDir, ".wine/system.reg")
    if (!systemRegFile.isFile) {
        return winVersions.indexOfFirst { it.version == WinVersions.DEFAULT_VERSION }.coerceAtLeast(0)
    }

    var position = winVersions.indexOfFirst { it.version == WinVersions.DEFAULT_VERSION }.coerceAtLeast(0)
    try {
        WineRegistryEditor(systemRegFile).use { reg ->
            var productName = reg.getStringValue(
                "Software\\Microsoft\\Windows NT\\CurrentVersion", "ProductName", ""
            )
            productName = productName.replace(Regex("(Microsoft )|(  Pro)"), "")
            for (i in winVersions.indices) {
                if (winVersions[i].description == productName) {
                    position = i
                    break
                }
            }
        }
    } catch (_: Exception) {
    }
    return position
}

private fun saveWineRegistryKeys(
    container: Container,
    systemFont: String,
    logPixels: Int,
    mouseWarpIdx: Int,
    winVersionIdx: Int,
    winVersionLoaded: Boolean,
) {
    val userRegFile = File(container.rootDir, ".wine/user.reg")
    try {
        WineRegistryEditor(userRegFile).use { reg ->
            WineUtils.setSystemFont(reg, systemFont)
            reg.setDwordValue("Control Panel\\Desktop", "LogPixels", logPixels)
            reg.setStringValue(
                "Software\\Wine\\DirectInput",
                "MouseWarpOverride",
                MOUSE_WARP_VALUES[mouseWarpIdx],
            )
            reg.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl")
            reg.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled")
        }
    } catch (_: Exception) {
    }

    if (winVersionLoaded && winVersionIdx >= 0) {
        WineUtils.setWinVersion(container, winVersionIdx)
    }
}
