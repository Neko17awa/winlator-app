package com.winlator.ui.screen

import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.winlator.ControlsEditorActivity
import com.winlator.ExternalControllerBindingsActivity
import com.winlator.R
import com.winlator.core.AppUtils
import com.winlator.core.FileUtils
import com.winlator.core.HttpUtils
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.ExternalController
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import com.winlator.ui.component.SectionCard
import com.winlator.ui.component.WinlatorDropdown
import com.winlator.ui.component.WinlatorSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

private const val INPUT_CONTROLS_URL =
    "https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/%s"

@Composable
fun InputControlsScreen(
    selectedProfileId: Int,
    onTitleChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val title = stringResource(R.string.input_controls)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { onTitleChange(title) }

    val manager = remember { InputControlsManager(context) }
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val profiles = remember { mutableStateListOf<ControlsProfile>() }
    var currentProfile by remember { mutableStateOf<ControlsProfile?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    fun reloadProfiles() {
        manager.loadProfiles(false)
        profiles.clear()
        profiles.addAll(manager.getProfiles())
    }

    LaunchedEffect(Unit) {
        reloadProfiles()
        if (selectedProfileId > 0) {
            currentProfile = manager.getProfile(selectedProfileId)
        }
    }

    val dropdownItems = remember(profiles.toList(), revision) {
        buildList {
            add("-- ${context.getString(R.string.select_profile)} --")
            profiles.forEach { add(it.name) }
        }
    }
    val selectedDropdownIndex = remember(currentProfile, profiles.toList(), revision) {
        val idx = profiles.indexOf(currentProfile)
        if (idx >= 0) idx + 1 else 0
    }

    var cursorSpeed by remember(currentProfile, revision) {
        mutableFloatStateOf((currentProfile?.cursorSpeed ?: 1f) * 100f)
    }
    var disableMouseInput by remember(currentProfile, revision) {
        mutableStateOf(currentProfile?.isDisableMouseInput ?: false)
    }
    var overlayOpacity by remember {
        mutableFloatStateOf(
            preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY) * 100f
        )
    }

    val controllers = remember { mutableStateListOf<ExternalController>() }
    fun reloadControllers() {
        controllers.clear()
        val connected = ExternalController.getControllers()
        val profileControllers =
            currentProfile?.loadControllers() ?: arrayListOf()
        val merged = ArrayList(profileControllers)
        for (c in connected) {
            if (!merged.contains(c)) merged.add(c)
        }
        controllers.addAll(merged)
    }
    LaunchedEffect(currentProfile, revision) { reloadControllers() }

    // ── Dialogs ──
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var showDownloadListDialog by remember { mutableStateOf(false) }
    var downloadListItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var downloadListLoading by remember { mutableStateOf(false) }

    // ── File picker ──
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = FileUtils.readString(context, uri)
                val imported = manager.importProfile(JSONObject(json))
                if (imported != null) {
                    currentProfile = imported
                    reloadProfiles()
                    revision++
                }
            } catch (_: Exception) {
                AppUtils.showToast(context, R.string.unable_to_import_profile)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ═══════════════════════════════════════
        // Profile Selection
        // ═══════════════════════════════════════
        item {
            SectionCard(title = stringResource(R.string.select_profile)) {
                WinlatorDropdown(
                    label = stringResource(R.string.select_profile),
                    items = dropdownItems,
                    selectedIndex = selectedDropdownIndex,
                    onSelectedIndexChange = { index ->
                        currentProfile = if (index > 0) profiles[index - 1] else null
                        revision++
                    },
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                    FilledTonalIconButton(onClick = {
                        if (currentProfile != null) showEditDialog = true
                        else AppUtils.showToast(context, R.string.no_profile_selected)
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    FilledTonalIconButton(onClick = {
                        if (currentProfile != null) {
                            currentProfile = manager.duplicateProfile(currentProfile!!)
                            reloadProfiles()
                            revision++
                        } else AppUtils.showToast(context, R.string.no_profile_selected)
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                    }
                    FilledTonalIconButton(onClick = {
                        if (currentProfile != null) {
                            manager.removeProfile(currentProfile!!)
                            currentProfile = null
                            reloadProfiles()
                            revision++
                        } else AppUtils.showToast(context, R.string.no_profile_selected)
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                    FilledTonalIconButton(onClick = { showImportOptionsDialog = true }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Import")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (currentProfile != null) {
                                val exported = manager.exportProfile(currentProfile!!)
                                if (exported != null) {
                                    val path = exported.path.substring(
                                        exported.path.indexOf(Environment.DIRECTORY_DOWNLOADS)
                                    )
                                    AppUtils.showToast(
                                        context,
                                        context.getString(R.string.profile_exported_to) + " " + path
                                    )
                                }
                            } else AppUtils.showToast(context, R.string.no_profile_selected)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_profile))
                    }

                    FilledTonalButton(
                        onClick = {
                            if (currentProfile != null) {
                                val intent = Intent(context, ControlsEditorActivity::class.java)
                                intent.putExtra("profile_id", currentProfile!!.id)
                                context.startActivity(intent)
                            } else AppUtils.showToast(context, R.string.no_profile_selected)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.controls_editor))
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // Profile Settings (only when profile selected)
        // ═══════════════════════════════════════
        item {
            AnimatedVisibility(
                visible = currentProfile != null,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionCard(title = "Cursor") {
                        WinlatorSlider(
                            label = stringResource(R.string.cursor_speed),
                            value = cursorSpeed,
                            onValueChange = { v ->
                                cursorSpeed = v
                                currentProfile?.let {
                                    it.cursorSpeed = v / 100f
                                    it.save()
                                }
                            },
                            valueRange = 10f..250f,
                            suffix = "%",
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.disable_mouse_input),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = disableMouseInput,
                                onCheckedChange = { checked ->
                                    disableMouseInput = checked
                                    currentProfile?.let {
                                        it.setDisableMouseInput(checked)
                                        it.save()
                                    }
                                },
                            )
                        }
                    }

                    SectionCard(title = "Overlay") {
                        WinlatorSlider(
                            label = stringResource(R.string.overlay_opacity),
                            value = overlayOpacity,
                            onValueChange = { v ->
                                overlayOpacity = v
                                preferences.edit()
                                    .putFloat("overlay_opacity", v / 100f)
                                    .apply()
                            },
                            valueRange = 10f..100f,
                            steps = 17,
                            suffix = "%",
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // External Controllers
        // ═══════════════════════════════════════
        item {
            SectionCard(title = stringResource(R.string.external_controllers)) {
                if (controllers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_items_to_display),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                }
            }
        }

        if (controllers.isNotEmpty()) {
            val bindingsLabel =
                context.getString(R.string.bindings).lowercase(Locale.ENGLISH)

            itemsIndexed(controllers.toList(), key = { _, c -> c.id }) { _, controller ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (currentProfile != null) {
                                val intent = Intent(
                                    context,
                                    ExternalControllerBindingsActivity::class.java
                                )
                                intent.putExtra("profile_id", currentProfile!!.id)
                                intent.putExtra("controller_id", controller.id)
                                context.startActivity(intent)
                            } else AppUtils.showToast(
                                context,
                                R.string.no_profile_selected
                            )
                        },
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Gamepad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = controller.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${controller.controllerBindingCount} $bindingsLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (controller.isConnected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                ),
                        )

                        if (controller.controllerBindingCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                currentProfile?.removeController(controller)
                                currentProfile?.save()
                                reloadControllers()
                                revision++
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add Profile Dialog ──
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.profile_name)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            currentProfile = manager.createProfile(name.trim())
                            reloadProfiles()
                            revision++
                        }
                        showAddDialog = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // ── Edit Profile Dialog ──
    if (showEditDialog && currentProfile != null) {
        var name by remember { mutableStateOf(currentProfile!!.name) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.profile_name)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            currentProfile!!.name = name.trim()
                            currentProfile!!.save()
                            reloadProfiles()
                            revision++
                        }
                        showEditDialog = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // ── Import Options Dialog ──
    if (showImportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showImportOptionsDialog = false },
            title = { Text(stringResource(R.string.import_profile)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showImportOptionsDialog = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.open_file),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        onClick = {
                            showImportOptionsDialog = false
                            downloadListLoading = true
                            showDownloadListDialog = true
                            scope.launch {
                                val content = suspendHttpDownload(
                                    String.format(INPUT_CONTROLS_URL, "index.txt")
                                )
                                downloadListLoading = false
                                if (content != null) {
                                    downloadListItems = content.split("\n").filter { it.isNotBlank() }
                                } else {
                                    showDownloadListDialog = false
                                    AppUtils.showToast(context, R.string.a_network_error_occurred)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.download_file),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportOptionsDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // ── Download Profile List Dialog ──
    if (showDownloadListDialog) {
        val selectedItems = remember { mutableStateListOf<Int>() }
        AlertDialog(
            onDismissRequest = {
                showDownloadListDialog = false
                downloadListItems = emptyList()
            },
            title = { Text(stringResource(R.string.import_profile)) },
            text = {
                if (downloadListLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(downloadListItems) { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedItems.contains(index)) selectedItems.remove(index)
                                        else selectedItems.add(index)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selectedItems.contains(index),
                                    onCheckedChange = { checked ->
                                        if (checked) selectedItems.add(index)
                                        else selectedItems.remove(index)
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDownload = selectedItems.toList()
                        showDownloadListDialog = false
                        if (toDownload.isNotEmpty()) {
                            scope.launch {
                                for (pos in toDownload) {
                                    val url = String.format(
                                        INPUT_CONTROLS_URL,
                                        downloadListItems[pos]
                                    )
                                    val content = suspendHttpDownload(url)
                                    if (content != null) {
                                        try {
                                            manager.importProfile(JSONObject(content))
                                        } catch (_: Exception) {}
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    reloadProfiles()
                                    revision++
                                }
                            }
                        }
                        downloadListItems = emptyList()
                    },
                    enabled = !downloadListLoading,
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadListDialog = false
                    downloadListItems = emptyList()
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private suspend fun suspendHttpDownload(url: String): String? =
    suspendCancellableCoroutine { cont ->
        HttpUtils.download(url) { content ->
            if (cont.isActive) cont.resume(content)
        }
    }
