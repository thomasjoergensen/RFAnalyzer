package com.mantz_it.rfanalyzer.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantz_it.rfanalyzer.BuildConfig
import com.mantz_it.rfanalyzer.database.AppStateRepository
import com.mantz_it.rfanalyzer.database.AppStateRepository.Companion.DEFAULT_VERTICAL_SCALE_MAX
import com.mantz_it.rfanalyzer.database.AppStateRepository.Companion.DEFAULT_VERTICAL_SCALE_MIN
import com.mantz_it.rfanalyzer.database.BillingRepositoryInterface
import com.mantz_it.rfanalyzer.database.DiscoveredSignal
import com.mantz_it.rfanalyzer.database.Recording
import com.mantz_it.rfanalyzer.database.RecordingDao
import com.mantz_it.rfanalyzer.database.IEMDao
import com.mantz_it.rfanalyzer.database.IEMDetectedChannel
import com.mantz_it.rfanalyzer.database.IEMDetectedChannelInfo
import com.mantz_it.rfanalyzer.database.IEMScanResult
import com.mantz_it.rfanalyzer.database.ScanDao
import com.mantz_it.rfanalyzer.database.ScanResult
import com.mantz_it.rfanalyzer.database.calculateFileName
import com.mantz_it.rfanalyzer.database.collectAppState
import com.mantz_it.rfanalyzer.source.AirspySource
import com.mantz_it.rfanalyzer.source.HackrfSource
import com.mantz_it.rfanalyzer.source.HydraSdrSource
import com.mantz_it.rfanalyzer.ui.composable.AboutTabActions
import com.mantz_it.rfanalyzer.ui.composable.DemodulationMode
import com.mantz_it.rfanalyzer.ui.composable.DemodulationTabActions
import com.mantz_it.rfanalyzer.ui.composable.DisplayTabActions
import com.mantz_it.rfanalyzer.ui.composable.FilesourceFileFormat
import com.mantz_it.rfanalyzer.ui.composable.AirCommTabActions
import com.mantz_it.rfanalyzer.ui.composable.RecordingTabActions
import com.mantz_it.rfanalyzer.ui.composable.ScanDetectionMode
import com.mantz_it.rfanalyzer.ui.composable.IEMPresetsTabActions
import com.mantz_it.rfanalyzer.ui.composable.ScanTabActions
import com.mantz_it.rfanalyzer.ui.composable.SettingsTabActions
import com.mantz_it.rfanalyzer.ui.composable.SourceTabActions
import com.mantz_it.rfanalyzer.ui.composable.SourceType
import com.mantz_it.rfanalyzer.ui.composable.asSizeInBytesToString
import com.mantz_it.rfanalyzer.ui.composable.asStringWithUnit
import com.mantz_it.rfanalyzer.ui.composable.saturationFunction
import com.mantz_it.rfanalyzer.ui.screens.RecordingScreenActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

/**
 * <h1>RF Analyzer - MainViewModel</h1>
 *
 * Module:      MainViewModel.kt
 * Description: The ViewModel of the app. Contains application logic related to the UI.
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


sealed class AppScreen(val route: String, open val subUrl: String = "") {
    data object WelcomeScreen: AppScreen("WelcomeScreen/")
    data object MainScreen: AppScreen("MainScreen/")
    data object RecordingScreen: AppScreen("RecordingScreen/")
    data object LogFileScreen: AppScreen("LogFileScreen/")
    data object AboutScreen: AppScreen("AboutScreen/")
    data class ManualScreen(override val subUrl: String = "index.html") : AppScreen("ManualScreen/", subUrl)
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appStateRepository: AppStateRepository,
    private val recordingDao:RecordingDao,
    private val scanDao: ScanDao,
    private val iemDao: IEMDao,
    private val billingRepository: BillingRepositoryInterface
) : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
        private const val GRACE_PERIOD_SECONDS = 30
    }

    // Define UI actions
    private val _uiActions = MutableSharedFlow<UiAction?>()
    val uiActions: SharedFlow<UiAction?> = _uiActions
    sealed class UiAction {
        data object OnStartClicked: UiAction()
        data object OnStopClicked: UiAction()
        data object OnOpenIQFileClicked: UiAction()
        data object OnAutoscaleClicked: UiAction()
        data object OnShowLogFileClicked: UiAction()
        data class OnSaveLogToFileClicked(val destUri: Uri): UiAction()
        data object OnShareLogFileClicked: UiAction()
        data object OnDeleteLogFileClicked: UiAction()
        data object OnStartRecordingClicked: UiAction()
        data object OnStopRecordingClicked: UiAction()
        data object OnChooseRecordingDirectoryClicked: UiAction()
        data class OnDeleteRecordingClicked(val filePath: String): UiAction()
        data object OnDeleteAllRecordingsClicked: UiAction()
        data class OnSaveRecordingClicked(val filename: String, val destUri: Uri): UiAction()
        data class OnShareRecordingClicked(val filename: String): UiAction()
        data class RenameFile(val file: File, val newName: String): UiAction()
        data class ShowDialog(val title: String, val msg: String, val positiveButton: String? = null, val negativeButton: String? = null, val action: (() -> Unit)? = null): UiAction()
        data object ShowDonationDialog: UiAction()
        data object OnBuyFullVersionClicked: UiAction()
        data class ShowRecordingFinishedNotification(val recordingName: String, val sizeInBytes: Long): UiAction()
        data object ShowRecordingResumedNotification: UiAction()
    }
    private fun sendActionToUi(uiAction: UiAction){ viewModelScope.launch { _uiActions.emit(uiAction) } }

    // Database
    val recordings: StateFlow<List<Recording>> = recordingDao.getAllRecordings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = emptyList())
    val totalRecordingSizeInBytes: StateFlow<Long> = recordingDao.getAllRecordings()
        .map { recordings -> recordings.sumOf { it.sizeInBytes } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )
    private fun insertRecording(recording: Recording, onInsertionCompleted: ((recording: Recording) -> Unit)? = null) = viewModelScope.launch(Dispatchers.IO) {
        val id = recordingDao.insert(recording)
        val insertedRecording = recordingDao.get(id)
        if (onInsertionCompleted != null) {
            withContext(Dispatchers.Main) {
                onInsertionCompleted(insertedRecording)
            }
        }
    }
    private fun deleteRecording(recording: Recording, onDeletionCompleted: (() -> Unit)? = null) = viewModelScope.launch(Dispatchers.IO) {
        recordingDao.delete(recording)
        if (onDeletionCompleted != null) {
            withContext(Dispatchers.Main) {
                onDeletionCompleted()
            }
        }
    }
    private fun deleteRecordingWithUndo(recording: Recording) {
        deleteRecording(recording) {
            showSnackbar(
                SnackbarEvent(
                    message = "Recording ${recording.name} deleted (${recording.sizeInBytes.asSizeInBytesToString()})",
                    buttonText = "Undo",
                    callback = { snackbarResult ->
                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                            Log.i(TAG, "deleteRecordingWithUndo: User clicked Undo, restoring recording")
                            insertRecording(recording)
                        } else {
                            Log.i(TAG, "deleteRecordingWithUndo: Snackbar dismissed, deleting file: ${recording.filePath}")
                            sendActionToUi(UiAction.OnDeleteRecordingClicked(recording.filePath))
                        }
                    }
                )
            )
        }
    }
    private fun deleteAllRecordings() = viewModelScope.launch(Dispatchers.IO) { recordingDao.deleteAllRecordings() }

    // Navigation between Screens
    private val _navigationEvent = MutableSharedFlow<AppScreen>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()
    fun navigate(screen: AppScreen) { _navigationEvent.tryEmit(screen) }

    // Snackbar Messages (events collected in MainActivity to show snackbar)
    data class SnackbarEvent(val message: String, val buttonText: String? = null, val callback: ((SnackbarResult) -> Unit)? = null)
    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvent = _snackbarEvent.asSharedFlow()
    fun showSnackbar(snackbarEvent: SnackbarEvent) { _snackbarEvent.tryEmit(snackbarEvent) }

    // A StateFlow to hold the list of log content
    private val _logContent = MutableStateFlow<List<String>>(listOf("nothing loaded yet"))
    val logContent: StateFlow<List<String>> = _logContent

    // Should a loading indicator be shown?
    private val _isLoadingIndicatorVisible = MutableStateFlow<Boolean>(false)
    val isLoadingIndicatorVisible: StateFlow<Boolean> = _isLoadingIndicatorVisible
    fun showLoadingIndicator(show: Boolean) { _isLoadingIndicatorVisible.update { show }}

    // Billing
    fun checkPurchases() {
        billingRepository.queryPurchases()
    }
    fun buyFullVersion(activity: Activity) {
        billingRepository.purchaseFullVersion(activity)
    }
    private var gracePeriodCountdown = GRACE_PERIOD_SECONDS
    fun showUsageTimeUsedUpDialog() {
        sendActionToUi(UiAction.ShowDialog(
            title = "End of Trial Version",
            msg = "The 60-minute operation time of the trial version is used up.\n" +
                  "To continue using the app without interruption and support its further development, please consider purchasing the full version.",
            positiveButton = "Buy full version",
            negativeButton = "Cancel",
            action = {
                sendActionToUi(UiAction.OnBuyFullVersionClicked)
            }
        ))
        Log.d(TAG, "showUsageTimeUsedUpDialog")
    }

    // Auto-resume recording after USB reconnection
    fun restartRecordingAfterReconnect() {
        Log.i(TAG, "restartRecordingAfterReconnect: Attempting to restart recording after USB reconnection")

        // Check if already recording (shouldn't happen, but just in case)
        if (appStateRepository.recordingRunning.value) {
            Log.w(TAG, "restartRecordingAfterReconnect: Recording is already running. Ignoring auto-resume request.")
            return
        }

        // Check if analyzer is running - recording requires active analyzer
        if (!appStateRepository.analyzerRunning.value) {
            Log.i(TAG, "restartRecordingAfterReconnect: Analyzer not running. Starting analyzer first...")

            // Start the analyzer
            sendActionToUi(UiAction.OnStartClicked)

            // Wait for analyzer to start, then start recording
            viewModelScope.launch {
                // Wait for analyzer to be running (with timeout)
                var waitTime = 0L
                val maxWaitTime = 10000L // 10 seconds max
                val checkInterval = 500L // Check every 500ms

                while (!appStateRepository.analyzerRunning.value && waitTime < maxWaitTime) {
                    kotlinx.coroutines.delay(checkInterval)
                    waitTime += checkInterval
                }

                if (appStateRepository.analyzerRunning.value) {
                    Log.i(TAG, "restartRecordingAfterReconnect: Analyzer started after ${waitTime}ms. Starting recording...")
                    sendActionToUi(UiAction.OnStartRecordingClicked)
                    sendActionToUi(UiAction.ShowRecordingResumedNotification)
                } else {
                    Log.e(TAG, "restartRecordingAfterReconnect: Analyzer failed to start after ${waitTime}ms. Cannot resume recording.")
                    showSnackbar(SnackbarEvent("Cannot auto-resume: Analyzer failed to start"))
                }
            }
            return
        }

        // Analyzer already running, start recording immediately
        Log.i(TAG, "restartRecordingAfterReconnect: Analyzer already running. Starting recording...")
        sendActionToUi(UiAction.OnStartRecordingClicked)
        sendActionToUi(UiAction.ShowRecordingResumedNotification)
    }

    // ACTIONS
    // MainScreen ACTIONS ------------------------------------------------------------------------
    val sourceTabActions = SourceTabActions(
        onStartStopClicked = {
            if (appStateRepository.analyzerRunning.value || appStateRepository.analyzerStartPending.value) {
                sendActionToUi(UiAction.OnStopClicked)

                // Show FOSS Donation Dialog (when the user presses Stop after a certain interval of app usage has passed):
                if (BuildConfig.IS_FOSS) {
                    val counter = appStateRepository.donationDialogCounter.value
                    val currentIntervalInSeconds = when (counter) {
                            0 -> 60 * 60          // 1 hour
                            1 -> 60 * 60 * 5      // 5 hours
                            2 -> 60 * 60 * 15     // 15 hours
                            3 -> 60 * 60 * 30     // 30 hours
                            else -> 60 * 60 * 50  // 50 hours
                        }
                    if (appStateRepository.appUsageTimeInSeconds.value > appStateRepository.timestampOfLastDonationDialog.value + currentIntervalInSeconds) {
                        Log.i(TAG, "onStartStopClicked: Showing donation dialog (counter: $counter, interval: $currentIntervalInSeconds)")
                        sendActionToUi(UiAction.ShowDonationDialog)
                        appStateRepository.timestampOfLastDonationDialog.set(appStateRepository.appUsageTimeInSeconds.value)
                        appStateRepository.donationDialogCounter.set(counter + 1)
                    }
                }
            } else {
                // Verify RTL SDR external IP/Hostname is valid:
                if (appStateRepository.sourceType.value == SourceType.RTLSDR && appStateRepository.rtlsdrExternalServerEnabled.value) {
                    val value = appStateRepository.rtlsdrExternalServerIP.value
                    val ipRegex = Regex("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.|$)){4}$")
                    val hostnameRegex = Regex("^(?=.{1,253}\$)([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}\$")
                    if (!(ipRegex.matches(value) || hostnameRegex.matches(value))) {
                        showSnackbar(SnackbarEvent(message = "Invalid IP or Hostname Value: $value"))
                        Log.w(TAG,"sourceTabActions.onStartStopClicked: Invalid IP or Hostname: $value")
                        return@SourceTabActions
                    }
                }
                appStateRepository.analyzerStartPending.set(true)
                sendActionToUi(UiAction.OnStartClicked)
            }
        },
        onSourceTypeChanged = { newSourceType ->
            appStateRepository.sourceType.set(newSourceType)
            appStateRepository.sourceSupportedSampleRates.set(newSourceType.defaultSupportedSampleRates)
            when (newSourceType) {
                SourceType.HACKRF -> {
                    appStateRepository.sourceMinimumFrequency.set(HackrfSource.MIN_FREQUENCY)
                    appStateRepository.sourceMaximumFrequency.set(HackrfSource.MAX_FREQUENCY)
                }

                SourceType.RTLSDR -> {
                    // We don't know the Tuner yet. So don't be restrictive:
                    appStateRepository.sourceMinimumFrequency.set(0)
                    appStateRepository.sourceMaximumFrequency.set(0)
                }

                SourceType.AIRSPY -> {
                    appStateRepository.sourceMinimumFrequency.set(AirspySource.MIN_FREQUENCY)
                    appStateRepository.sourceMaximumFrequency.set(AirspySource.MAX_FREQUENCY)
                }

                SourceType.HYDRASDR -> {
                    appStateRepository.sourceMinimumFrequency.set(HydraSdrSource.MIN_FREQUENCY)
                    appStateRepository.sourceMaximumFrequency.set(HydraSdrSource.MAX_FREQUENCY)
                }

                SourceType.FILESOURCE -> Unit // no need to set any values
            }
        },
        onFrequencyChanged = { newFrequency ->
            if (newFrequency >= 0) {
                val updateFrequency: () -> Unit = {
                    appStateRepository.sourceFrequency.set(newFrequency)
                    // if channel is outside of the signal range, reset it to the center freq:
                    if (appStateRepository.channelFrequency.value !in appStateRepository.sourceSignalStartFrequency.value..appStateRepository.sourceSignalEndFrequency.value)
                        appStateRepository.channelFrequency.set(newFrequency)
                }
                if (appStateRepository.recordingRunning.value)
                    sendActionToUi(
                        UiAction.ShowDialog(
                        title = "Stop Recording?",
                        msg = "The recording is still running? Stop recording to change frequency?",
                        positiveButton = "Yes, stop recording!",
                        negativeButton = "No",
                        action = {
                            appStateRepository.recordingRunning.set(false)
                            updateFrequency()
                        }
                    ))
                else
                    updateFrequency()
            }
        },
        onSampleRateChanged = { newSampleRate ->
            if (newSampleRate >= 0) {
                val updateSampleRate: () -> Unit = {
                    appStateRepository.sourceSampleRate.set(newSampleRate)
                    // if channel is outside of the signal range, reset it to the center freq:
                    if (appStateRepository.channelFrequency.value !in appStateRepository.sourceSignalStartFrequency.value..appStateRepository.sourceSignalEndFrequency.value)
                        appStateRepository.channelFrequency.set(appStateRepository.sourceFrequency.value)
                }
                if (appStateRepository.recordingRunning.value)
                    sendActionToUi(
                        UiAction.ShowDialog(
                        title = "Stop Recording?",
                        msg = "The recording is still running? Stop recording to change sample rate?",
                        positiveButton = "Yes, stop recording!",
                        negativeButton = "No",
                        action = {
                            appStateRepository.recordingRunning.set(false)
                            updateSampleRate()
                        }
                    ))
                else
                    updateSampleRate()
            }
        },
        onAutomaticSampleRateAdjustmentChanged = appStateRepository.sourceAutomaticSampleRateAdjustment::set,
        onHackrfVgaGainIndexChanged = appStateRepository.hackrfVgaGainIndex::set,
        onHackrfLnaGainIndexChanged = appStateRepository.hackrfLnaGainIndex::set,
        onHackrfAmplifierEnabledChanged = appStateRepository.hackrfAmplifierEnabled::set,
        onHackrfAntennaPowerEnabledChanged = appStateRepository.hackrfAntennaPowerEnabled::set,
        onHackrfConverterOffsetChanged = appStateRepository.hackrfConverterOffset::set,
        onRtlsdrGainIndexChanged = appStateRepository.rtlsdrGainIndex::set,
        onRtlsdrIFGainIndexChanged = appStateRepository.rtlsdrIFGainIndex::set,
        onRtlsdrAgcEnabledChanged = appStateRepository.rtlsdrAgcEnabled::set,
        onRtlsdrManualGainEnabledChanged = appStateRepository.rtlsdrManualGainEnabled::set,
        onRtlsdrExternalServerEnabledChanged = appStateRepository.rtlsdrExternalServerEnabled::set,
        onRtlsdrExternalServerIPChanged = appStateRepository.rtlsdrExternalServerIP::set,
        onRtlsdrExternalServerPortChanged = appStateRepository.rtlsdrExternalServerPort::set,
        onRtlsdrConverterOffsetChanged = appStateRepository.rtlsdrConverterOffset::set,
        onRtlsdrFrequencyCorrectionChanged = appStateRepository.rtlsdrFrequencyCorrection::set,
        onRtlsdrEnableBiasTChanged = appStateRepository.rtlsdrEnableBiasT::set,
        onAirspyAdvancedGainEnabledChanged = appStateRepository.airspyAdvancedGainEnabled::set,
        onAirspyVgaGainChanged = appStateRepository.airspyVgaGain::set,
        onAirspyLnaGainChanged = appStateRepository.airspyLnaGain::set,
        onAirspyMixerGainChanged = appStateRepository.airspyMixerGain::set,
        onAirspyLinearityGainChanged = { newGain ->
            appStateRepository.airspyLinearityGain.set(newGain)
            // Linearity and sensitivity gains are mutually exclusive - zero out the other
            if (newGain > 0) {
                appStateRepository.airspySensitivityGain.set(0)
            }
        },
        onAirspySensitivityGainChanged = { newGain ->
            appStateRepository.airspySensitivityGain.set(newGain)
            // Linearity and sensitivity gains are mutually exclusive - zero out the other
            if (newGain > 0) {
                appStateRepository.airspyLinearityGain.set(0)
            }
        },
        onAirspyRfBiasEnabledChanged = appStateRepository.airspyRfBiasEnabled::set,
        onAirspyConverterOffsetChanged = appStateRepository.airspyConverterOffset::set,
        onHydraSdrAdvancedGainEnabledChanged = appStateRepository.hydraSdrAdvancedGainEnabled::set,
        onHydraSdrVgaGainChanged = appStateRepository.hydraSdrVgaGain::set,
        onHydraSdrLnaGainChanged = appStateRepository.hydraSdrLnaGain::set,
        onHydraSdrMixerGainChanged = appStateRepository.hydraSdrMixerGain::set,
        onHydraSdrLinearityGainChanged = { newGain ->
            appStateRepository.hydraSdrLinearityGain.set(newGain)
            // Linearity and sensitivity gains are mutually exclusive - zero out the other
            if (newGain > 0) {
                appStateRepository.hydraSdrSensitivityGain.set(0)
            }
        },
        onHydraSdrSensitivityGainChanged = { newGain ->
            appStateRepository.hydraSdrSensitivityGain.set(newGain)
            // Linearity and sensitivity gains are mutually exclusive - zero out the other
            if (newGain > 0) {
                appStateRepository.hydraSdrLinearityGain.set(0)
            }
        },
        onHydraSdrRfBiasEnabledChanged = appStateRepository.hydraSdrRfBiasEnabled::set,
        onHydraSdrRfPortChanged = appStateRepository.hydraSdrRfPort::set,
        onHydraSdrConverterOffsetChanged = appStateRepository.hydraSdrConverterOffset::set,
        onOpenFileClicked = { sendActionToUi(UiAction.OnOpenIQFileClicked) },
        onViewRecordingsClicked = { navigate(AppScreen.RecordingScreen) },
        onFilesourceFileFormatChanged = appStateRepository.filesourceFileFormat::set,
        onFilesourceRepeatChanged = appStateRepository.filesourceRepeatEnabled::set,
    )

    val displayTabActions = DisplayTabActions(
        onVerticalScaleChanged = { newMin, newMax ->
            appStateRepository.viewportVerticalScaleMin.set(newMin)
            appStateRepository.viewportVerticalScaleMax.set(newMax)
        },
        onAutoscaleClicked = { sendActionToUi(UiAction.OnAutoscaleClicked) },
        onResetScalingClicked = { analyzerSurfaceActions.onViewportVerticalScaleChanged(Pair(DEFAULT_VERTICAL_SCALE_MIN, DEFAULT_VERTICAL_SCALE_MAX)) },
        onFftSizeChanged = appStateRepository.fftSize::set,
        onAverageLengthChanged = appStateRepository.fftAverageLength::set,
        onPeakHoldEnabledChanged = appStateRepository.fftPeakHold::set,
        onMaxFrameRateChanged = appStateRepository.maxFrameRate::set,
        onColorMapChanged = appStateRepository.waterfallColorMap::set,
        onWaterfallSpeedChanged = appStateRepository.waterfallSpeed::set,
        onDrawingTypeChanged = appStateRepository.fftDrawingType::set,
        onRelativeFrequencyEnabledChanged = appStateRepository.fftRelativeFrequency::set,
        onFftWaterfallRatioChanged = appStateRepository.fftWaterfallRatio::set,
    )

    val demodulationTabActions = DemodulationTabActions(
        onDemodulationModeChanged = { newDemodulationMode ->
            // initialize channel freq if it is out of the viewport range:
            var channelFrequency = appStateRepository.channelFrequency.value
            if (appStateRepository.demodulationMode.value == DemodulationMode.OFF && newDemodulationMode != DemodulationMode.OFF) {
                if (channelFrequency < appStateRepository.viewportStartFrequency.value || channelFrequency > appStateRepository.viewportEndFrequency.value) {
                    channelFrequency = appStateRepository.viewportFrequency.value
                }
                appStateRepository.channelFrequency.set(channelFrequency)
            }
            appStateRepository.demodulationMode.set(newDemodulationMode)
        },
        onChannelFrequencyChanged = { newChannelFrequency ->
            setChannelFrequency(newChannelFrequency)
        },
        onTunerWheelDelta = { delta ->
            val minimumStepSize = appStateRepository.demodulationMode.value.tuneStepDistance
            val absDelta = abs(delta)
            val factor = appStateRepository.viewportSampleRate.value / 1000f / minimumStepSize
            val amplification = factor * saturationFunction(x=absDelta-1, a=3f, k=5f)
            val amplifiedDelta = amplification * delta * (if(appStateRepository.reverseTuningWheel.value) -1 else 1)
            val finalDelta = if (amplifiedDelta > 0) amplifiedDelta.coerceAtLeast(1f) else amplifiedDelta.coerceAtMost( -1f )
            val newChannelFrequency = (appStateRepository.channelFrequency.value + finalDelta.toLong()*minimumStepSize)
            val stepAlignedNewChannelFrequency = newChannelFrequency / minimumStepSize * minimumStepSize
            setChannelFrequency(stepAlignedNewChannelFrequency)
        },
        onChannelWidthChanged = { newWidth ->
            appStateRepository.channelWidth.set(newWidth.coerceIn(
                appStateRepository.demodulationMode.value.minChannelWidth,
                appStateRepository.demodulationMode.value.maxChannelWidth
            ))
        },
        onSquelchEnabledChanged = appStateRepository.squelchEnabled::set,
        onSquelchChanged = appStateRepository.squelch::set,
        onKeepChannelCenteredChanged = { newValue ->
            appStateRepository.keepChannelCentered.set(newValue)
            if (newValue)
                setViewportFrequency(appStateRepository.channelFrequency.value)
        },
        onZoomChanged = { zoom ->
            val newVpSampleRate = ((1f - zoom) * appStateRepository.sourceSampleRate.value).toLong()
            val sampleRateDiff = newVpSampleRate - appStateRepository.viewportSampleRate.value
            val channelToCenterOffset = appStateRepository.viewportFrequency.value - appStateRepository.channelFrequency.value
            val offsetRatio = channelToCenterOffset / (appStateRepository.viewportSampleRate.value.toFloat() / 2)   // -1..1
            val newVpFrequency = appStateRepository.viewportFrequency.value + (sampleRateDiff / 2 * offsetRatio).toLong()
            appStateRepository.viewportSampleRate.set(newVpSampleRate)
            setViewportFrequency(newVpFrequency)
        },
        onAudioMuteClicked = { appStateRepository.audioMuted.set(!appStateRepository.audioMuted.value) },
        onAudioVolumeLevelChanged = appStateRepository.audioVolumeLevel::set,
    )

    val recordingTabActions = RecordingTabActions(
        onNameChanged = { appStateRepository.recordingName.set(it.replace('/', '_')) },
        onOnlyRecordWhenSquelchIsSatisfiedChanged = appStateRepository.recordOnlyWhenSquelchIsSatisfied::set,
        onSquelchChanged = appStateRepository.squelch::set,
        onStopAfterThresholdChanged = { appStateRepository.recordingStopAfterThreshold.set(it.coerceAtLeast(0)) },
        onStopAfterUnitChanged = appStateRepository.recordingstopAfterUnit::set,
        onStartRecordingClicked = {
            if(appStateRepository.recordingRunning.value) {
                sendActionToUi(UiAction.OnStopRecordingClicked)
            } else {
                if(!appStateRepository.isFullVersion.value && !BuildConfig.IS_FOSS) {
                    if (appStateRepository.isAppUsageTimeUsedUp.value) {
                        showUsageTimeUsedUpDialog()
                        return@RecordingTabActions
                    }
                }
                // start recording:
                sendActionToUi(UiAction.OnStartRecordingClicked)
            }
        },
        onViewRecordingsClicked = { navigate(AppScreen.RecordingScreen) },
        onChooseRecordingDirectoryClicked = { sendActionToUi(UiAction.OnChooseRecordingDirectoryClicked) },
        onAutoResumeRecordingChanged = appStateRepository.autoResumeRecording::set
    )

    // Scan Tab
    private var scanJob: kotlinx.coroutines.Job? = null

    val scanTabActions = ScanTabActions(
        onStartFrequencyChanged = appStateRepository.scanStartFrequency::set,
        onEndFrequencyChanged = appStateRepository.scanEndFrequency::set,
        onThresholdChanged = appStateRepository.scanThreshold::set,
        onStepSizeChanged = appStateRepository.scanStepSize::set,
        onDwellTimeChanged = appStateRepository.scanDwellTime::set,
        onDetectionModeChanged = appStateRepository.scanDetectionMode::set,
        onNoiseFloorMarginChanged = appStateRepository.scanNoiseFloorMargin::set,
        onSignalGroupingChanged = appStateRepository.scanEnableSignalGrouping::set,
        onMinimumGapChanged = appStateRepository.scanMinimumGap::set,
        onStartScanClicked = { startScanning() },
        onStopScanClicked = { stopScanning() },
        onClearResultsClicked = {
            appStateRepository.discoveredSignals.set(emptyList())
            appStateRepository.currentScanResultId.set(null)
            appStateRepository.noiseFloorLevel.set(-999f)
        },
        onTuneToSignal = { frequency ->
            if (!appStateRepository.scanRunning.value) {
                appStateRepository.sourceFrequency.set(frequency)
                // Center the channel on the frequency
                appStateRepository.channelFrequency.set(frequency)
            }
        },
        onRemoveSignal = { signal ->
            val currentSignals = appStateRepository.discoveredSignals.value
            appStateRepository.discoveredSignals.set(currentSignals.filter { it.frequency != signal.frequency })
        }
    )

    // IEM Presets Tab
    private var iemScanJob: kotlinx.coroutines.Job? = null

    val iemPresetsTabActions = IEMPresetsTabActions(
        onPresetSelectionChanged = { presetId, selected ->
            val currentSelection = appStateRepository.iemSelectedPresetIds.value.toMutableSet()
            if (selected) {
                currentSelection.add(presetId)
            } else {
                currentSelection.remove(presetId)
            }
            appStateRepository.iemSelectedPresetIds.set(currentSelection)
        },
        onStartScanClicked = { startIEMScanning() },
        onStopScanClicked = { stopIEMScanning() },
        onClearResultsClicked = {
            appStateRepository.iemDetectedChannels.set(emptyList())
            appStateRepository.iemCurrentScanResultId.set(null)
        },
        onTuneToChannel = { frequency ->
            if (!appStateRepository.iemScanRunning.value) {
                appStateRepository.sourceFrequency.set(frequency)
                appStateRepository.channelFrequency.set(frequency)
            }
        },
        onRecordChannel = { frequency ->
            if (!appStateRepository.iemScanRunning.value && !appStateRepository.recordingRunning.value) {
                // Tune to the frequency first
                appStateRepository.sourceFrequency.set(frequency)
                appStateRepository.channelFrequency.set(frequency)
                // Start recording
                sendActionToUi(UiAction.OnStartRecordingClicked)
            }
        },
        onRemoveDetection = { detection ->
            val currentDetections = appStateRepository.iemDetectedChannels.value
            appStateRepository.iemDetectedChannels.set(currentDetections.filter {
                it.detectedChannel.id != detection.detectedChannel.id
            })
        },
        onDetectionThresholdChanged = appStateRepository.iemDetectionThreshold::set,
        onNoiseFloorMarginChanged = appStateRepository.iemNoiseFloorMargin::set,
        onUseNoiseFloorChanged = appStateRepository.iemUseNoiseFloor::set
    )

    // Air Communication Scanner Tab
    private var airCommScanJob: kotlinx.coroutines.Job? = null

    val airCommTabActions = AirCommTabActions(
        onStartFrequencyChanged = appStateRepository.airCommStartFrequency::set,
        onEndFrequencyChanged = appStateRepository.airCommEndFrequency::set,
        onStepSizeChanged = appStateRepository.airCommStepSize::set,
        onDwellTimeChanged = appStateRepository.airCommDwellTime::set,
        onHangTimeChanged = appStateRepository.airCommHangTime::set,
        onDetectionThresholdChanged = appStateRepository.airCommDetectionThreshold::set,
        onNoiseFloorMarginChanged = appStateRepository.airCommNoiseFloorMargin::set,
        onUseNoiseFloorChanged = appStateRepository.airCommUseNoiseFloor::set,
        onStartScanClicked = { startAirCommScanning() },
        onStopScanClicked = { stopAirCommScanning() },
        onAddToExceptionList = { frequency ->
            val current = appStateRepository.airCommExceptionList.value.toMutableSet()
            current.add(frequency)
            appStateRepository.airCommExceptionList.set(current)
            Log.d(TAG, "Air Comm: Added ${frequency.asStringWithUnit("Hz")} to exception list")
        },
        onRemoveFromExceptionList = { frequency ->
            val current = appStateRepository.airCommExceptionList.value.toMutableSet()
            current.remove(frequency)
            appStateRepository.airCommExceptionList.set(current)
            Log.d(TAG, "Air Comm: Removed ${frequency.asStringWithUnit("Hz")} from exception list")
        },
        onClearExceptionList = {
            appStateRepository.airCommExceptionList.set(emptySet())
            Log.d(TAG, "Air Comm: Cleared exception list")
        },
        onResetToDefaults = {
            // Reset all Air Comm settings to default values
            appStateRepository.airCommStartFrequency.set(118000000L) // 118 MHz
            appStateRepository.airCommEndFrequency.set(137000000L)   // 137 MHz
            appStateRepository.airCommStepSize.set(25000L)           // 25 kHz
            appStateRepository.airCommDwellTime.set(100L)            // 100ms
            appStateRepository.airCommHangTime.set(3000L)            // 3 seconds
            appStateRepository.airCommDetectionThreshold.set(-40f)   // -40 dB
            appStateRepository.airCommNoiseFloorMargin.set(15f)      // 15 dB
            appStateRepository.airCommUseNoiseFloor.set(true)        // Use adaptive detection
            appStateRepository.airCommExceptionList.set(emptySet())  // Clear exception list
            Log.d(TAG, "Air Comm: Reset all settings to defaults")
            showSnackbar(SnackbarEvent("Air Comm settings reset to defaults"))
        }
    )

    private fun startIEMScanning() {
        // Validate preconditions
        if (!appStateRepository.analyzerRunning.value) {
            showSnackbar(SnackbarEvent("Analyzer must be running to scan IEM presets"))
            return
        }
        if (appStateRepository.recordingRunning.value) {
            showSnackbar(SnackbarEvent("Cannot scan while recording is active"))
            return
        }
        if (appStateRepository.iemSelectedPresetIds.value.isEmpty()) {
            showSnackbar(SnackbarEvent("Please select at least one IEM preset to scan"))
            return
        }

        // Start IEM scanning
        appStateRepository.iemScanRunning.set(true)
        appStateRepository.iemScanProgress.set(0f)
        appStateRepository.iemDetectedChannels.set(emptyList())

        val selectedPresetIds = appStateRepository.iemSelectedPresetIds.value.toList()
        val dwellTime = 300L // Fixed 300ms dwell time for IEM scanning (IEM signals are continuous)
        val useNoiseFloor = appStateRepository.iemUseNoiseFloor.value
        val fixedThreshold = appStateRepository.iemDetectionThreshold.value
        val noiseFloorMargin = appStateRepository.iemNoiseFloorMargin.value

        iemScanJob = viewModelScope.launch {
            val scanStartTime = System.currentTimeMillis()
            val detectedChannels = mutableListOf<IEMDetectedChannel>()

            try {
                // Step 1: Query database for all channels in selected presets
                val channels = withContext(Dispatchers.IO) {
                    iemDao.getChannelsForPresets(selectedPresetIds)
                }

                if (channels.isEmpty()) {
                    showSnackbar(SnackbarEvent("No channels found in selected presets"))
                    return@launch
                }

                Log.d(TAG, "IEM Scan: Found ${channels.size} channels to scan")

                // Step 2: Get current sample rate and calculate usable bandwidth
                val sampleRate = appStateRepository.sourceSampleRate.value
                val usableBandwidth = (sampleRate * 0.8).toLong()

                Log.d(TAG, "IEM Scan: sampleRate=$sampleRate, usableBandwidth=$usableBandwidth")

                // Step 2.5: Estimate noise floor if enabled
                var noiseFloor = -80f // Default fallback
                if (useNoiseFloor) {
                    val noiseFloorSamples = mutableListOf<Float>()
                    // Sample 3 random frequencies from the channels list
                    val sampleIndices = if (channels.size <= 3) {
                        channels.indices.toList()
                    } else {
                        listOf(0, channels.size / 2, channels.size - 1)
                    }

                    for (i in sampleIndices) {
                        appStateRepository.sourceFrequency.set(channels[i].frequency)
                        delay(dwellTime)
                        val avgLevel = getAverageSignalLevel()
                        if (avgLevel != null) noiseFloorSamples.add(avgLevel)
                    }

                    noiseFloor = if (noiseFloorSamples.isNotEmpty()) {
                        noiseFloorSamples.average().toFloat()
                    } else {
                        -80f
                    }
                    appStateRepository.noiseFloorLevel.set(noiseFloor)
                    Log.d(TAG, "IEM Scan: Estimated noise floor: $noiseFloor dB, margin: $noiseFloorMargin dB")
                }

                // Step 3: Group channels by frequency proximity (batch channels within same FFT window)
                val sortedChannels = channels.sortedBy { it.frequency }
                val channelBatches = mutableListOf<List<com.mantz_it.rfanalyzer.database.IEMChannel>>()
                var currentBatch = mutableListOf<com.mantz_it.rfanalyzer.database.IEMChannel>()

                for (channel in sortedChannels) {
                    if (currentBatch.isEmpty()) {
                        currentBatch.add(channel)
                    } else {
                        val batchCenterFreq = (currentBatch.first().frequency + currentBatch.last().frequency) / 2
                        val freqDiff = kotlin.math.abs(channel.frequency - batchCenterFreq)

                        // Can we fit this channel in current batch's FFT window?
                        if (freqDiff < usableBandwidth / 2) {
                            currentBatch.add(channel)
                        } else {
                            // Start new batch
                            channelBatches.add(currentBatch)
                            currentBatch = mutableListOf(channel)
                        }
                    }
                }
                if (currentBatch.isNotEmpty()) {
                    channelBatches.add(currentBatch)
                }

                Log.d(TAG, "IEM Scan: Grouped ${channels.size} channels into ${channelBatches.size} batches")

                // Step 4: Scan each batch
                var scannedChannels = 0
                for ((batchIndex, batch) in channelBatches.withIndex()) {
                    if (!appStateRepository.iemScanRunning.value) break

                    // Calculate center frequency for this batch
                    val batchCenterFreq = (batch.first().frequency + batch.last().frequency) / 2

                    // Tune to batch center frequency
                    appStateRepository.sourceFrequency.set(batchCenterFreq)
                    appStateRepository.iemCurrentScanFrequency.set(batchCenterFreq)

                    // Wait for FFT to settle
                    delay(dwellTime)

                    // Step 5: Analyze each channel in this batch
                    val effectiveThreshold = if (useNoiseFloor) {
                        noiseFloor + noiseFloorMargin
                    } else {
                        fixedThreshold
                    }
                    val batchDetections = detectIEMChannelsInFFT(batch, batchCenterFreq, sampleRate, effectiveThreshold)
                    detectedChannels.addAll(batchDetections)

                    // Update progress
                    scannedChannels += batch.size
                    val progress = scannedChannels.toFloat() / channels.size
                    appStateRepository.iemScanProgress.set(progress)

                    Log.d(TAG, "IEM Scan: Batch ${batchIndex + 1}/${channelBatches.size} - Found ${batchDetections.size} active channels")
                }

                // Step 6: Save results to database and update UI
                if (detectedChannels.isNotEmpty()) {
                    val scanDuration = System.currentTimeMillis() - scanStartTime
                    val avgStrength = detectedChannels.map { it.averageStrength }.average().toFloat()

                    withContext(Dispatchers.IO) {
                        val scanResult = IEMScanResult(
                            timestamp = scanStartTime,
                            scanDuration = scanDuration,
                            scannedPresetIds = selectedPresetIds.joinToString(","),
                            detectedCount = detectedChannels.size,
                            averageSignalStrength = avgStrength
                        )
                        val scanResultId = iemDao.saveScanWithDetections(scanResult, detectedChannels)
                        appStateRepository.iemCurrentScanResultId.set(scanResultId)

                        // Load full channel info for UI
                        val channelInfo = iemDao.getDetectedChannelInfoForScan(scanResultId)
                        appStateRepository.iemDetectedChannels.set(channelInfo)
                    }

                    showSnackbar(SnackbarEvent("IEM scan complete. Found ${detectedChannels.size} active channels."))
                } else {
                    showSnackbar(SnackbarEvent("IEM scan complete. No active channels detected."))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during IEM scanning", e)
                showSnackbar(SnackbarEvent("IEM scan failed: ${e.message}"))
            } finally {
                appStateRepository.iemScanRunning.set(false)
            }
        }
    }

    private fun stopIEMScanning() {
        iemScanJob?.cancel()
        appStateRepository.iemScanRunning.set(false)
        showSnackbar(SnackbarEvent("IEM scan stopped"))
    }

    /**
     * Analyzes FFT data for specific IEM channel frequencies
     * Uses configurable threshold (fixed or noise floor + margin)
     */
    private fun detectIEMChannelsInFFT(
        channels: List<com.mantz_it.rfanalyzer.database.IEMChannel>,
        centerFrequency: Long,
        sampleRate: Long,
        threshold: Float
    ): List<IEMDetectedChannel> {
        val fftProcessorData = appStateRepository.fftProcessorData
        val detectedChannels = mutableListOf<IEMDetectedChannel>()

        try {
            fftProcessorData.lock.readLock().lock()
            try {
                val waterfallBuffer = fftProcessorData.waterfallBuffer ?: return emptyList()
                if (waterfallBuffer.isEmpty()) return emptyList()

                val readIndex = fftProcessorData.readIndex
                if (readIndex < 0 || readIndex >= waterfallBuffer.size) return emptyList()

                val currentFFT = waterfallBuffer[readIndex]
                if (currentFFT.isEmpty()) return emptyList()

                // Calculate FFT parameters
                val fftSize = currentFFT.size
                val frequencyResolution = sampleRate.toFloat() / fftSize
                val startFrequency = centerFrequency - sampleRate / 2

                // Check each channel frequency
                for (channel in channels) {
                    // Calculate FFT bin index for this channel frequency
                    val binIndex = ((channel.frequency - startFrequency) / frequencyResolution).toInt()

                    if (binIndex in currentFFT.indices) {
                        // Analyze window around the channel (±100 kHz for 200 kHz IEM bandwidth)
                        // With typical 2.5 MHz sample rate, this is about ±40 bins
                        val windowHalfSize = (100000 / frequencyResolution).toInt().coerceAtLeast(5)
                        val windowStart = (binIndex - windowHalfSize).coerceAtLeast(0)
                        val windowEnd = (binIndex + windowHalfSize).coerceAtMost(currentFFT.size - 1)

                        val windowData = currentFFT.sliceArray(windowStart..windowEnd)
                        val peakSignal = windowData.maxOrNull() ?: continue
                        val avgSignal = windowData.average().toFloat()

                        // Detect if signal is above threshold
                        // Use peak detection since IEM signals have strong carriers
                        if (peakSignal > threshold) {
                            detectedChannels.add(
                                IEMDetectedChannel(
                                    id = 0,
                                    scanResultId = 0, // Will be set when saving to DB
                                    channelId = channel.id,
                                    peakStrength = peakSignal,
                                    averageStrength = avgSignal,
                                    detectionConfidence = 1.0f
                                )
                            )

                            Log.d(TAG, "IEM detected: ${channel.frequency.asStringWithUnit("Hz")} - " +
                                    "peak=${peakSignal.toInt()}dB, avg=${avgSignal.toInt()}dB")
                        }
                    }
                }

            } finally {
                fftProcessorData.lock.readLock().unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting IEM channels in FFT", e)
        }

        return detectedChannels
    }

    // ===== Air Communication Scanner Functions =====

    private fun startAirCommScanning() {
        // Validate preconditions
        if (!appStateRepository.analyzerRunning.value) {
            showSnackbar(SnackbarEvent("Analyzer must be running to scan air communications"))
            return
        }
        if (appStateRepository.recordingRunning.value) {
            showSnackbar(SnackbarEvent("Cannot scan while recording is active"))
            return
        }

        val startFreq = appStateRepository.airCommStartFrequency.value
        val endFreq = appStateRepository.airCommEndFrequency.value

        if (startFreq >= endFreq) {
            showSnackbar(SnackbarEvent("Start frequency must be less than end frequency"))
            return
        }

        // Start Air Comm scanning
        appStateRepository.airCommScanRunning.set(true)
        appStateRepository.airCommSignalDetected.set(false)
        appStateRepository.airCommCurrentFrequency.set(startFreq)

        val stepSize = appStateRepository.airCommStepSize.value
        val dwellTime = appStateRepository.airCommDwellTime.value
        val hangTime = appStateRepository.airCommHangTime.value
        val useNoiseFloor = appStateRepository.airCommUseNoiseFloor.value
        val fixedThreshold = appStateRepository.airCommDetectionThreshold.value
        val noiseFloorMargin = appStateRepository.airCommNoiseFloorMargin.value

        // Auto-enable AM demodulation for air communications
        if (appStateRepository.demodulationMode.value != DemodulationMode.AM) {
            appStateRepository.demodulationMode.set(DemodulationMode.AM)
            Log.d(TAG, "Air Comm Scan: Auto-enabled AM demodulation")
        }

        airCommScanJob = viewModelScope.launch {
            try {
                // Estimate noise floor if enabled
                var noiseFloor = -80f
                if (useNoiseFloor) {
                    val noiseFloorSamples = mutableListOf<Float>()
                    // Sample 3 frequencies across the range
                    for (i in 0 until 3) {
                        val sampleFreq = startFreq + (i * (endFreq - startFreq) / 3)
                        appStateRepository.sourceFrequency.set(sampleFreq)
                        delay(dwellTime)
                        val avgLevel = getAverageSignalLevel()
                        if (avgLevel != null) noiseFloorSamples.add(avgLevel)
                    }

                    noiseFloor = if (noiseFloorSamples.isNotEmpty()) {
                        noiseFloorSamples.average().toFloat()
                    } else {
                        -80f
                    }
                    appStateRepository.noiseFloorLevel.set(noiseFloor)
                    Log.d(TAG, "Air Comm Scan: Estimated noise floor: $noiseFloor dB, margin: $noiseFloorMargin dB")
                }

                // Calculate effective threshold
                val effectiveThreshold = if (useNoiseFloor) {
                    noiseFloor + noiseFloorMargin
                } else {
                    fixedThreshold
                }

                // Get sample rate and calculate FFT batching
                val sampleRate = appStateRepository.sourceSampleRate.value
                val usableBandwidth = (sampleRate * 0.8).toLong()

                // Build list of target frequencies to scan
                val targetFrequencies = mutableListOf<Long>()
                var freq = startFreq
                while (freq <= endFreq) {
                    targetFrequencies.add(freq)
                    freq += stepSize
                }

                // Group frequencies into batches based on FFT coverage
                val frequencyBatches = mutableListOf<List<Long>>()
                var currentBatch = mutableListOf<Long>()
                var batchCenterFreq = 0L

                for (targetFreq in targetFrequencies) {
                    if (currentBatch.isEmpty()) {
                        currentBatch.add(targetFreq)
                        batchCenterFreq = targetFreq
                    } else {
                        val freqDiff = kotlin.math.abs(targetFreq - batchCenterFreq)
                        // Can we fit this frequency in current batch's FFT window?
                        if (freqDiff < usableBandwidth / 2) {
                            currentBatch.add(targetFreq)
                        } else {
                            // Start new batch
                            frequencyBatches.add(currentBatch)
                            currentBatch = mutableListOf(targetFreq)
                            batchCenterFreq = targetFreq
                        }
                    }
                }
                if (currentBatch.isNotEmpty()) {
                    frequencyBatches.add(currentBatch)
                }

                Log.d(TAG, "Air Comm Scan: Starting continuous scan from ${startFreq.asStringWithUnit("Hz")} to ${endFreq.asStringWithUnit("Hz")}")
                Log.d(TAG, "Air Comm Scan: Grouped ${targetFrequencies.size} frequencies into ${frequencyBatches.size} FFT batches (sample rate=${sampleRate.asStringWithUnit("Hz")}, usable=${usableBandwidth.asStringWithUnit("Hz")})")
                Log.d(TAG, "Air Comm Scan: Effective threshold=$effectiveThreshold dB")

                // Continuous scan loop
                while (appStateRepository.airCommScanRunning.value) {
                    // Scan through frequency batches
                    for (batch in frequencyBatches) {
                        if (!appStateRepository.airCommScanRunning.value) break

                        // Calculate center frequency for this batch
                        val batchCenter = (batch.first() + batch.last()) / 2

                        // Tune to batch center frequency ONCE
                        appStateRepository.sourceFrequency.set(batchCenter)

                        // Wait for FFT to settle
                        delay(dwellTime)

                        // Check each frequency in this batch
                        val exceptionList = appStateRepository.airCommExceptionList.value

                        for (targetFreq in batch) {
                            if (!appStateRepository.airCommScanRunning.value) break

                            appStateRepository.airCommCurrentFrequency.set(targetFreq)

                            // Skip if in exception list
                            if (exceptionList.contains(targetFreq)) {
                                continue
                            }

                            // Detect signal at this specific frequency within the FFT
                            val signalStrength = detectAirCommSignalAtFrequency(targetFreq, batchCenter, sampleRate)
                            appStateRepository.airCommSignalStrength.set(signalStrength ?: -999f)

                            if (signalStrength != null && signalStrength > effectiveThreshold) {
                                // SIGNAL DETECTED - PAUSE SCANNING
                                appStateRepository.airCommSignalDetected.set(true)
                                Log.d(TAG, "Air Comm Scan: Signal detected at ${targetFreq.asStringWithUnit("Hz")}, strength=$signalStrength dB - PAUSED")

                                // Tune to the detected frequency for better reception
                                appStateRepository.sourceFrequency.set(targetFreq)
                                appStateRepository.channelFrequency.set(targetFreq)
                                delay(dwellTime)

                                // Monitor signal while active
                                var addedToExceptionList = false
                                while (appStateRepository.airCommScanRunning.value) {
                                    delay(100) // Check signal every 100ms

                                    // Check if this frequency was added to exception list
                                    val currentExceptionList = appStateRepository.airCommExceptionList.value
                                    if (currentExceptionList.contains(targetFreq)) {
                                        Log.d(TAG, "Air Comm Scan: Frequency ${targetFreq.asStringWithUnit("Hz")} added to exception list, resuming scan")
                                        addedToExceptionList = true
                                        break
                                    }

                                    val currentSignal = detectAirCommSignal()
                                    appStateRepository.airCommSignalStrength.set(currentSignal ?: -999f)

                                    // Signal dropped below threshold
                                    if (currentSignal == null || currentSignal <= effectiveThreshold) {
                                        Log.d(TAG, "Air Comm Scan: Signal dropped, entering hang time (${hangTime}ms)")
                                        break
                                    }
                                }

                                // HANG TIME - Wait before resuming scan (skip if added to exception list)
                                appStateRepository.airCommSignalDetected.set(false)
                                if (!addedToExceptionList && hangTime > 0 && appStateRepository.airCommScanRunning.value) {
                                    delay(hangTime)
                                }

                                // Break out of frequency loop to restart batch scanning
                                break
                            }
                        }
                    }

                    // Loop completed, restart from beginning
                    Log.d(TAG, "Air Comm Scan: Scan loop completed, restarting...")
                }

            } catch (e: CancellationException) {
                // Normal cancellation - don't show error
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during air comm scanning", e)
                showSnackbar(SnackbarEvent("Air comm scan failed: ${e.message}"))
            } finally {
                appStateRepository.airCommScanRunning.set(false)
                appStateRepository.airCommSignalDetected.set(false)
                appStateRepository.airCommSignalStrength.set(-999f)
            }
        }
    }

    private fun stopAirCommScanning() {
        airCommScanJob?.cancel()
        appStateRepository.airCommScanRunning.set(false)
        appStateRepository.airCommSignalDetected.set(false)
        showSnackbar(SnackbarEvent("Air comm scan stopped"))
    }

    /**
     * Detects air communication signals at current frequency
     * Returns signal strength in dB or null if no signal detected
     */
    private fun detectAirCommSignal(): Float? {
        val fftProcessorData = appStateRepository.fftProcessorData

        return try {
            fftProcessorData.lock.readLock().lock()
            try {
                val waterfallBuffer = fftProcessorData.waterfallBuffer ?: return null
                if (waterfallBuffer.isEmpty()) return null

                val readIndex = fftProcessorData.readIndex
                if (readIndex < 0 || readIndex >= waterfallBuffer.size) return null

                val currentFFT = waterfallBuffer[readIndex]
                if (currentFFT.isEmpty()) return null

                // For AM aviation signals, analyze center ±12.5 kHz window
                // (25 kHz channel spacing, so ±12.5 kHz covers the signal)
                val fftSize = currentFFT.size
                val centerBin = fftSize / 2

                // Calculate window size (±12.5 kHz)
                val sampleRate = appStateRepository.sourceSampleRate.value
                val frequencyResolution = sampleRate.toFloat() / fftSize
                val windowHalfSize = (12500 / frequencyResolution).toInt().coerceAtLeast(3)

                val windowStart = (centerBin - windowHalfSize).coerceAtLeast(0)
                val windowEnd = (centerBin + windowHalfSize).coerceAtMost(currentFFT.size - 1)

                val windowData = currentFFT.sliceArray(windowStart..windowEnd)
                val peakSignal = windowData.maxOrNull() ?: return null

                peakSignal

            } finally {
                fftProcessorData.lock.readLock().unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting air comm signal", e)
            null
        }
    }

    /**
     * Detects air communication signal at a specific frequency within the current FFT
     * Used for FFT batching optimization - analyzes targetFreq when tuned to batchCenter
     *
     * @param targetFreq The actual frequency to check for signal
     * @param batchCenter The frequency we're currently tuned to
     * @param sampleRate Current sample rate (determines FFT coverage)
     * @return Signal strength in dB or null if no signal detected
     */
    private fun detectAirCommSignalAtFrequency(
        targetFreq: Long,
        batchCenter: Long,
        sampleRate: Long
    ): Float? {
        val fftProcessorData = appStateRepository.fftProcessorData

        return try {
            fftProcessorData.lock.readLock().lock()
            try {
                val waterfallBuffer = fftProcessorData.waterfallBuffer ?: return null
                if (waterfallBuffer.isEmpty()) return null

                val readIndex = fftProcessorData.readIndex
                if (readIndex < 0 || readIndex >= waterfallBuffer.size) return null

                val currentFFT = waterfallBuffer[readIndex]
                if (currentFFT.isEmpty()) return null

                // Calculate FFT parameters
                val fftSize = currentFFT.size
                val frequencyResolution = sampleRate.toFloat() / fftSize
                val startFrequency = batchCenter - sampleRate / 2

                // Calculate bin index for target frequency
                val frequencyOffset = targetFreq - startFrequency
                val binIndex = (frequencyOffset / frequencyResolution).toInt()

                // Check if target frequency is within FFT range
                if (binIndex !in currentFFT.indices) return null

                // Analyze ±12.5 kHz window (25 kHz channel spacing)
                val windowHalfSize = (12500 / frequencyResolution).toInt().coerceAtLeast(3)
                val windowStart = (binIndex - windowHalfSize).coerceAtLeast(0)
                val windowEnd = (binIndex + windowHalfSize).coerceAtMost(currentFFT.size - 1)

                val windowData = currentFFT.sliceArray(windowStart..windowEnd)
                val peakSignal = windowData.maxOrNull() ?: return null

                peakSignal

            } finally {
                fftProcessorData.lock.readLock().unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting air comm signal at frequency $targetFreq", e)
            null
        }
    }

    private fun startScanning() {
        // Validate preconditions
        if (!appStateRepository.analyzerRunning.value) {
            showSnackbar(SnackbarEvent("Analyzer must be running to scan"))
            return
        }
        if (appStateRepository.recordingRunning.value) {
            showSnackbar(SnackbarEvent("Cannot scan while recording is active"))
            return
        }
        if (appStateRepository.scanStartFrequency.value >= appStateRepository.scanEndFrequency.value) {
            showSnackbar(SnackbarEvent("Start frequency must be less than end frequency"))
            return
        }

        // Start scanning
        appStateRepository.scanRunning.set(true)
        appStateRepository.scanProgress.set(0f)
        appStateRepository.discoveredSignals.set(emptyList())

        val startFreq = appStateRepository.scanStartFrequency.value
        val endFreq = appStateRepository.scanEndFrequency.value
        val stepSize = appStateRepository.scanStepSize.value
        val dwellTime = appStateRepository.scanDwellTime.value
        val threshold = appStateRepository.scanThreshold.value
        val detectionMode = appStateRepository.scanDetectionMode.value
        val noiseFloorMargin = appStateRepository.scanNoiseFloorMargin.value
        val enableGrouping = appStateRepository.scanEnableSignalGrouping.value
        val minimumGap = appStateRepository.scanMinimumGap.value

        scanJob = viewModelScope.launch {
            val rawDetectionsList = mutableListOf<DiscoveredSignal>()

            try {
                // Get current sample rate (FFT bandwidth)
                val sampleRate = appStateRepository.sourceSampleRate.value

                // Use middle 80% of FFT bandwidth to avoid edge effects
                val usableBandwidth = (sampleRate * 0.8).toLong()

                Log.d(TAG, "Scanning with FFT bandwidth optimization: sampleRate=$sampleRate, usableBandwidth=$usableBandwidth")

                // Step 1: Estimate noise floor (sample 5 positions across the range)
                val noiseFloorSamples = mutableListOf<Float>()
                for (i in 0 until 5) {
                    val sampleFreq = startFreq + (i * (endFreq - startFreq) / 5)
                    appStateRepository.sourceFrequency.set(sampleFreq)
                    delay(dwellTime)
                    val avgLevel = getAverageSignalLevel()
                    if (avgLevel != null) noiseFloorSamples.add(avgLevel)
                }
                val noiseFloor = if (noiseFloorSamples.isNotEmpty()) {
                    noiseFloorSamples.average().toFloat()
                } else {
                    -80f // Default if estimation fails
                }
                appStateRepository.noiseFloorLevel.set(noiseFloor)
                Log.d(TAG, "Estimated noise floor: $noiseFloor dB, margin: $noiseFloorMargin dB")

                // Step 2: Scan using FFT bandwidth (much more efficient!)
                // Instead of tuning to each frequency, we tune once and analyze the entire FFT
                var tuneFrequency = startFreq
                val totalRange = endFreq - startFreq
                var scannedRange = 0L

                while (tuneFrequency <= endFreq && appStateRepository.scanRunning.value) {
                    // Tune to this position
                    appStateRepository.sourceFrequency.set(tuneFrequency)
                    appStateRepository.currentScanFrequency.set(tuneFrequency)

                    // Wait for FFT to settle
                    delay(dwellTime)

                    // Analyze all frequencies in the usable FFT bandwidth
                    val detectedSignals = detectSignalsInFFT(
                        tuneFrequency,
                        sampleRate,
                        usableBandwidth,
                        stepSize,
                        threshold,
                        detectionMode,
                        noiseFloor,
                        noiseFloorMargin,
                        startFreq,
                        endFreq
                    )

                    rawDetectionsList.addAll(detectedSignals)

                    // Update progress
                    scannedRange = minOf(tuneFrequency - startFreq + usableBandwidth, totalRange)
                    val progress = scannedRange.toFloat() / totalRange
                    appStateRepository.scanProgress.set(progress)

                    // Move to next FFT window (with small overlap to avoid missing signals)
                    tuneFrequency += (usableBandwidth * 0.9).toLong()
                }

                // Step 3: Group signals if enabled
                val finalSignalsList = if (enableGrouping && rawDetectionsList.isNotEmpty()) {
                    groupSignals(rawDetectionsList, stepSize, minimumGap)
                } else {
                    rawDetectionsList
                }

                // Update UI with final results
                appStateRepository.discoveredSignals.set(finalSignalsList)

                // Save scan results to database
                if (finalSignalsList.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val scanResult = ScanResult(
                            timestamp = System.currentTimeMillis(),
                            startFrequency = startFreq,
                            endFrequency = endFreq,
                            threshold = threshold,
                            stepSize = stepSize,
                            dwellTime = dwellTime
                        )
                        val scanResultId = scanDao.saveScanWithSignals(scanResult, finalSignalsList)
                        appStateRepository.currentScanResultId.set(scanResultId)
                    }
                }

                showSnackbar(SnackbarEvent("Scan complete. Found ${finalSignalsList.size} signals (${rawDetectionsList.size} raw detections)."))
            } catch (e: Exception) {
                Log.e(TAG, "Error during scanning", e)
                showSnackbar(SnackbarEvent("Scan failed: ${e.message}"))
            } finally {
                appStateRepository.scanRunning.set(false)
            }
        }
    }

    private fun stopScanning() {
        scanJob?.cancel()
        appStateRepository.scanRunning.set(false)
        showSnackbar(SnackbarEvent("Scan stopped"))
    }

    private fun getAverageSignalLevel(): Float? {
        val fftProcessorData = appStateRepository.fftProcessorData
        return try {
            fftProcessorData.lock.readLock().lock()
            try {
                val waterfallBuffer = fftProcessorData.waterfallBuffer
                if (waterfallBuffer == null || waterfallBuffer.isEmpty()) return null

                val readIndex = fftProcessorData.readIndex
                if (readIndex < 0 || readIndex >= waterfallBuffer.size) return null

                val currentFFT = waterfallBuffer[readIndex]
                if (currentFFT.isEmpty()) return null

                currentFFT.average().toFloat()
            } finally {
                fftProcessorData.lock.readLock().unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting average signal level", e)
            null
        }
    }

    private fun detectSignal(threshold: Float, mode: ScanDetectionMode, noiseFloor: Float, noiseFloorMargin: Float): Pair<Float, Float>? {
        val fftProcessorData = appStateRepository.fftProcessorData

        return try {
            fftProcessorData.lock.readLock().lock()
            try {
                val waterfallBuffer = fftProcessorData.waterfallBuffer
                if (waterfallBuffer == null || waterfallBuffer.isEmpty()) return null

                val readIndex = fftProcessorData.readIndex
                if (readIndex < 0 || readIndex >= waterfallBuffer.size) return null

                val currentFFT = waterfallBuffer[readIndex]
                if (currentFFT.isEmpty()) return null

                // Calculate peak and average signal strength
                val peakSignal = currentFFT.maxOrNull() ?: return null
                val avgSignal = currentFFT.average().toFloat()

                // Use noise floor + margin as effective threshold
                val effectiveThreshold = maxOf(threshold, noiseFloor + noiseFloorMargin)

                // Apply detection mode
                val detected = when (mode) {
                    ScanDetectionMode.PEAK_ONLY -> peakSignal > effectiveThreshold
                    ScanDetectionMode.AVERAGE_ONLY -> avgSignal > effectiveThreshold
                    ScanDetectionMode.PEAK_OR_AVERAGE -> peakSignal > effectiveThreshold || avgSignal > effectiveThreshold
                }

                if (detected) {
                    Pair(peakSignal, avgSignal)
                } else {
                    null
                }
            } finally {
                fftProcessorData.lock.readLock().unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting signal", e)
            null
        }
    }

    /**
     * Analyzes all frequencies in the current FFT and returns detected signals
     * This is much more efficient than tuning to each individual frequency
     */
    private fun detectSignalsInFFT(
        centerFrequency: Long,
        sampleRate: Long,
        usableBandwidth: Long,
        stepSize: Long,
        threshold: Float,
        mode: ScanDetectionMode,
        noiseFloor: Float,
        noiseFloorMargin: Float,
        scanStartFreq: Long,
        scanEndFreq: Long
    ): List<DiscoveredSignal> {
        val fftProcessorData = appStateRepository.fftProcessorData
        val detectedSignals = mutableListOf<DiscoveredSignal>()

        try {
            fftProcessorData.lock.readLock().lock()
            try {
                val waterfallBuffer = fftProcessorData.waterfallBuffer ?: return emptyList()
                if (waterfallBuffer.isEmpty()) return emptyList()

                val readIndex = fftProcessorData.readIndex
                if (readIndex < 0 || readIndex >= waterfallBuffer.size) return emptyList()

                val currentFFT = waterfallBuffer[readIndex]
                if (currentFFT.isEmpty()) return emptyList()

                // Calculate FFT parameters
                val fftSize = currentFFT.size
                val frequencyResolution = sampleRate.toFloat() / fftSize
                val startFrequency = centerFrequency - sampleRate / 2
                val usableStartOffset = ((sampleRate - usableBandwidth) / 2).toLong()
                val usableEndOffset = usableStartOffset + usableBandwidth

                // Use noise floor + margin as effective threshold
                val effectiveThreshold = maxOf(threshold, noiseFloor + noiseFloorMargin)

                // Scan through the FFT at stepSize intervals
                var currentFreq = maxOf(scanStartFreq, startFrequency + usableStartOffset)
                val endFreq = minOf(scanEndFreq, startFrequency + usableEndOffset)

                while (currentFreq <= endFreq) {
                    // Calculate FFT bin index for this frequency
                    val binIndex = ((currentFreq - startFrequency) / frequencyResolution).toInt()

                    if (binIndex in currentFFT.indices) {
                        // Analyze a small window around the bin (e.g., ±2 bins)
                        val windowStart = maxOf(0, binIndex - 2)
                        val windowEnd = minOf(currentFFT.size - 1, binIndex + 2)

                        val windowData = currentFFT.sliceArray(windowStart..windowEnd)
                        val peakSignal = windowData.maxOrNull() ?: 0f
                        val avgSignal = windowData.average().toFloat()

                        // Apply detection mode
                        val detected = when (mode) {
                            ScanDetectionMode.PEAK_ONLY -> peakSignal > effectiveThreshold
                            ScanDetectionMode.AVERAGE_ONLY -> avgSignal > effectiveThreshold
                            ScanDetectionMode.PEAK_OR_AVERAGE -> peakSignal > effectiveThreshold || avgSignal > effectiveThreshold
                        }

                        if (detected) {
                            detectedSignals.add(
                                DiscoveredSignal(
                                    id = 0,
                                    scanResultId = 0,
                                    frequency = currentFreq,
                                    peakStrength = peakSignal,
                                    averageStrength = avgSignal,
                                    bandwidth = 0,
                                    isGrouped = false
                                )
                            )
                        }
                    }

                    currentFreq += stepSize
                }

            } finally {
                fftProcessorData.lock.readLock().unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting signals in FFT", e)
        }

        return detectedSignals
    }

    private fun groupSignals(signals: List<DiscoveredSignal>, stepSize: Long, minimumGap: Int): List<DiscoveredSignal> {
        if (signals.isEmpty()) return emptyList()

        // Sort signals by frequency
        val sortedSignals = signals.sortedBy { it.frequency }
        val groupedSignals = mutableListOf<DiscoveredSignal>()
        val gapThreshold = stepSize * minimumGap

        var currentGroup = mutableListOf<DiscoveredSignal>()
        currentGroup.add(sortedSignals[0])

        for (i in 1 until sortedSignals.size) {
            val prevSignal = sortedSignals[i - 1]
            val currentSignal = sortedSignals[i]
            val gap = currentSignal.frequency - prevSignal.frequency

            if (gap <= gapThreshold) {
                // Signals are close, add to current group
                currentGroup.add(currentSignal)
            } else {
                // Gap is too large, finalize current group and start new one
                groupedSignals.add(finalizeGroup(currentGroup))
                currentGroup = mutableListOf(currentSignal)
            }
        }

        // Finalize last group
        groupedSignals.add(finalizeGroup(currentGroup))

        return groupedSignals
    }

    private fun finalizeGroup(group: List<DiscoveredSignal>): DiscoveredSignal {
        if (group.size == 1) {
            // Single signal, return as-is
            return group[0]
        }

        // Multiple signals, create grouped signal
        val minFreq = group.minOf { it.frequency }
        val maxFreq = group.maxOf { it.frequency }
        val centerFreq = (minFreq + maxFreq) / 2
        val bandwidth = maxFreq - minFreq
        val maxPeak = group.maxOf { it.peakStrength }
        val avgOfAverages = group.map { it.averageStrength }.average().toFloat()

        return DiscoveredSignal(
            id = 0,
            scanResultId = 0,
            frequency = centerFreq,
            peakStrength = maxPeak,
            averageStrength = avgOfAverages,
            bandwidth = bandwidth,
            isGrouped = true
        )
    }

    val settingsTabActions = SettingsTabActions(
        onScreenOrientationChanged = appStateRepository.screenOrientation::set,
        onFontSizeChanged = appStateRepository.fontSize::set,
        onColorThemeChanged = appStateRepository.colorTheme::set,
        onLongPressHelpEnabledChanged = appStateRepository.longPressHelpEnabled::set,
        onReverseTuningWheelChanged = appStateRepository.reverseTuningWheel::set,
        onControlDrawerSideChanged = appStateRepository.controlDrawerSide::set,
        onRtlsdrAllowOutOfBoundFrequencyChanged = appStateRepository.rtlsdrAllowOutOfBoundFrequency::set,
        onShowDebugInformationChanged = appStateRepository.showDebugInformation::set,
        onLoggingEnabledChanged = appStateRepository.loggingEnabled::set,
        onShowLogClicked = { sendActionToUi(UiAction.OnShowLogFileClicked) },
        onSaveLogToFileClicked = { destUri -> sendActionToUi(UiAction.OnSaveLogToFileClicked(destUri)) },
        onShareLogClicked = { sendActionToUi(UiAction.OnShareLogFileClicked) },
        onDeleteLogClicked = {
            showSnackbar(SnackbarEvent(
                message = "Log file deleted",
                buttonText = "Undo",
                callback = { snackbarResult ->
                    if (snackbarResult != SnackbarResult.ActionPerformed) {
                        sendActionToUi(UiAction.OnDeleteLogFileClicked)
                    }
                }
            )
            )
        },
    )

    val aboutTabActions = AboutTabActions(
        onAboutClicked = { navigate(AppScreen.AboutScreen) },
        onManualClicked = { navigate(AppScreen.ManualScreen()) },
        onTutorialClicked = { navigate(AppScreen.WelcomeScreen) },
        onBuyFullVersionClicked = { sendActionToUi(UiAction.OnBuyFullVersionClicked) }
    )

    // Surface ACTIONS ------------------------------------------------------------------------
    val analyzerSurfaceActions = AnalyzerSurfaceActions(
        onViewportFrequencyChanged = this::setViewportFrequency,
        onViewportSampleRateChanged = { newSampleRate ->
            appStateRepository.viewportSampleRate.set(newSampleRate)

            // Automatically re-adjust the sample rate of the source if we zoom too far out or in (only if not recording!)
            if (appStateRepository.sourceAutomaticSampleRateAdjustment.value && appStateRepository.analyzerRunning.value && !appStateRepository.recordingRunning.value) {
                val optimalSampleRates = appStateRepository.sourceSupportedSampleRates.value
                val bestSampleRate = optimalSampleRates.firstOrNull { it > appStateRepository.viewportSampleRate.value } ?: optimalSampleRates.last()
                if(appStateRepository.sourceSampleRate.value != bestSampleRate) {
                    appStateRepository.sourceSampleRate.set(bestSampleRate)
                }
            }
        },
        onViewportVerticalScaleChanged = { verticalScalePair ->
            appStateRepository.viewportVerticalScaleMin.set(verticalScalePair.first)
            appStateRepository.viewportVerticalScaleMax.set(verticalScalePair.second)
            val coercedSquelch = appStateRepository.squelch.value.coerceIn(verticalScalePair.first, verticalScalePair.second)
            if (appStateRepository.squelch.value != coercedSquelch)
                appStateRepository.squelch.set(coercedSquelch)
        },
        onChannelFrequencyChanged = { newFrequency ->
            appStateRepository.channelFrequency.set(newFrequency)
            if (appStateRepository.keepChannelCentered.value)
                setViewportFrequency(newFrequency)
        },
        onChannelWidthChanged = { newWidth -> appStateRepository.channelWidth.set(newWidth.coerceIn(appStateRepository.demodulationMode.value.minChannelWidth, appStateRepository.demodulationMode.value.maxChannelWidth)) },
        onSquelchChanged = appStateRepository.squelch::set
    )

    // RecordingScreen ACTIONS ------------------------------------------------------------------------
    val recordingsScreenActions = RecordingScreenActions(
        onDelete = { recording -> deleteRecordingWithUndo(recording) },
        onPlay = { recording ->
            showLoadingIndicator(true)
            // function which loads the recording and starts the analyzer:
            fun playRecording() {
                if(!appStateRepository.analyzerRunning.value) {
                    appStateRepository.sourceType.set(SourceType.FILESOURCE)
                    appStateRepository.filesourceUri.set(recording.filePath)

                    // Extract filename - handle both File paths and content:// URIs
                    val filename = if (recording.filePath.startsWith("content://")) {
                        try {
                            val uri = Uri.parse(recording.filePath)
                            val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                            docFile?.name ?: "recording.iq"
                        } catch (e: Exception) {
                            Log.e(TAG, "playRecording: Error getting filename from URI: ${e.message}", e)
                            "recording.iq"
                        }
                    } else {
                        File(recording.filePath).name
                    }
                    appStateRepository.filesourceFilename.set(filename)

                    appStateRepository.sourceFrequency.set(recording.frequency)
                    appStateRepository.sourceSampleRate.set(recording.sampleRate)
                    appStateRepository.filesourceFileFormat.set(recording.fileFormat)
                    navigate(AppScreen.MainScreen)
                    Log.i(TAG, "recordingScreenActions.onPlay: Start playing")
                    sendActionToUi(UiAction.OnStartClicked)
                    showLoadingIndicator(false)
                }
            }
            if(appStateRepository.analyzerRunning.value) {
                Log.i(TAG, "recordingScreenActions.onPlay: Stopping analyzer")
                sendActionToUi(UiAction.OnStopClicked)  // stop current analyzer
                Log.i(TAG, "recordingScreenActions.onPlay: Start playing in 2 seconds...")
                viewModelScope.launch {
                    delay(2000)  // delay for 2 seconds to give the analyzer time to shut down properly
                    playRecording()
                }
            } else {
                playRecording() // start playing immediately if analyzer was off previously
            }
        },
        onToggleFavorite = { recording ->
            viewModelScope.launch(Dispatchers.IO) {
                recordingDao.toggleFavorite(recording.id)
            }
        },
        onSaveToStorage = { recording, destUri -> sendActionToUi(UiAction.OnSaveRecordingClicked(recording.filePath, destUri)) },
        onShare = { recording -> sendActionToUi(UiAction.OnShareRecordingClicked(recording.filePath)) },
        onDeleteAll = {
            sendActionToUi(UiAction.ShowDialog(
                title = "Delete ALL Recordings?",
                msg = "Do you really want to delete all recordings (cannot be un-done)?",
                positiveButton = "Yes, delete all!",
                negativeButton = "Cancel",
                action = {
                    deleteAllRecordings()
                    sendActionToUi(UiAction.OnDeleteAllRecordingsClicked)
                }
            ))
        },
        onToggleDisplayOnlyFavorites = { appStateRepository.displayOnlyFavoriteRecordings.set(!appStateRepository.displayOnlyFavoriteRecordings.value) },
        renameRecording = { recording, newNameRaw ->
            viewModelScope.launch(Dispatchers.IO) {
                val newName = newNameRaw.replace('/', '_')
                recordingDao.rename(recording.id, newName)
                val renamedRecording = recordingDao.get(recording.id)
                val oldFile = File(recording.filePath)
                val newFileName = renamedRecording.calculateFileName()
                sendActionToUi(UiAction.RenameFile(oldFile, newFileName))
                val newPath = "${oldFile.parent}/${newFileName}"
                recordingDao.setFilePath(recording.id, newPath)
            }
        }
    )

    init {
        viewModelScope.collectAppState(appStateRepository.appUsageTimeInSeconds) { usageTime ->
            if (appStateRepository.settingsLoaded.value) {
                if (!BuildConfig.IS_FOSS && !appStateRepository.isFullVersion.value) {
                    // TRIAL
                    if (usageTime % 30 == 0) {
                        Log.d(TAG, "init (collect appUsageTimeInSeconds): usageTime=$usageTime  (start query for purchases...)")
                        checkPurchases()
                    }
                    if (appStateRepository.isAppUsageTimeUsedUp.value) {
                        if (gracePeriodCountdown != 0) {
                            gracePeriodCountdown--
                        } else {
                            sendActionToUi(UiAction.OnStopClicked)
                            gracePeriodCountdown = GRACE_PERIOD_SECONDS
                            showUsageTimeUsedUpDialog()
                        }
                    }
                }
            }
        }

        viewModelScope.collectAppState(appStateRepository.isFullVersion) { isFullVersion ->
            if(appStateRepository.settingsLoaded.value) {
                if (isFullVersion) {
                    Log.d(TAG, "init (collect isFullVersion): isFullVersion -> TRUE!")
                    showSnackbar(SnackbarEvent("RF Analyzer FULL VERSION unlocked!"))
                } else {
                    Log.d(TAG, "init (collect isFullVersion): isFullVersion -> FALSE!")
                    showSnackbar(SnackbarEvent("RF Analyzer Full Version refunded. App is now TRIAL VERSION."))
                }
            }
        }

        viewModelScope.collectAppState(appStateRepository.isPurchasePending) { isPurchasePending ->
            if (isPurchasePending) {
                Log.d(TAG, "init (collect isPurchasePending): There is a pending purchase!")
                showSnackbar(SnackbarEvent("Purchase of FULL VERSION is pending.."))
            }
        }

        // Observe and handle the analyzer events
        viewModelScope.launch {
            appStateRepository.analyzerEvents.collect { event ->
                when (event) {
                    is AppStateRepository.AnalyzerEvent.RecordingFinished -> {
                        // Handle both File (internal storage) and Uri (SAF) references
                        val fileRef = event.recordingFileRef
                        val customDirUri = appStateRepository.recordingDirectoryUri.value

                        val finalFilePath: String = if (fileRef is android.net.Uri) {
                            // SAF file - rename it using the tree URI
                            try {
                                // Get the tree URI (directory) from settings
                                val treeUri = android.net.Uri.parse(customDirUri)
                                val docTree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)

                                if (docTree == null) {
                                    Log.e(TAG, "RecordingFinished: Could not get directory DocumentFile from tree URI")
                                    fileRef.toString()
                                } else {
                                    // Find the ongoing_recording.iq file in the directory
                                    val ongoingFile = docTree.findFile("ongoing_recording.iq")

                                    if (ongoingFile == null) {
                                        Log.e(TAG, "RecordingFinished: Could not find ongoing_recording.iq in directory")
                                        fileRef.toString()
                                    } else {
                                        val finalFilename = Recording(
                                            name = appStateRepository.recordingName.value,
                                            frequency = appStateRepository.sourceFrequency.value,
                                            sampleRate = appStateRepository.sourceSampleRate.value,
                                            date = appStateRepository.recordingStartedTimestamp.value,
                                            fileFormat = when(appStateRepository.sourceType.value) {
                                                SourceType.HACKRF -> FilesourceFileFormat.HACKRF
                                                SourceType.RTLSDR -> FilesourceFileFormat.RTLSDR
                                                SourceType.AIRSPY -> FilesourceFileFormat.AIRSPY
                                                SourceType.HYDRASDR -> FilesourceFileFormat.HYDRASDR
                                                SourceType.FILESOURCE -> appStateRepository.filesourceFileFormat.value
                                            },
                                            sizeInBytes = 0L,
                                            filePath = "",
                                            favorite = false
                                        ).calculateFileName()

                                        val renamed = ongoingFile.renameTo(finalFilename)
                                        if (renamed) {
                                            Log.i(TAG, "RecordingFinished: Successfully renamed to $finalFilename")
                                            // Return the new URI after rename
                                            ongoingFile.uri.toString()
                                        } else {
                                            Log.e(TAG, "RecordingFinished: Failed to rename file to $finalFilename")
                                            fileRef.toString()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "RecordingFinished: Exception while renaming SAF file: ${e.message}", e)
                                fileRef.toString()
                            }
                        } else if (fileRef is File) {
                            // Internal storage file - rename as before
                            val newRecording = Recording(
                                name = appStateRepository.recordingName.value,
                                frequency = appStateRepository.sourceFrequency.value,
                                sampleRate = appStateRepository.sourceSampleRate.value,
                                date = appStateRepository.recordingStartedTimestamp.value,
                                fileFormat = when(appStateRepository.sourceType.value) {
                                    SourceType.HACKRF -> FilesourceFileFormat.HACKRF
                                    SourceType.RTLSDR -> FilesourceFileFormat.RTLSDR
                                    SourceType.AIRSPY -> FilesourceFileFormat.AIRSPY
                                    SourceType.HYDRASDR -> FilesourceFileFormat.HYDRASDR
                                    SourceType.FILESOURCE -> appStateRepository.filesourceFileFormat.value
                                },
                                sizeInBytes = 0L,
                                filePath = "",
                                favorite = false
                            )
                            sendActionToUi(UiAction.RenameFile(fileRef, newRecording.calculateFileName()))
                            "${fileRef.parent}/${newRecording.calculateFileName()}"
                        } else {
                            Log.e(TAG, "RecordingFinished: Unknown file reference type: ${fileRef.javaClass.name}")
                            ""
                        }

                        val newRecording = Recording(
                            name = appStateRepository.recordingName.value,
                            frequency = appStateRepository.sourceFrequency.value,
                            sampleRate = appStateRepository.sourceSampleRate.value,
                            date = appStateRepository.recordingStartedTimestamp.value,
                            fileFormat = when(appStateRepository.sourceType.value) {
                                SourceType.HACKRF -> FilesourceFileFormat.HACKRF
                                SourceType.RTLSDR -> FilesourceFileFormat.RTLSDR
                                SourceType.AIRSPY -> FilesourceFileFormat.AIRSPY
                                SourceType.HYDRASDR -> FilesourceFileFormat.HYDRASDR
                                SourceType.FILESOURCE -> appStateRepository.filesourceFileFormat.value
                            },
                            sizeInBytes = event.finalSize,
                            filePath = finalFilePath,
                            favorite = false
                        )

                        insertRecording(newRecording) { insertedRecording ->
                            appStateRepository.recordingStartedTimestamp.set(0L)
                            appStateRepository.recordingRunning.set(false)

                            // Show notification for smartwatch/wearables
                            sendActionToUi(UiAction.ShowRecordingFinishedNotification(
                                insertedRecording.name,
                                insertedRecording.sizeInBytes
                            ))

                            showSnackbar(SnackbarEvent(
                                message = "Recording '${insertedRecording.name}' finished (${insertedRecording.sizeInBytes.asSizeInBytesToString()})",
                                buttonText = "Delete Recording",
                                callback = { askDeletionResult ->
                                    if(askDeletionResult == SnackbarResult.ActionPerformed) deleteRecordingWithUndo(insertedRecording)
                                }
                            ))
                        }
                    }
                    is AppStateRepository.AnalyzerEvent.SourceFailure ->
                        showSnackbar(SnackbarEvent(message = event.message))
                    null -> Log.e(TAG, "Event from AnalyzerService was null!")
                }
            }
        }
    }

    private fun setViewportFrequency(newViewportFrequnecy: Long) {
        val coercedFrequency = newViewportFrequnecy.coerceIn(appStateRepository.sourceSignalStartFrequency.value, appStateRepository.sourceSignalEndFrequency.value).coerceAtLeast(0)
        //Log.d(TAG, "setViewportFrequency: BEFORE: [vpFreq=${appStateRepository.viewportFrequency.value}] [vpStartFreq=${appStateRepository.viewportStartFrequency.value}]")
        appStateRepository.viewportFrequency.set(coercedFrequency)
        //Log.d(TAG, "setViewportFrequency: AFTER:  [vpFreq=${appStateRepository.viewportFrequency.value}] [vpStartFreq=${appStateRepository.viewportStartFrequency.value}]")

        if (appStateRepository.keepChannelCentered.value)
            appStateRepository.channelFrequency.set(coercedFrequency)

        // Automatically re-tune the source if we scrolled the samples out of the visible window:
        if (appStateRepository.analyzerRunning.value &&
            (appStateRepository.sourceSignalEndFrequency.value < appStateRepository.viewportEndFrequency.value ||
                    appStateRepository.sourceSignalStartFrequency.value > appStateRepository.viewportStartFrequency.value)
        ) {
            if(!appStateRepository.recordingRunning.value) {
                val tuneFreq = coercedFrequency.coerceIn(appStateRepository.sourceMinimumFrequency.value, appStateRepository.sourceMaximumFrequency.value)
                appStateRepository.sourceFrequency.set(tuneFreq)
            }
        }
    }

    private fun setChannelFrequency(newChannelFrequency: Long) {
        if (newChannelFrequency in appStateRepository.sourceSignalStartFrequency.value..appStateRepository.sourceSignalEndFrequency.value) {
            // move viewport if necessary:
            if (appStateRepository.keepChannelCentered.value) {
                setViewportFrequency(newChannelFrequency)
            } else if (newChannelFrequency !in appStateRepository.viewportStartFrequency.value..appStateRepository.viewportEndFrequency.value) {
                val newViewportFrequency =
                    if (newChannelFrequency < appStateRepository.viewportStartFrequency.value)
                        newChannelFrequency + appStateRepository.viewportSampleRate.value/2
                    else
                        newChannelFrequency - appStateRepository.viewportSampleRate.value/2
                setViewportFrequency(newViewportFrequency)
            }
            appStateRepository.channelFrequency.set(newChannelFrequency)
        }
        else {
            // Re-tune necessary
            val retuneAndUpdateChannel: () -> Unit = {
                val newSourceFrequency = newChannelFrequency + appStateRepository.channelWidth.value*2    // avoid DC peak by tuning not directly to channel frequnecy
                appStateRepository.sourceFrequency.set(newSourceFrequency)
                appStateRepository.channelFrequency.set(newChannelFrequency)
            }
            if (newChannelFrequency !in appStateRepository.sourceMinimumPossibleSignalFrequency.value..appStateRepository.sourceMaximumPossibleSignalFrequency.value) {
                showSnackbar(SnackbarEvent(message = "Frequency ${newChannelFrequency.asStringWithUnit("Hz")} is out of range for the current source"))
            } else if (appStateRepository.recordingRunning.value) {
                sendActionToUi(UiAction.ShowDialog(
                    title = "Recording Running",
                    msg = "Frequency out of range for the current recording. Stop Recording?",
                    positiveButton = "Yes, stop recording!",
                    negativeButton = "Cancel",
                    action = {
                        retuneAndUpdateChannel()
                    }
                ))
            } else {
                retuneAndUpdateChannel()
            }
        }
    }

    fun setFilesourceUri(uri: String, filename: String?) {
        var fileFormat = appStateRepository.filesourceFileFormat.value
        var frequency  = appStateRepository.sourceFrequency.value
        var sampleRate = appStateRepository.sourceSampleRate.value

        // Try to extract frequency, sample rate, and file format from file name
        if (filename != null) {
            try {
                // 1. Format. Search for strings like hackrf, rtl-sdr, ...
                if (filename.matches(".*hackrf.*".toRegex()) || filename.matches(".*HackRF.*".toRegex()) ||
                    filename.matches(".*HACKRF.*".toRegex()) || filename.matches(".*hackrfone.*".toRegex())
                ) fileFormat = FilesourceFileFormat.HACKRF
                if (filename.matches(".*rtlsdr.*".toRegex()) || filename.matches(".*rtl-sdr.*".toRegex()) ||
                    filename.matches(".*RTLSDR.*".toRegex()) || filename.matches(".*RTL-SDR.*".toRegex())
                ) fileFormat = FilesourceFileFormat.RTLSDR
                if (filename.matches(".*airspy.*".toRegex()) || filename.matches(".*Airspy.*".toRegex()) ||
                    filename.matches(".*AIRSPY.*".toRegex()) || filename.matches(".*AirSpy.*".toRegex())
                ) fileFormat = FilesourceFileFormat.AIRSPY
                if (filename.matches(".*hydrasdr.*".toRegex()) || filename.matches(".*HydraSDR.*".toRegex()) ||
                    filename.matches(".*HYDRASDR.*".toRegex()) || filename.matches(".*HydraSdr.*".toRegex())
                ) fileFormat = FilesourceFileFormat.HYDRASDR

                // 2. Sampe Rate. Search for pattern XXXXXXXSps
                if (filename.matches(".*(_|-|\\s)([0-9]+)(sps|Sps|SPS).*".toRegex())) sampleRate =
                    filename.replaceFirst(".*(_|-|\\s)([0-9]+)(sps|Sps|SPS).*".toRegex(), "$2").toLong()
                if (filename.matches(".*(_|-|\\s)([0-9]+)(ksps|Ksps|KSps|KSPS).*".toRegex())) sampleRate =
                    filename.replaceFirst(".*(_|-|\\s)([0-9]+)(ksps|Ksps|KSps|KSPS).*".toRegex(), "$2")
                        .toLong() * 1000
                if (filename.matches(".*(_|-|\\s)([0-9]+)(msps|Msps|MSps|MSPS).*".toRegex())) sampleRate =
                    filename.replaceFirst(".*(_|-|\\s)([0-9]+)(msps|Msps|MSps|MSPS).*".toRegex(), "$2")
                        .toLong() * 1000000

                // 3. Frequency. Search for pattern XXXXXXXHz
                if (filename.matches(".*(_|-|\\s)([0-9]+)(hz|Hz|HZ).*".toRegex())) frequency =
                    filename.replaceFirst(".*(_|-|\\s)([0-9]+)(hz|Hz|HZ).*".toRegex(), "$2").toLong()
                if (filename.matches(".*(_|-|\\s)([0-9]+)(khz|Khz|KHz|KHZ).*".toRegex())) frequency =
                    filename.replaceFirst(".*(_|-|\\s)([0-9]+)(khz|Khz|KHz|KHZ).*".toRegex(), "$2")
                        .toLong() * 1000
                if (filename.matches(".*(_|-|\\s)([0-9]+)(mhz|Mhz|MHz|MHZ).*".toRegex())) frequency =
                    filename.replaceFirst(".*(_|-|\\s)([0-9]+)(mhz|Mhz|MHz|MHZ).*".toRegex(), "$2")
                        .toLong() * 1000000
            } catch (e: NumberFormatException) {
                Log.i(TAG, "setFilesourceUri: Error parsing filename: " + e.message)
            }
        }
        appStateRepository.sourceType.set(SourceType.FILESOURCE)
        appStateRepository.filesourceUri.set(uri)
        appStateRepository.filesourceFilename.set(filename ?: uri)
        appStateRepository.sourceFrequency.set(frequency)
        appStateRepository.sourceSampleRate.set(sampleRate)
        appStateRepository.filesourceFileFormat.set(fileFormat)
    }

    fun loadLogs(logFile: File) {
        showLoadingIndicator(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lines = logFile.readLines()
                _logContent.value = lines
            } catch (e: Exception) {
                _logContent.value = listOf("Error loading log file: ${e.message}")
            } finally {
                showLoadingIndicator(false)
            }
        }
    }
}
