package com.winlator.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CpuListSelector(
    label: String,
    cpuCount: Int = Runtime.getRuntime().availableProcessors(),
    checkedCpus: String,
    onCpuListChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSet = remember(checkedCpus) {
        checkedCpus.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (i in 0 until cpuCount) {
                val isSelected = i in selectedSet
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newSet = if (isSelected) {
                            selectedSet - i
                        } else {
                            selectedSet + i
                        }
                        onCpuListChanged(newSet.sorted().joinToString(","))
                    },
                    label = {
                        Text(
                            text = "CPU $i",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}
