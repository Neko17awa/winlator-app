package com.winlator.ui.screen

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.winlator.R
import com.winlator.XServerDisplayActivity
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.container.FileInfo
import com.winlator.core.FileUtils
import com.winlator.core.StringUtils
import com.winlator.core.WineUtils
import com.winlator.win32.MSIcon
import com.winlator.win32.MSLink
import com.winlator.win32.PEParser
import com.winlator.xenvironment.RootFS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class ViewStyle { LIST, GRID }

private data class ClipboardData(
    val files: List<File>,
    val cutMode: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContainerFileManagerScreen(
    containerId: Int,
    startPath: String?,
    onTitleChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val manager = remember { ContainerManager(context) }
    val container = remember { manager.getContainerById(containerId) }

    val fileManagerTitle = stringResource(R.string.file_manager)

    if (container == null) {
        LaunchedEffect(Unit) { onTitleChange(fileManagerTitle) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Container not found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    var viewStyle by remember {
        mutableStateOf(
            try {
                ViewStyle.valueOf(
                    preferences.getString("container_file_manager_view_style", "GRID")!!
                )
            } catch (_: Exception) {
                ViewStyle.GRID
            }
        )
    }

    val folderStack = remember { mutableStateListOf<FileInfo>() }
    var fileList by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var clipboard by remember { mutableStateOf<ClipboardData?>(null) }
    var isOperationInProgress by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf("") }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FileInfo?>(null) }
    var showRemoveDialog by remember { mutableStateOf<FileInfo?>(null) }
    var showInfoDialog by remember { mutableStateOf<FileInfo?>(null) }
    var showBottomSheet by remember { mutableStateOf<FileInfo?>(null) }

    fun getCurrentWorkingPath(): String {
        if (folderStack.isEmpty()) return ""
        val sb = StringBuilder()
        folderStack.forEachIndexed { i, fi ->
            if (i > 0) sb.append("\\")
            sb.append(fi.displayName)
        }
        if (folderStack.size == 1) sb.append("\\")
        return sb.toString()
    }

    fun refreshContent() {
        val parent = folderStack.lastOrNull()
        fileList = manager.loadFiles(container, parent)
        onTitleChange(
            if (folderStack.isNotEmpty()) getCurrentWorkingPath()
            else container.name
        )
    }

    fun setCurrentWorkingPath(dosPath: String) {
        val cleanPath = StringUtils.removeEndSlash(dosPath)
        val names = cleanPath.split("\\")
        var basePath = ""
        folderStack.clear()
        for (name in names) {
            if (name.isNotEmpty()) {
                val unixPath = WineUtils.dosToUnixPath(basePath + name, container)
                if (basePath.isEmpty() && name.matches(Regex("[A-Za-z]:"))) {
                    folderStack.add(FileInfo(container, name, unixPath, FileInfo.Type.DRIVE))
                } else {
                    folderStack.add(FileInfo(container, unixPath, FileInfo.Type.DIRECTORY))
                }
                basePath += "$name\\"
            }
        }
    }

    fun openFile(file: FileInfo) {
        val linkInfo = file.linkinfo
        val isFile = if (linkInfo != null) !linkInfo.isDirectory else file.type == FileInfo.Type.FILE

        if (isFile) {
            val intent = Intent(context, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", container.id)
                putExtra("exec_path", file.path)
            }
            context.startActivity(intent)
        } else {
            folderStack.add(file)
            refreshContent()
        }
    }

    LaunchedEffect(Unit) {
        if (startPath != null) {
            setCurrentWorkingPath(WineUtils.unixToDOSPath(startPath, container))
        }
        refreshContent()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBarArea(
                folderStack = folderStack,
                containerName = container.name,
                viewStyle = viewStyle,
                onHomeClick = {
                    folderStack.clear()
                    refreshContent()
                },
                onViewStyleToggle = {
                    viewStyle =
                        if (viewStyle == ViewStyle.GRID) ViewStyle.LIST else ViewStyle.GRID
                    preferences.edit()
                        .putString("container_file_manager_view_style", viewStyle.name)
                        .apply()
                },
                onNewFolderClick = {
                    if (folderStack.isNotEmpty()) showNewFolderDialog = true
                },
                onBreadcrumbClick = { index ->
                    while (folderStack.size > index + 1) folderStack.removeLast()
                    refreshContent()
                },
            )

            AnimatedVisibility(visible = isOperationInProgress) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = operationMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (fileList.isEmpty() && !isOperationInProgress) {
                EmptyState()
            } else {
                when (viewStyle) {
                    ViewStyle.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(fileList, key = { it.path }) { file ->
                                FileListItem(
                                    file = file,
                                    container = container,
                                    isAtRoot = folderStack.isEmpty(),
                                    onClick = { openFile(file) },
                                    onLongClick = { showBottomSheet = file },
                                    onMenuClick = { showBottomSheet = file },
                                    onRunClick = { openFile(file) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }

                    ViewStyle.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(fileList, key = { it.path }) { file ->
                                FileGridItem(
                                    file = file,
                                    container = container,
                                    isAtRoot = folderStack.isEmpty(),
                                    onClick = { openFile(file) },
                                    onLongClick = { showBottomSheet = file },
                                    onMenuClick = { showBottomSheet = file },
                                    onRunClick = { openFile(file) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Paste FAB
        AnimatedVisibility(
            visible = clipboard != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    val cb = clipboard ?: return@ExtendedFloatingActionButton
                    if (folderStack.isEmpty()) {
                        clipboard = null
                        return@ExtendedFloatingActionButton
                    }
                    val targetDir = folderStack.last().toFile()
                    for (f in cb.files) {
                        if (File(targetDir, f.name).exists()) {
                            return@ExtendedFloatingActionButton
                        }
                    }
                    isOperationInProgress = true
                    operationMessage = context.getString(R.string.copying_files)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            for (originFile in cb.files) {
                                if (originFile.exists()) {
                                    val targetFile = File(targetDir, originFile.name)
                                    if (FileUtils.copy(originFile, targetFile) && cb.cutMode) {
                                        FileUtils.delete(originFile)
                                    }
                                }
                            }
                        }
                        clipboard = null
                        isOperationInProgress = false
                        refreshContent()
                    }
                },
                icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                text = { Text(stringResource(R.string.copy)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    // Bottom sheet
    val bottomSheetFile = showBottomSheet
    if (bottomSheetFile != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = null },
            sheetState = sheetState,
            shape = MaterialTheme.shapes.large,
        ) {
            FileActionsSheet(
                file = bottomSheetFile,
                isAtRoot = folderStack.isEmpty(),
                isInFavorites = folderStack.isNotEmpty() && folderStack.last().name == "Favorites",
                onCopy = {
                    clipboard =
                        ClipboardData(listOf(File(bottomSheetFile.path)), cutMode = false)
                    showBottomSheet = null
                },
                onCut = {
                    clipboard =
                        ClipboardData(listOf(File(bottomSheetFile.path)), cutMode = true)
                    showBottomSheet = null
                },
                onRemove = {
                    showBottomSheet = null
                    showRemoveDialog = bottomSheetFile
                },
                onRename = {
                    showBottomSheet = null
                    showRenameDialog = bottomSheetFile
                },
                onAddFavorite = {
                    val favoritesDir =
                        File(container.userDir, context.getString(R.string.favorites))
                    val targetFile = File(
                        favoritesDir,
                        FileUtils.getBasename(bottomSheetFile.name) + ".lnk"
                    )
                    if (!targetFile.exists()) {
                        val linkInfo = MSLink.LinkInfo().apply {
                            targetPath =
                                WineUtils.unixToDOSPath(bottomSheetFile.path, container)
                            isDirectory = bottomSheetFile.type == FileInfo.Type.DIRECTORY
                        }
                        MSLink.createFile(linkInfo, targetFile)
                    }
                    showBottomSheet = null
                },
                onInfo = {
                    showBottomSheet = null
                    showInfoDialog = bottomSheetFile
                },
            )
        }
    }

    // New Folder dialog
    if (showNewFolderDialog) {
        InputDialog(
            title = stringResource(R.string.new_folder),
            initialValue = "",
            onConfirm = { name ->
                showNewFolderDialog = false
                if (folderStack.isNotEmpty() && name.isNotBlank()) {
                    val file = File(folderStack.last().toFile(), name)
                    if (!file.isDirectory) {
                        file.mkdir()
                        refreshContent()
                    }
                }
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    // Rename dialog
    val renameFile = showRenameDialog
    if (renameFile != null) {
        InputDialog(
            title = stringResource(R.string.rename),
            initialValue = renameFile.name,
            onConfirm = { newName ->
                showRenameDialog = null
                if (newName.isNotBlank()) {
                    renameFile.renameTo(newName)
                    refreshContent()
                }
            },
            onDismiss = { showRenameDialog = null },
        )
    }

    // Remove confirmation dialog
    val removeFile = showRemoveDialog
    if (removeFile != null) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.do_you_want_to_remove_this_file)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = null
                    isOperationInProgress = true
                    operationMessage = context.getString(R.string.removing_files)
                    scope.launch {
                        withContext(Dispatchers.IO) { FileUtils.delete(removeFile.toFile()) }
                        isOperationInProgress = false
                        clipboard = null
                        refreshContent()
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Info dialog
    val infoFile = showInfoDialog
    if (infoFile != null) {
        FileInfoAlertDialog(
            file = infoFile,
            container = container,
            onDismiss = { showInfoDialog = null },
        )
    }
}

// ─── Top Bar ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBarArea(
    folderStack: List<FileInfo>,
    containerName: String,
    viewStyle: ViewStyle,
    onHomeClick: () -> Unit,
    onViewStyleToggle: () -> Unit,
    onNewFolderClick: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onHomeClick) {
                Text(
                    text = containerName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (folderStack.isEmpty())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            folderStack.forEachIndexed { index, fi ->
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onBreadcrumbClick(index) }) {
                    Text(
                        text = fi.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index == folderStack.lastIndex)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home))
            }
            FilledTonalIconButton(onClick = onViewStyleToggle) {
                Icon(
                    if (viewStyle == ViewStyle.LIST) Icons.Default.GridView
                    else Icons.Default.ViewList,
                    contentDescription = null,
                )
            }
            FilledTonalIconButton(onClick = onNewFolderClick) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = stringResource(R.string.new_folder),
                )
            }
        }
    }
}

// ─── File List Item ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: FileInfo,
    container: Container,
    isAtRoot: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    onRunClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkInfo = file.linkinfo
    val effectiveType = when {
        linkInfo != null && linkInfo.isDirectory -> FileInfo.Type.DIRECTORY
        else -> file.type
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncFileIcon(
                file = file,
                container = container,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (file.type == FileInfo.Type.DRIVE) {
                        "${context.getString(R.string.drive)} (${file.name})"
                    } else {
                        file.displayName
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = getSubtitle(file, effectiveType, isAtRoot, context)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onRunClick) {
                Icon(
                    if (effectiveType == FileInfo.Type.FILE) Icons.Default.PlayArrow
                    else Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── File Grid Item ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    file: FileInfo,
    container: Container,
    isAtRoot: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    onRunClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkInfo = file.linkinfo
    val effectiveType = when {
        linkInfo != null && linkInfo.isDirectory -> FileInfo.Type.DIRECTORY
        else -> file.type
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                AsyncFileIcon(
                    file = file,
                    container = container,
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center),
                )
                IconButton(onClick = onMenuClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (file.type == FileInfo.Type.DRIVE) {
                    "${context.getString(R.string.drive)} (${file.name})"
                } else {
                    file.displayName
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val subtitle = getSubtitle(file, effectiveType, isAtRoot, context)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
            IconButton(onClick = onRunClick, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (effectiveType == FileInfo.Type.FILE) Icons.Default.PlayArrow
                    else Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── Async File Icon ───────────────────────────────────────────────────────────

@Composable
private fun AsyncFileIcon(
    file: FileInfo,
    container: Container,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var iconBitmap by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }
    var iconResId by remember(file.path) { mutableStateOf<Int?>(null) }
    var loading by remember(file.path) { mutableStateOf(true) }

    LaunchedEffect(file.path) {
        loading = true
        withContext(Dispatchers.IO) {
            when (val result = resolveFileIcon(file, container, context)) {
                is Bitmap -> iconBitmap = result.asImageBitmap()
                is Int -> iconResId = result
            }
        }
        loading = false
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )

            iconBitmap != null -> Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )

            iconResId != null -> Image(
                painter = painterResource(iconResId!!),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )

            else -> Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun resolveFileIcon(
    file: FileInfo,
    container: Container,
    context: android.content.Context,
): Any {
    if (file.type == FileInfo.Type.DIRECTORY) {
        val user = RootFS.USER
        return when {
            file.path.endsWith("$user/${context.getString(R.string.documents)}") ->
                R.drawable.container_folder_documents

            file.path.endsWith("$user/${context.getString(R.string.favorites)}") ->
                R.drawable.container_folder_favorites

            else -> R.drawable.container_folder
        }
    }
    if (file.type == FileInfo.Type.DRIVE) return R.drawable.container_drive

    return when (FileUtils.getExtension(file.path)) {
        "exe" -> PEParser.extractIcon(file.toFile()) ?: R.drawable.container_file_window
        "bat" -> R.drawable.container_file_window
        "ico" -> MSIcon.decodeFile(file.toFile()) ?: R.drawable.container_file
        "dll" -> R.drawable.container_file_library
        "lnk" -> {
            val linkInfo = file.linkinfo
            if (linkInfo != null) {
                if (linkInfo.isDirectory) {
                    R.drawable.container_folder
                } else {
                    val targetPath = WineUtils.dosToUnixPath(
                        linkInfo.iconLocation ?: linkInfo.targetPath, container
                    )
                    val bitmap = if (targetPath.endsWith(".ico")) {
                        MSIcon.decodeFile(File(targetPath))
                    } else {
                        PEParser.extractIcon(File(targetPath), linkInfo.iconIndex)
                    }
                    bitmap ?: R.drawable.container_file_link
                }
            } else {
                R.drawable.container_file_link
            }
        }

        else -> R.drawable.container_file
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

private fun getSubtitle(
    file: FileInfo,
    effectiveType: FileInfo.Type,
    isAtRoot: Boolean,
    context: android.content.Context,
): String? = when {
    effectiveType == FileInfo.Type.DIRECTORY && !isAtRoot ->
        "${file.itemCount} ${context.getString(R.string.items)}"

    effectiveType == FileInfo.Type.FILE ->
        StringUtils.formatBytes(file.size)

    else -> null
}

// ─── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.no_items_to_display),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// ─── File Actions Bottom Sheet Content ─────────────────────────────────────────

@Composable
private fun FileActionsSheet(
    file: FileInfo,
    isAtRoot: Boolean,
    isInFavorites: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onAddFavorite: () -> Unit,
    onInfo: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = file.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        SheetActionItem(
            icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            label = stringResource(R.string.copy),
            onClick = onCopy,
        )

        if (!isAtRoot) {
            SheetActionItem(
                icon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                label = stringResource(R.string.cut),
                onClick = onCut,
            )
            SheetActionItem(
                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                label = stringResource(R.string.remove),
                onClick = onRemove,
            )
            SheetActionItem(
                icon = {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                },
                label = stringResource(R.string.rename),
                onClick = onRename,
            )
        }

        if (!isAtRoot && !isInFavorites) {
            SheetActionItem(
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                label = stringResource(R.string.add_to_favorites),
                onClick = onAddFavorite,
            )
        }

        SheetActionItem(
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            label = stringResource(R.string.information),
            onClick = onInfo,
        )
    }
}

@Composable
private fun SheetActionItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ─── Input Dialog ──────────────────────────────────────────────────────────────

@Composable
private fun InputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ─── File Info Dialog ──────────────────────────────────────────────────────────

@Composable
private fun FileInfoAlertDialog(
    file: FileInfo,
    container: Container,
    onDismiss: () -> Unit,
) {
    val typeName = when (file.type) {
        FileInfo.Type.FILE -> stringResource(R.string.file)
        FileInfo.Type.DRIVE -> stringResource(R.string.drive)
        FileInfo.Type.DIRECTORY -> stringResource(R.string.folder)
    }

    val itemCountText = if (file.type == FileInfo.Type.DIRECTORY) {
        "${file.itemCount} ${stringResource(R.string.items)}"
    } else null

    var sizeText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.path) {
        withContext(Dispatchers.IO) {
            sizeText = if (file.type == FileInfo.Type.FILE) {
                StringUtils.formatBytes(file.size)
            } else {
                "..."
            }
        }
    }

    val location = if (file.type != FileInfo.Type.DRIVE) {
        WineUtils.unixToDOSPath(FileUtils.getDirname(file.path), container)
    } else null

    val modified = remember(file.path) {
        val date = Date(file.toFile().lastModified())
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.information)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(stringResource(R.string.type), typeName)
                if (itemCountText != null) {
                    InfoRow(stringResource(R.string.contains), itemCountText)
                }
                InfoRow(stringResource(R.string.size), sizeText ?: "...")
                if (location != null) {
                    InfoRow(stringResource(R.string.location), location)
                }
                InfoRow(stringResource(R.string.modified), modified)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
