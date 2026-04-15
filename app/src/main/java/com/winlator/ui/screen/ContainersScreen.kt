package com.winlator.ui.screen

import android.content.Intent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winlator.R
import com.winlator.XServerDisplayActivity
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.xenvironment.RootFS
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainersScreen(
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToFileManager: (Int) -> Unit,
    onTitleChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val title = stringResource(R.string.containers)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { onTitleChange(title) }

    val manager = remember { ContainerManager(context) }
    val containers = remember { mutableStateListOf<Container>() }
    var isLoading by remember { mutableStateOf(true) }

    fun loadContainers() {
        containers.clear()
        containers.addAll(manager.containers)
        isLoading = false
    }

    LaunchedEffect(Unit) { loadContainers() }

    var menuContainer by remember { mutableStateOf<Container?>(null) }
    var confirmAction by remember { mutableStateOf<Pair<Int, Container>?>(null) }
    var operationInProgress by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (RootFS.find(context).isValid) {
                        onNavigateToDetail(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (containers.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_items_to_display),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to create your first container",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(containers, key = { it.id }) { container ->
                        ContainerCard(
                            container = container,
                            onRun = {
                                val intent = Intent(context, XServerDisplayActivity::class.java)
                                intent.putExtra("container_id", container.id)
                                context.startActivity(intent)
                            },
                            onMenuClick = { menuContainer = container },
                        )
                    }
                }
            }

            if (operationInProgress) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    menuContainer?.let { container ->
        ModalBottomSheet(
            onDismissRequest = { menuContainer = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = container.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))

                BottomSheetMenuItem(Icons.Default.FolderOpen, stringResource(R.string.file_manager)) {
                    scope.launch { sheetState.hide() }
                    menuContainer = null
                    onNavigateToFileManager(container.id)
                }
                BottomSheetMenuItem(Icons.Default.Edit, stringResource(R.string.edit)) {
                    scope.launch { sheetState.hide() }
                    menuContainer = null
                    onNavigateToDetail(container.id)
                }
                BottomSheetMenuItem(Icons.Default.ContentCopy, stringResource(R.string.duplicate)) {
                    scope.launch { sheetState.hide() }
                    menuContainer = null
                    confirmAction = R.string.do_you_want_to_duplicate_this_container to container
                }
                BottomSheetMenuItem(Icons.Default.Info, stringResource(R.string.storage_info)) {
                    scope.launch { sheetState.hide() }
                    menuContainer = null
                }
                BottomSheetMenuItem(
                    Icons.Default.Delete,
                    stringResource(R.string.remove),
                    tint = MaterialTheme.colorScheme.error,
                ) {
                    scope.launch { sheetState.hide() }
                    menuContainer = null
                    confirmAction = R.string.do_you_want_to_remove_this_container to container
                }
            }
        }
    }

    confirmAction?.let { (msgResId, container) ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            icon = {
                Icon(
                    if (msgResId == R.string.do_you_want_to_remove_this_container) Icons.Default.Delete
                    else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = if (msgResId == R.string.do_you_want_to_remove_this_container)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text(
                    if (msgResId == R.string.do_you_want_to_remove_this_container)
                        stringResource(R.string.remove) else stringResource(R.string.duplicate),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = { Text(stringResource(msgResId)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    confirmAction = null
                    operationInProgress = true
                    Executors.newSingleThreadExecutor().execute {
                        when (msgResId) {
                            R.string.do_you_want_to_duplicate_this_container ->
                                manager.duplicateContainerAsync(container) {
                                    operationInProgress = false
                                    loadContainers()
                                }
                            R.string.do_you_want_to_remove_this_container ->
                                manager.removeContainerAsync(container) {
                                    operationInProgress = false
                                    loadContainers()
                                }
                        }
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun ContainerCard(
    container: Container,
    onRun: () -> Unit,
    onMenuClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onRun,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = container.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Container #${container.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalIconButton(
                onClick = onRun,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.run),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BottomSheetMenuItem(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text, color = tint, style = MaterialTheme.typography.bodyLarge)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = tint)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}
