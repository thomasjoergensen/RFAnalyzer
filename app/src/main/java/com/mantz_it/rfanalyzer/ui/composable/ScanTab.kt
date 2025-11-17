package com.mantz_it.rfanalyzer.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantz_it.rfanalyzer.database.DiscoveredSignal

enum class ScanDetectionMode(val displayName: String) {
    PEAK_ONLY("Peak Only"),
    AVERAGE_ONLY("Average Only"),
    PEAK_OR_AVERAGE("Peak or Average")
}

/**
 * <h1>RF Analyzer - Scan Tab</h1>
 *
 * Module:      ScanTab.kt
 * Description: UI composable for the Scan Tab, which allows users to scan frequency ranges
 *              and discover signals.
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

data class ScanTabActions(
    val onStartFrequencyChanged: (Long) -> Unit,
    val onEndFrequencyChanged: (Long) -> Unit,
    val onThresholdChanged: (Float) -> Unit,
    val onStepSizeChanged: (Long) -> Unit,
    val onDwellTimeChanged: (Long) -> Unit,
    val onDetectionModeChanged: (ScanDetectionMode) -> Unit,
    val onNoiseFloorMarginChanged: (Float) -> Unit,
    val onSignalGroupingChanged: (Boolean) -> Unit,
    val onMinimumGapChanged: (Int) -> Unit,
    val onStartScanClicked: () -> Unit,
    val onStopScanClicked: () -> Unit,
    val onClearResultsClicked: () -> Unit,
    val onTuneToSignal: (Long) -> Unit,
    val onRemoveSignal: (DiscoveredSignal) -> Unit
)

@Composable
fun ScanTabComposable(
    analyzerRunning: Boolean,
    recordingRunning: Boolean,
    scanRunning: Boolean,
    scanProgress: Float,
    currentScanFrequency: Long,
    startFrequency: Long,
    endFrequency: Long,
    threshold: Float,
    stepSize: Long,
    dwellTime: Long,
    detectionMode: ScanDetectionMode,
    noiseFloorMargin: Float,
    enableSignalGrouping: Boolean,
    minimumGap: Int,
    noiseFloor: Float,
    discoveredSignals: List<DiscoveredSignal>,
    scanTabActions: ScanTabActions
) {
    ScrollableColumnWithFadingEdge {
        // Warning when recording is active
        if (recordingRunning) {
            Text(
                text = "Scanning is disabled while recording",
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
                text = "Start the analyzer to enable scanning",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // Frequency Range Configuration
        Text(
            text = "Frequency Range",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        FrequencyChooser(
            label = "Start Frequency",
            unit = "Hz",
            currentFrequency = startFrequency,
            onFrequencyChanged = scanTabActions.onStartFrequencyChanged,
            minFrequency = 0,
            maxFrequency = 10000000000L,
            enabled = !scanRunning,
            liveUpdate = true
        )

        FrequencyChooser(
            label = "End Frequency",
            unit = "Hz",
            currentFrequency = endFrequency,
            onFrequencyChanged = scanTabActions.onEndFrequencyChanged,
            minFrequency = 0,
            maxFrequency = 10000000000L,
            enabled = !scanRunning,
            liveUpdate = true
        )

        // Step Size Configuration
        FrequencyChooser(
            label = "Step Size",
            unit = "Hz",
            currentFrequency = stepSize,
            onFrequencyChanged = scanTabActions.onStepSizeChanged,
            minFrequency = 1000,
            maxFrequency = 10000000000L,
            enabled = !scanRunning,
            digitCount = 9,
            liveUpdate = true
        )

        // Dwell Time Configuration
        if (!scanRunning) {
            OutlinedSlider(
                label = "Dwell Time",
                unit = "ms",
                value = dwellTime.toFloat(),
                minValue = 50f,
                maxValue = 1000f,
                decimalPlaces = 0,
                onValueChanged = { scanTabActions.onDwellTimeChanged(it.toLong()) }
            )
        } else {
            Text(
                text = "Dwell Time: ${dwellTime}ms",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Threshold Configuration
        if (!scanRunning) {
            OutlinedSlider(
                label = "Detection Threshold",
                unit = "dB",
                value = threshold,
                minValue = -100f,
                maxValue = 0f,
                decimalPlaces = 0,
                onValueChanged = scanTabActions.onThresholdChanged
            )
        } else {
            Text(
                text = "Threshold: ${threshold.toInt()} dB",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Advanced Detection Settings
        Text(
            text = "Detection Settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedEnumDropDown(
            enumClass = ScanDetectionMode::class,
            getDisplayName = { it.displayName },
            selectedEnum = detectionMode,
            onSelectionChanged = scanTabActions.onDetectionModeChanged,
            label = "Detection Mode",
            enabled = !scanRunning
        )

        if (!scanRunning) {
            OutlinedSlider(
                label = "Noise Floor Margin",
                unit = "dB",
                value = noiseFloorMargin,
                minValue = 3f,
                maxValue = 30f,
                decimalPlaces = 0,
                onValueChanged = scanTabActions.onNoiseFloorMarginChanged
            )
        } else {
            Text(
                text = "Noise Floor Margin: ${noiseFloorMargin.toInt()} dB",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        OutlinedSwitch(
            label = "Signal Grouping",
            helpText = "Group nearby detections as single signal",
            isChecked = enableSignalGrouping,
            onCheckedChange = scanTabActions.onSignalGroupingChanged,
            enabled = !scanRunning
        )

        if (enableSignalGrouping && !scanRunning) {
            OutlinedSlider(
                label = "Minimum Gap",
                unit = "× step",
                value = minimumGap.toFloat(),
                minValue = 1f,
                maxValue = 10f,
                decimalPlaces = 0,
                onValueChanged = { scanTabActions.onMinimumGapChanged(it.toInt()) }
            )
        }

        // Noise Floor Display
        if (noiseFloor > -999f) {
            Text(
                text = "Estimated Noise Floor: ${noiseFloor.toInt()} dB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Scan Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (scanRunning) {
                        scanTabActions.onStopScanClicked()
                    } else {
                        scanTabActions.onStartScanClicked()
                    }
                },
                enabled = analyzerRunning && !recordingRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (scanRunning) "Stop Scan" else "Start Scan")
            }

            if (discoveredSignals.isNotEmpty() && !scanRunning) {
                Button(
                    onClick = scanTabActions.onClearResultsClicked,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Results")
                }
            }
        }

        // Progress Indicator
        if (scanRunning) {
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
                    text = "Scanning: ${currentScanFrequency.asStringWithUnit("Hz")} (${(scanProgress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Results Section
        if (discoveredSignals.isNotEmpty()) {
            Text(
                text = "Discovered Signals (${discoveredSignals.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                items(discoveredSignals, key = { it.frequency }) { signal ->
                    SignalListItem(
                        signal = signal,
                        onTune = { scanTabActions.onTuneToSignal(signal.frequency) },
                        onRemove = { scanTabActions.onRemoveSignal(signal) },
                        enabled = !scanRunning
                    )
                }
            }
        } else if (!scanRunning) {
            Text(
                text = "No signals discovered yet. Configure the scan parameters and click 'Start Scan'.",
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
fun SignalListItem(
    signal: DiscoveredSignal,
    onTune: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = enabled) { onTune() },
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
                        text = signal.frequency.asStringWithUnit("Hz"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (signal.isGrouped) {
                        Text(
                            text = "GROUPED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Peak: ${signal.peakStrength.toInt()} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Avg: ${signal.averageStrength.toInt()} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (signal.bandwidth > 0) {
                        Text(
                            text = "BW: ${signal.bandwidth.asStringWithUnit("Hz")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onRemove,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove signal",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
