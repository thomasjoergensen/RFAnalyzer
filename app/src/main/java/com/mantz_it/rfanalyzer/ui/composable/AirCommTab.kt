package com.mantz_it.rfanalyzer.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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

/**
 * <h1>RF Analyzer - Air Communication Scanner Tab</h1>
 *
 * Module:      AirCommTab.kt
 * Description: UI composable for continuous air communication scanning (squelch scan)
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */

data class AirCommTabActions(
    val onStartFrequencyChanged: (Long) -> Unit,
    val onEndFrequencyChanged: (Long) -> Unit,
    val onStepSizeChanged: (Long) -> Unit,
    val onDwellTimeChanged: (Long) -> Unit,
    val onHangTimeChanged: (Long) -> Unit,
    val onDetectionThresholdChanged: (Float) -> Unit,
    val onNoiseFloorMarginChanged: (Float) -> Unit,
    val onUseNoiseFloorChanged: (Boolean) -> Unit,
    val onStartScanClicked: () -> Unit,
    val onStopScanClicked: () -> Unit,
    val onAddToExceptionList: (Long) -> Unit,
    val onRemoveFromExceptionList: (Long) -> Unit,
    val onClearExceptionList: () -> Unit,
    val onResetToDefaults: () -> Unit
)

@Composable
fun AirCommTabComposable(
    analyzerRunning: Boolean,
    recordingRunning: Boolean,
    airCommScanRunning: Boolean,
    currentFrequency: Long,
    signalDetected: Boolean,
    signalStrength: Float,
    startFrequency: Long,
    endFrequency: Long,
    stepSize: Long,
    dwellTime: Long,
    hangTime: Long,
    detectionThreshold: Float,
    noiseFloorMargin: Float,
    useNoiseFloor: Boolean,
    exceptionList: Set<Long>,
    airCommTabActions: AirCommTabActions
) {
    ScrollableColumnWithFadingEdge {
        // Warning when recording is active
        if (recordingRunning) {
            Text(
                text = "Air comm scanning is disabled while recording",
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
                text = "Start the analyzer to enable air comm scanning",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        // Title
        Text(
            text = "Air Communication Scanner",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Scan Control
        Button(
            onClick = {
                if (airCommScanRunning) {
                    airCommTabActions.onStopScanClicked()
                } else {
                    airCommTabActions.onStartScanClicked()
                }
            },
            enabled = analyzerRunning && !recordingRunning,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(if (airCommScanRunning) "Stop Scan" else "Start Scan")
        }

        // Status Display
        if (airCommScanRunning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (signalDetected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status Icon and Text
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (signalDetected)
                                Icons.Default.RadioButtonChecked
                            else
                                Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (signalDetected) "Signal Detected" else "Scanning",
                            tint = if (signalDetected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (signalDetected) "SIGNAL DETECTED - PAUSED" else "SCANNING",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (signalDetected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Current Frequency Display
                    Text(
                        text = currentFrequency.asStringWithUnit("Hz"),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (signalDetected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )

                    // Signal Strength
                    if (signalStrength > -999f) {
                        Text(
                            text = "Signal: ${signalStrength.toInt()} dB",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // Signal Strength Meter
                        val normalizedStrength = ((signalStrength + 80f) / 50f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { normalizedStrength },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(top = 8.dp),
                        )
                    }

                    // Skip This Frequency button (always visible)
                    Button(
                        onClick = {
                            airCommTabActions.onAddToExceptionList(currentFrequency)
                        },
                        enabled = signalDetected && !exceptionList.contains(currentFrequency),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            if (exceptionList.contains(currentFrequency)) "Frequency in Exception List"
                            else "Skip This Frequency"
                        )
                    }
                }
            }
        }

        // Frequency Range Configuration
        Text(
            text = "Frequency Range",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FrequencyChooser(
                label = "Start Frequency",
                unit = "Hz",
                currentFrequency = startFrequency,
                onFrequencyChanged = airCommTabActions.onStartFrequencyChanged,
                enabled = !airCommScanRunning,
                modifier = Modifier.weight(1f)
            )
            FrequencyChooser(
                label = "End Frequency",
                unit = "Hz",
                currentFrequency = endFrequency,
                onFrequencyChanged = airCommTabActions.onEndFrequencyChanged,
                enabled = !airCommScanRunning,
                modifier = Modifier.weight(1f)
            )
        }

        // Quick Presets
        Text(
            text = "Quick Presets",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    airCommTabActions.onStartFrequencyChanged(118000000L) // 118 MHz
                    airCommTabActions.onEndFrequencyChanged(137000000L)   // 137 MHz
                },
                enabled = !airCommScanRunning,
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Text("Full Band", fontSize = 12.sp)
            }
            Button(
                onClick = {
                    airCommTabActions.onStartFrequencyChanged(118000000L) // 118 MHz
                    airCommTabActions.onEndFrequencyChanged(121400000L)   // 121.4 MHz
                },
                enabled = !airCommScanRunning,
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Text("Tower", fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    airCommTabActions.onStartFrequencyChanged(119000000L) // 119 MHz
                    airCommTabActions.onEndFrequencyChanged(136000000L)   // 136 MHz
                },
                enabled = !airCommScanRunning,
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Text("Approach", fontSize = 12.sp)
            }
            Button(
                onClick = {
                    airCommTabActions.onStartFrequencyChanged(121500000L) // 121.5 MHz Emergency
                    airCommTabActions.onEndFrequencyChanged(121500000L)
                },
                enabled = !airCommScanRunning,
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Text("Emergency", fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    airCommTabActions.onResetToDefaults()
                },
                enabled = !airCommScanRunning,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Text("Reset to Defaults", fontSize = 12.sp)
            }
        }

        // Scan Settings
        Text(
            text = "Scan Settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        OutlinedSteppedSlider(
            label = "Step Size",
            unit = "kHz",
            steps = listOf(8330L, 12500L, 25000L, 50000L),
            selectedStepIndex = when (stepSize) {
                8330L -> 0
                12500L -> 1
                25000L -> 2
                else -> 3
            },
            onSelectedStepIndexChanged = { index ->
                airCommTabActions.onStepSizeChanged(
                    listOf(8330L, 12500L, 25000L, 50000L)[index]
                )
            },
            formatValue = { (it / 1000f).toString() },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedSlider(
            label = "Dwell Time",
            unit = "ms",
            minValue = 50f,
            maxValue = 500f,
            value = dwellTime.toFloat(),
            onValueChanged = { airCommTabActions.onDwellTimeChanged(it.toLong()) },
            decimalPlaces = 0,
            modifier = Modifier.padding(top = 8.dp)
        )

        OutlinedSlider(
            label = "Hang Time",
            unit = "sec",
            minValue = 0f,
            maxValue = 10f,
            value = (hangTime / 1000f),
            onValueChanged = { airCommTabActions.onHangTimeChanged((it * 1000).toLong()) },
            decimalPlaces = 1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Detection Settings
        Text(
            text = "Detection Settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        OutlinedSwitch(
            label = "Detection Mode",
            helpText = if (useNoiseFloor) "Using noise floor + margin (adaptive)" else "Using fixed threshold",
            isChecked = useNoiseFloor,
            onCheckedChange = airCommTabActions.onUseNoiseFloorChanged,
            enabled = !airCommScanRunning
        )

        if (useNoiseFloor) {
            OutlinedSlider(
                label = "Noise Floor Margin",
                unit = "dB",
                minValue = 5f,
                maxValue = 30f,
                value = noiseFloorMargin,
                onValueChanged = airCommTabActions.onNoiseFloorMarginChanged,
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
                onValueChanged = airCommTabActions.onDetectionThresholdChanged,
                decimalPlaces = 0,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Instructions
        Text(
            text = "Continuously scan aviation VHF band. Scanner pauses when signals detected and resumes after hang time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // Exception List Display
        if (exceptionList.isNotEmpty()) {
            Text(
                text = "Exception List (${exceptionList.size} frequencies)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    exceptionList.sortedDescending().forEach { frequency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = frequency.asStringWithUnit("Hz"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { airCommTabActions.onRemoveFromExceptionList(frequency) },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Remove", fontSize = 12.sp)
                            }
                        }
                    }

                    if (exceptionList.size > 1) {
                        Button(
                            onClick = airCommTabActions.onClearExceptionList,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Clear All")
                        }
                    }
                }
            }
        }
    }
}
