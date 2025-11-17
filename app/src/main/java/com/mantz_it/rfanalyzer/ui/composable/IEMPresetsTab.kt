package com.mantz_it.rfanalyzer.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mantz_it.rfanalyzer.database.IEMDetectedChannelInfo
import com.mantz_it.rfanalyzer.database.IEMPreset

/**
 * <h1>RF Analyzer - IEM Presets Tab</h1>
 *
 * Module:      IEMPresetsTab.kt
 * Description: UI composable for IEM (In-Ear Monitor) preset scanning
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */

data class IEMPresetsTabActions(
    val onPresetSelectionChanged: (Long, Boolean) -> Unit,
    val onStartScanClicked: () -> Unit,
    val onStopScanClicked: () -> Unit,
    val onClearResultsClicked: () -> Unit,
    val onTuneToChannel: (Long) -> Unit,
    val onRecordChannel: (Long) -> Unit,
    val onRemoveDetection: (IEMDetectedChannelInfo) -> Unit,
    val onDetectionThresholdChanged: (Float) -> Unit,
    val onNoiseFloorMarginChanged: (Float) -> Unit,
    val onUseNoiseFloorChanged: (Boolean) -> Unit
)

@Composable
fun IEMPresetsTabComposable(
    analyzerRunning: Boolean,
    recordingRunning: Boolean,
    iemScanRunning: Boolean,
    availablePresets: List<IEMPreset>,
    selectedPresetIds: Set<Long>,
    detectedChannels: List<IEMDetectedChannelInfo>,
    scanProgress: Float,
    currentFrequency: Long,
    detectionThreshold: Float,
    noiseFloorMargin: Float,
    useNoiseFloor: Boolean,
    iemPresetsTabActions: IEMPresetsTabActions
) {
    ScrollableColumnWithFadingEdge {
        // Warning when recording is active
        if (recordingRunning) {
            Text(
                text = "IEM scanning is disabled while recording",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // Warning when analyzer is not running
        if (!analyzerRunning) {
            Text(
                text = "Start the analyzer to enable IEM scanning",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // Instructions
        Text(
            text = "IEM Preset Scanner",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "Select wireless IEM systems to scan for active channels. " +
                    "This will quickly identify which frequencies are in use at your venue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Detection Settings
        OutlinedSwitch(
            label = "Detection Mode",
            helpText = if (useNoiseFloor) "Using noise floor + margin (adaptive)" else "Using fixed threshold",
            isChecked = useNoiseFloor,
            onCheckedChange = iemPresetsTabActions.onUseNoiseFloorChanged,
            enabled = !iemScanRunning
        )

        if (useNoiseFloor) {
            OutlinedSlider(
                label = "Noise Floor Margin",
                unit = "dB",
                minValue = 5f,
                maxValue = 30f,
                value = noiseFloorMargin,
                onValueChanged = iemPresetsTabActions.onNoiseFloorMarginChanged,
                decimalPlaces = 0,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            OutlinedSlider(
                label = "Detection Threshold",
                unit = "dB",
                minValue = -80f,
                maxValue = -20f,
                value = detectionThreshold,
                onValueChanged = iemPresetsTabActions.onDetectionThresholdChanged,
                decimalPlaces = 0,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Preset Selection Section
        Text(
            text = "Select Systems (${selectedPresetIds.size} selected)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (availablePresets.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "No IEM presets available. Database may not be initialized.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Group presets by manufacturer
            val presetsByManufacturer = availablePresets.groupBy { it.manufacturer }

            presetsByManufacturer.forEach { (manufacturer, presets) ->
                ManufacturerSection(
                    manufacturer = manufacturer,
                    presets = presets,
                    selectedPresetIds = selectedPresetIds,
                    enabled = !iemScanRunning,
                    onPresetSelectionChanged = iemPresetsTabActions.onPresetSelectionChanged
                )
            }
        }

        // Scan Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (iemScanRunning) {
                        iemPresetsTabActions.onStopScanClicked()
                    } else {
                        iemPresetsTabActions.onStartScanClicked()
                    }
                },
                enabled = analyzerRunning && !recordingRunning && selectedPresetIds.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (iemScanRunning) "Stop Scan" else "Start Scan")
            }

            if (detectedChannels.isNotEmpty() && !iemScanRunning) {
                Button(
                    onClick = iemPresetsTabActions.onClearResultsClicked,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Results")
                }
            }
        }

        // Progress Indicator
        if (iemScanRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
                Text(
                    text = "Scanning: ${currentFrequency.asStringWithUnit("Hz")} (${(scanProgress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Results Section
        if (detectedChannels.isNotEmpty()) {
            Text(
                text = "Detected Channels (${detectedChannels.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Group results by manufacturer and model
            val detectionsBySystem = detectedChannels.groupBy {
                "${it.preset.manufacturer} ${it.preset.model} ${it.preset.band}"
            }

            detectionsBySystem.forEach { (systemName, detections) ->
                Text(
                    text = systemName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                detections.forEach { detection ->
                    DetectedChannelListItem(
                        detection = detection,
                        onTune = { iemPresetsTabActions.onTuneToChannel(detection.channel.frequency) },
                        onRecord = { iemPresetsTabActions.onRecordChannel(detection.channel.frequency) },
                        onRemove = { iemPresetsTabActions.onRemoveDetection(detection) },
                        enabled = !iemScanRunning
                    )
                }
            }
        } else if (!iemScanRunning && selectedPresetIds.isNotEmpty()) {
            Text(
                text = "No active channels detected. Select systems and click 'Start Scan'.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            )
        }
    }
}

@Composable
fun ManufacturerSection(
    manufacturer: String,
    presets: List<IEMPreset>,
    selectedPresetIds: Set<Long>,
    enabled: Boolean,
    onPresetSelectionChanged: (Long, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = manufacturer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            presets.forEach { preset ->
                PresetCheckboxItem(
                    preset = preset,
                    isSelected = selectedPresetIds.contains(preset.id),
                    enabled = enabled,
                    onSelectionChanged = { selected ->
                        onPresetSelectionChanged(preset.id, selected)
                    }
                )
            }
        }
    }
}

@Composable
fun PresetCheckboxItem(
    preset: IEMPreset,
    isSelected: Boolean,
    enabled: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelectionChanged(!isSelected) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${preset.model} ${preset.band}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${preset.frequencyRange} • ${preset.channelCount} channels",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DetectedChannelListItem(
    detection: IEMDetectedChannelInfo,
    onTune: () -> Unit,
    onRecord: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${detection.channel.groupName} Ch ${detection.channel.channelNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = detection.channel.frequency.asStringWithUnit("Hz"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Peak: ${detection.detectedChannel.peakStrength.toInt()} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Avg: ${detection.detectedChannel.averageStrength.toInt()} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Button(
                        onClick = onTune,
                        enabled = enabled,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Tune", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = onRecord,
                        enabled = enabled && !detection.preset.manufacturer.contains("recording", ignoreCase = true),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Record", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            IconButton(
                onClick = onRemove,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove detection",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
