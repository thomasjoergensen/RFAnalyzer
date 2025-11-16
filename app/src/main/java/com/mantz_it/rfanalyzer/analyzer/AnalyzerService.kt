package com.mantz_it.rfanalyzer.analyzer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mantz_it.rfanalyzer.source.FileIQSource
import com.mantz_it.rfanalyzer.source.HackrfSource
import com.mantz_it.rfanalyzer.source.IQSourceInterface
import com.mantz_it.rfanalyzer.LogcatLogger
import com.mantz_it.rfanalyzer.R
import com.mantz_it.rfanalyzer.source.RtlsdrSource
import com.mantz_it.rfanalyzer.ui.RECORDINGS_DIRECTORY
import com.mantz_it.rfanalyzer.ui.composable.DemodulationMode
import com.mantz_it.rfanalyzer.ui.composable.SourceType
import com.mantz_it.rfanalyzer.ui.composable.StopAfterUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri
import com.mantz_it.rfanalyzer.database.AppStateRepository
import com.mantz_it.rfanalyzer.database.GlobalPerformanceData
import com.mantz_it.rfanalyzer.database.collectAppState
import com.mantz_it.rfanalyzer.source.AirspySource
import com.mantz_it.rfanalyzer.source.HydraSdrSource
import com.mantz_it.rfanalyzer.ui.MainActivity
import com.mantz_it.rfanalyzer.ui.composable.FilesourceFileFormat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs

/**
 * <h1>RF Analyzer - Analyzer Service</h1>
 *
 * Module:      AnalyzerService.kt
 * Description: Foreground Service which orchestrates the signal processing pipeline.
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

/**
 * Represents a complete processing pipeline for a single SDR device
 */
data class DevicePipeline(
    val id: String,                      // Unique device identifier (e.g., "hackrf_0", "rtlsdr_1")
    val sourceType: SourceType,          // Type of SDR device
    val source: IQSourceInterface,       // IQ source instance
    val scheduler: Scheduler,            // Scheduler for this device
    val demodulator: Demodulator,        // Demodulator for this device
    val fftProcessor: FftProcessor       // FFT processor for this device
)

@AndroidEntryPoint
class AnalyzerService : Service() {
    @Inject lateinit var appStateRepository: AppStateRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job()) // Coroutine scope for observing the App State
    private val binder = LocalBinder()
    private var isBound = false

    // Multi-device support: Map of device ID to pipeline
    private val activePipelines = mutableMapOf<String, DevicePipeline>()

    // Current active device for display/demodulation
    private var activeDeviceId: String? = null

    // Legacy compatibility: expose primary source as before
    val source: IQSourceInterface?
        get() = activePipelines[activeDeviceId]?.source
    val scheduler: Scheduler?
        get() = activePipelines[activeDeviceId]?.scheduler
    val demodulator: Demodulator?
        get() = activePipelines[activeDeviceId]?.demodulator
    val fftProcessor: FftProcessor?
        get() = activePipelines[activeDeviceId]?.fftProcessor

    inner class LocalBinder : Binder() {
        fun getService(): AnalyzerService = this@AnalyzerService
    }

    companion object {
        private const val TAG = "AnalyzerService"
        const val ACTION_STOP = "com.mantz_it.rfanalyzer.analyzer.ACTION_STOP"
    }

    // Map device-specific callbacks (each source gets its own callback that knows its device ID)
    private fun createIQSourceCallback(deviceId: String): IQSourceInterface.Callback {
        return object : IQSourceInterface.Callback {
            override fun onIQSourceReady(source: IQSourceInterface?) {
                serviceScope.launch {
                    Log.d(TAG, "onIQSourceReady for device $deviceId")
                    startSchedulerForDevice(deviceId)
                }
            }

            override fun onIQSourceError(source: IQSourceInterface?, message: String?) {
                val errorMessage = "Error with Source $deviceId (${source?.getName()}): $message"
                Log.e(TAG, "onIQSourceError: $errorMessage")
                serviceScope.launch {
                    appStateRepository.emitAnalyzerEvent(AppStateRepository.AnalyzerEvent.SourceFailure("Device $deviceId: $message"))
                    stopDevice(deviceId)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: Service is bound.")
        isBound = true
        return binder
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "onRebind: Service is bound.")
        isBound = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        if(appStateRepository.analyzerRunning.value) {
            Log.d(TAG, "onUnbind: Service is unbound. Keep running in the background")
            return true // don't terminate the service if the analyzer is still running
        } else {
            Log.d(TAG, "onUnbind: Service is unbound. Stop Service.")
            stopSelf()  // stop service if activity disconnects
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service created.")

        // Start Logging to file:
        if(appStateRepository.loggingEnabled.value)
            LogcatLogger.startLogging(this)

        handleAppStateChanges()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        when (intent?.action) {
            ACTION_STOP -> {
                // If the stop action is received from the notification's stop button
                Log.d(TAG, "onStartCommand: Stop action received from Notification. Stopping service.")
                stopAnalyzer()
                if(!isBound)
                    stopSelf() // Stop the service if activity is not connected
                return START_NOT_STICKY // Ensure the service is not restarted automatically
            }
            else -> {
                // This handles the case where the service is started by the activity or any other intent
                Log.d(TAG, "onStartCommand: Service started sticky.")
                // keep the service running:
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Clean up the coroutine scope
        stopAnalyzer() // make sure all threads are stopped
        Log.d(TAG, "onDestroy: Service destroyed.")
        LogcatLogger.stopLogging()
    }

    private fun stopForegroundService() {
        Log.d(TAG, "stopForegroundService: removing notification.")
        stopForeground(STOP_FOREGROUND_REMOVE) // This removes the notification
        stopSelf() // remove the 'started' state so the service stops when unbound
    }

    private fun startForegroundService() {
        val notification = createNotification()
        Log.d(TAG, "startForegroundService: Moving service to foreground.")
        ServiceCompat.startForeground(this, 1, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        // **Explicitly start the service so it doesn't stop when unbound**
        val serviceIntent = Intent(this, this::class.java)
        startService(serviceIntent)
    }

    private fun createNotification(): Notification {
        Log.d(TAG, "createNotification: Creating foreground notification.")

        // Intent to launch the main activity
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the service
        val stopIntent = Intent(this, AnalyzerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "SERVICE_CHANNEL")
            .setContentTitle("RF Analyzer")
            .setContentText("RF Analyzer is running...")
            .setSmallIcon(R.drawable.signal_wave)
            .setContentIntent(activityPendingIntent)
            .addAction(R.drawable.stop_circle, "Stop", pendingStopIntent)
            .build()
    }

    /**
     * Will stop the RF Analyzer. This includes shutting down all schedulers (which turn off the
     * sources) and the demodulators if running.
     */
    fun stopAnalyzer() {
        Log.i(TAG, "stopAnalyzer: Stopping all ${activePipelines.size} pipelines")

        // Stop all pipelines
        activePipelines.values.forEach { pipeline ->
            Log.d(TAG, "stopAnalyzer: Stopping pipeline ${pipeline.id}")
            // Stop the Scheduler if running:
            pipeline.scheduler.stopScheduler()

            // Stop the Demodulator if running:
            pipeline.demodulator.stopDemodulator()

            pipeline.fftProcessor.stopLoop()

            // Wait for the scheduler to stop:
            try {
                if (pipeline.scheduler.name != Thread.currentThread().name)
                    pipeline.scheduler.join()
            } catch (e: InterruptedException) {
                Log.e(TAG, "stopAnalyzer: Error while stopping Scheduler for ${pipeline.id}")
            }

            // Close the source
            pipeline.source.close()
        }

        // Clear all pipelines
        activePipelines.clear()
        activeDeviceId = null

        appStateRepository.sourceName.set("")
        appStateRepository.analyzerRunning.set(false)
        appStateRepository.analyzerStartPending.set(false)
        GlobalPerformanceData.reset()

        stopForegroundService()
    }

    /**
     * Will start the RF Analyzer. This includes creating a source (if null), open a source
     * (if not open), starting the scheduler (which starts the source) and starting the
     * processing loop.
     *
     * Legacy method - now delegates to per-device start
     */
    fun startAnalyzer() : Boolean {
        Log.d(TAG, "startAnalyzer (legacy)")
        val sourceType = appStateRepository.sourceType.value
        val deviceId = "${sourceType.name.lowercase()}_0"
        return startDevice(sourceType, deviceId)
    }

    /**
     * Start a specific SDR device and create its processing pipeline
     */
    fun startDevice(sourceType: SourceType, deviceId: String): Boolean {
        Log.d(TAG, "startDevice: Starting device $deviceId of type $sourceType")

        // Check if device is already running
        if (activePipelines.containsKey(deviceId)) {
            Log.w(TAG, "startDevice: Device $deviceId is already running")
            return true
        }

        // Create the source
        val newSource = createSourceForType(sourceType, deviceId) ?: return false

        // Open the source
        if (!openSourceForDevice(newSource, deviceId, sourceType)) {
            Toast.makeText(this, "Source not available ($deviceId - ${newSource.getName()})", Toast.LENGTH_LONG).show()
            appStateRepository.analyzerStartPending.set(false)
            return false
        }

        // If source opened synchronously, start scheduler; otherwise callback will do it
        if (newSource.isOpen()) {
            return startSchedulerForDevice(deviceId)
        }

        return true // Waiting for onIQSourceReady callback
    }

    /**
     * Stop a specific device pipeline
     */
    fun stopDevice(deviceId: String) {
        Log.d(TAG, "stopDevice: Stopping device $deviceId")

        val pipeline = activePipelines[deviceId] ?: run {
            Log.w(TAG, "stopDevice: Device $deviceId not found")
            return
        }

        // Stop the pipeline components
        pipeline.scheduler.stopScheduler()
        pipeline.demodulator.stopDemodulator()
        pipeline.fftProcessor.stopLoop()

        // Wait for scheduler to stop
        try {
            if (pipeline.scheduler.name != Thread.currentThread().name)
                pipeline.scheduler.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "stopDevice: Error while stopping Scheduler for $deviceId")
        }

        // Close the source
        pipeline.source.close()

        // Remove from active pipelines
        activePipelines.remove(deviceId)

        // If this was the active device, clear it
        if (activeDeviceId == deviceId) {
            activeDeviceId = activePipelines.keys.firstOrNull()
            Log.d(TAG, "stopDevice: Active device was stopped. New active device: $activeDeviceId")
        }

        // If no more pipelines, stop analyzer
        if (activePipelines.isEmpty()) {
            appStateRepository.analyzerRunning.set(false)
            appStateRepository.analyzerStartPending.set(false)
            stopForegroundService()
        }
    }

    /**
     * Set which device is active for display and demodulation
     */
    fun setActiveDevice(deviceId: String) {
        if (!activePipelines.containsKey(deviceId)) {
            Log.w(TAG, "setActiveDevice: Device $deviceId not found")
            return
        }

        Log.d(TAG, "setActiveDevice: Switching active device to $deviceId")
        activeDeviceId = deviceId

        val pipeline = activePipelines[deviceId]!!
        appStateRepository.sourceName.set(pipeline.source.name)
        appStateRepository.sourceFrequency.set(pipeline.source.frequency)
        appStateRepository.sourceSampleRate.set(pipeline.source.sampleRate.toLong())
    }

    /**
     * Get list of all active device IDs
     */
    fun getActiveDeviceIds(): List<String> = activePipelines.keys.toList()

    /**
     * Start scheduler for a specific device
     */
    private fun startSchedulerForDevice(deviceId: String): Boolean {
        Log.d(TAG, "startSchedulerForDevice: Starting scheduler for $deviceId")

        // Find if we have a temporary source waiting to be added
        val tempSource = tempSources[deviceId] ?: run {
            Log.e(TAG, "startSchedulerForDevice: No source found for $deviceId")
            return false
        }

        // Ensure that source sample rate is supported:
        val supportedSampleRates = tempSource.supportedSampleRates
        val currentSampleRate = tempSource.sampleRate
        if(!supportedSampleRates.contains(tempSource.sampleRate)) {
            val bestMatch = supportedSampleRates.minByOrNull { supportedSampleRate -> abs(supportedSampleRate - currentSampleRate) }
            if(bestMatch == null || bestMatch == 0)
                Log.w(TAG, "startSchedulerForDevice: Source sample rate $currentSampleRate is not supported! (${supportedSampleRates})")
            else
                tempSource.sampleRate = bestMatch
        }

        // Update UI for RTL-SDR gain steps (if this is the active device)
        if (tempSource is RtlsdrSource && activeDeviceId == null) {
            val currentGainIndex = appStateRepository.rtlsdrGainIndex.value
            val currentIFGainIndex = appStateRepository.rtlsdrIFGainIndex.value
            val gainIndexList = tempSource.possibleGainValues.toList()
            val ifGainIndexList = tempSource.possibleIFGainValues.toList()
            appStateRepository.rtlsdrGainIndex.set(0)
            appStateRepository.rtlsdrIFGainIndex.set(0)
            appStateRepository.rtlsdrGainSteps.set(gainIndexList)
            appStateRepository.rtlsdrIFGainSteps.set(ifGainIndexList)
            appStateRepository.rtlsdrGainIndex.set(currentGainIndex.coerceAtMost(gainIndexList.size - 1))
            appStateRepository.rtlsdrIFGainIndex.set(currentIFGainIndex.coerceAtMost(ifGainIndexList.size - 1))
        }

        // Create a new instance of Scheduler
        val newScheduler = Scheduler(appStateRepository.fftSize.value, tempSource)

        // Start the demodulator thread:
        val newDemodulator = Demodulator(
            newScheduler.demodOutputQueue,
            newScheduler.demodInputQueue,
            tempSource.packetSize / tempSource.bytesPerSample
        )
        newDemodulator.audioVolumeLevel = appStateRepository.effectiveAudioVolumeLevel.value
        newDemodulator.start()

        // Start the scheduler
        newScheduler.start()

        val newFftProcessor = FftProcessor(
            initialFftSize = appStateRepository.fftSize.value,
            inputQueue = newScheduler.fftOutputQueue,
            returnQueue = newScheduler.fftInputQueue,
            fftProcessorData = appStateRepository.fftProcessorData,
            appStateRepository.waterfallSpeed.value,
            fftPeakHold = appStateRepository.fftPeakHold.value,
            getChannelFrequencyRange = {
                val schedulerHandle = newScheduler
                val demodulatorHandle = newDemodulator
                Pair(
                    schedulerHandle.channelFrequency - demodulatorHandle.channelWidth,
                    schedulerHandle.channelFrequency + demodulatorHandle.channelWidth)
            },
            onAverageSignalStrengthChanged = appStateRepository.averageSignalStrength::set
        )
        newFftProcessor.start()

        // Create pipeline and add to active pipelines
        val pipeline = DevicePipeline(
            id = deviceId,
            sourceType = tempSources_types[deviceId]!!,
            source = tempSource,
            scheduler = newScheduler,
            demodulator = newDemodulator,
            fftProcessor = newFftProcessor
        )
        activePipelines[deviceId] = pipeline

        // Remove from temporary storage
        tempSources.remove(deviceId)
        tempSources_types.remove(deviceId)

        // If this is the first device, make it active
        if (activeDeviceId == null) {
            activeDeviceId = deviceId
            appStateRepository.sourceName.set(tempSource.name)
            appStateRepository.sourceMinimumFrequency.set(tempSource.minFrequency)
            appStateRepository.sourceMaximumFrequency.set(tempSource.maxFrequency)
            appStateRepository.sourceSupportedSampleRates.set(supportedSampleRates.map { it.toLong() })
            appStateRepository.sourceFrequency.set(tempSource.frequency)
            appStateRepository.sourceSampleRate.set(tempSource.sampleRate.toLong())

            applyNewDemodulationMode(appStateRepository.demodulationMode.value)
        }

        // Set state to running on first device
        if (activePipelines.size == 1) {
            appStateRepository.analyzerRunning.set(true)
            appStateRepository.analyzerStartPending.set(false)
            startForegroundService()
            launchSupervisionCoroutine()
        }

        // workaround for bug: when manual gain is enabled and the rtlsdr is started, the manual gain value is not accepted. so set it again here
        if (tempSource is RtlsdrSource && appStateRepository.rtlsdrManualGainEnabled.value) {
            tempSource.gain = appStateRepository.rtlsdrGainSteps.value[appStateRepository.rtlsdrGainIndex.value]
            tempSource.ifGain = appStateRepository.rtlsdrIFGainSteps.value[appStateRepository.rtlsdrIFGainIndex.value]
        }

        Log.i(TAG, "startSchedulerForDevice: Device $deviceId started successfully. Total active devices: ${activePipelines.size}")
        return true
    }

    // Temporary storage for sources being initialized (before scheduler is started)
    private val tempSources = mutableMapOf<String, IQSourceInterface>()
    private val tempSources_types = mutableMapOf<String, SourceType>()

    private fun startScheduler(): Boolean {
        // Legacy method - delegates to active device
        return activeDeviceId?.let { startSchedulerForDevice(it) } ?: false
    }

    private fun launchSupervisionCoroutine() {
        // Launch a coroutine to supervise the activity connection and measure running time
        serviceScope.launch {
            var secondsSinceActivityStopped = 0
            while (appStateRepository.analyzerRunning.value) {
                delay(1000) // Checks every second
                if (!isBound)
                    secondsSinceActivityStopped += 1
                else
                    secondsSinceActivityStopped = 0

                // Stop Analyzer after activity is absent for 5 seconds
                if (secondsSinceActivityStopped > 5 && !appStateRepository.recordingRunning.value && appStateRepository.demodulationMode.value == DemodulationMode.OFF) {
                    Log.i(TAG, "launchSupervisionCoroutine: Activity was unbound for 5 seconds. Stopping Analyzer..")
                    stopAnalyzer()
                }

                appStateRepository.appUsageTimeInSeconds.set(appStateRepository.appUsageTimeInSeconds.value + 1)  // increase app usage timer
            }
        }
    }

    private fun applyNewDemodulationMode(newDemodulationMode: DemodulationMode): Boolean {
        if(demodulator == null || source == null || scheduler == null)
            return false

        // (de-)activate demodulation in the scheduler and set the sample rate accordingly:
        if (newDemodulationMode == DemodulationMode.OFF) {
            Log.d(TAG, "applyNewDemodulationMode: De-activating demodulation")
            scheduler!!.isDemodulationActivated = false
            demodulator!!.demodulationMode = DemodulationMode.OFF
            return true
        }

        Log.d(TAG, "applyNewDemodulationMode: Switching to demodulation mode ${newDemodulationMode.displayName}")
        demodulator!!.demodulationMode = newDemodulationMode
        scheduler!!.isDemodulationActivated = true
        demodulator!!.channelWidth = appStateRepository.channelWidth.value
        scheduler!!.squelchSatisfied = appStateRepository.squelchSatisfied.value
        scheduler!!.channelFrequency = if(newDemodulationMode == DemodulationMode.CW)
                Demodulator.CW_OFFSET_FREQUENCY - appStateRepository.channelFrequency.value
            else
                appStateRepository.channelFrequency.value
        return true
    }

    /**
     * Create an IQ Source instance for a specific device type and ID
     */
    private fun createSourceForType(sourceType: SourceType, deviceId: String): IQSourceInterface? {
        val newSource: IQSourceInterface = when (sourceType) {
            SourceType.FILESOURCE -> FileIQSource()
            SourceType.HACKRF -> {
                val hackrfSource = HackrfSource()
                hackrfSource.setFrequency(appStateRepository.sourceFrequency.value)
                hackrfSource.setSampleRate(appStateRepository.sourceSampleRate.value.toInt())
                hackrfSource.vgaRxGain = appStateRepository.hackrfVgaGainSteps[appStateRepository.hackrfVgaGainIndex.value]
                hackrfSource.lnaGain = appStateRepository.hackrfVgaGainSteps[appStateRepository.hackrfVgaGainIndex.value]
                hackrfSource.setAmplifier(appStateRepository.hackrfAmplifierEnabled.value)
                hackrfSource.setAntennaPower(appStateRepository.hackrfAntennaPowerEnabled.value)
                hackrfSource.frequencyOffset = appStateRepository.hackrfConverterOffset.value.toInt()
                hackrfSource
            }
            SourceType.RTLSDR -> {
                val rtlsdrSource = if(appStateRepository.rtlsdrExternalServerEnabled.value) {
                    RtlsdrSource(
                        appStateRepository.rtlsdrExternalServerIP.value,
                        appStateRepository.rtlsdrExternalServerPort.value
                    )
                } else {
                    RtlsdrSource("127.0.0.1", 1234)
                }
                rtlsdrSource.setAllowOutOfBoundFrequency(appStateRepository.rtlsdrAllowOutOfBoundFrequency.value || appStateRepository.rtlsdrBlogV4connected.value)
                rtlsdrSource.setFrequency(appStateRepository.sourceFrequency.value)
                rtlsdrSource.setSampleRate(appStateRepository.sourceSampleRate.value.toInt())
                rtlsdrSource.frequencyCorrection = appStateRepository.rtlsdrFrequencyCorrection.value
                rtlsdrSource.frequencyOffset = appStateRepository.rtlsdrConverterOffset.value.toInt()
                rtlsdrSource.isAutomaticGainControl = appStateRepository.rtlsdrAgcEnabled.value

                if (appStateRepository.rtlsdrManualGainEnabled.value) {
                    rtlsdrSource.isManualGain = true
                    rtlsdrSource.gain = appStateRepository.rtlsdrGainSteps.value[appStateRepository.rtlsdrGainIndex.value]
                    rtlsdrSource.ifGain = appStateRepository.rtlsdrIFGainSteps.value[appStateRepository.rtlsdrIFGainIndex.value]
                } else {
                    rtlsdrSource.isManualGain = false
                }
                rtlsdrSource
            }
            SourceType.AIRSPY -> {
                val index = deviceId.split("_").lastOrNull()?.toIntOrNull() ?: 0
                val airspySource = AirspySource(deviceIndex = index)
                airspySource.frequency = appStateRepository.sourceFrequency.value
                airspySource.sampleRate = appStateRepository.sourceSampleRate.value.toInt()
                airspySource.lnaGain = appStateRepository.airspyLnaGain.value
                airspySource.vgaGain = appStateRepository.airspyVgaGain.value
                airspySource.mixerGain = appStateRepository.airspyMixerGain.value
                airspySource.linearityGain = appStateRepository.airspyLinearityGain.value
                airspySource.sensitivityGain = appStateRepository.airspySensitivityGain.value
                airspySource.advancedGainEnabled = appStateRepository.airspyAdvancedGainEnabled.value
                airspySource.rfBias = appStateRepository.airspyRfBiasEnabled.value
                airspySource.frequencyOffset = appStateRepository.airspyConverterOffset.value.toInt()
                airspySource
            }
            SourceType.HYDRASDR -> {
                val hydraSdrSource = HydraSdrSource()
                hydraSdrSource.frequency = appStateRepository.sourceFrequency.value
                hydraSdrSource.sampleRate = appStateRepository.sourceSampleRate.value.toInt()
                hydraSdrSource.lnaGain = appStateRepository.hydraSdrLnaGain.value
                hydraSdrSource.vgaGain = appStateRepository.hydraSdrVgaGain.value
                hydraSdrSource.mixerGain = appStateRepository.hydraSdrMixerGain.value
                hydraSdrSource.linearityGain = appStateRepository.hydraSdrLinearityGain.value
                hydraSdrSource.sensitivityGain = appStateRepository.hydraSdrSensitivityGain.value
                hydraSdrSource.advancedGainEnabled = appStateRepository.hydraSdrAdvancedGainEnabled.value
                hydraSdrSource.rfBias = appStateRepository.hydraSdrRfBiasEnabled.value
                hydraSdrSource.rfPort = appStateRepository.hydraSdrRfPort.value
                hydraSdrSource.frequencyOffset = appStateRepository.hydraSdrConverterOffset.value.toInt()
                hydraSdrSource
            }
        }

        // Store in temporary map
        tempSources[deviceId] = newSource
        tempSources_types[deviceId] = sourceType
        return newSource
    }

    /**
     * Open the IQ Source for a specific device
     */
    private fun openSourceForDevice(source: IQSourceInterface, deviceId: String, sourceType: SourceType): Boolean {
        val callback = createIQSourceCallback(deviceId)

        return when (sourceType) {
            SourceType.FILESOURCE -> if (source is FileIQSource) {
                source.init(
                    appStateRepository.filesourceUri.value.toUri(),
                    this.contentResolver,
                    appStateRepository.sourceSampleRate.value.toInt(),
                    appStateRepository.sourceFrequency.value,
                    1024*256,
                    appStateRepository.filesourceRepeatEnabled.value,
                    when(appStateRepository.filesourceFileFormat.value) {
                        FilesourceFileFormat.HACKRF -> FileIQSource.FILE_FORMAT_8BIT_SIGNED
                        FilesourceFileFormat.RTLSDR -> FileIQSource.FILE_FORMAT_8BIT_UNSIGNED
                        FilesourceFileFormat.AIRSPY -> FileIQSource.FILE_FORMAT_16BIT_SIGNED
                        FilesourceFileFormat.HYDRASDR -> FileIQSource.FILE_FORMAT_16BIT_SIGNED
                    }
                )
                source.open(this, callback) == true
            } else {
                Log.e(TAG,"openSourceForDevice: sourceType is FILE_SOURCE, but source is of wrong type.")
                false
            }

            SourceType.HACKRF -> if (source is HackrfSource) {
                source.open(this, callback)
            } else {
                Log.e(TAG, "openSourceForDevice: sourceType is HACKRF_SOURCE, but source is of wrong type.")
                false
            }

            SourceType.RTLSDR -> if (source is RtlsdrSource) {
                source.open(this, callback) == true
            } else {
                Log.e(TAG, "openSourceForDevice: sourceType is RTLSDR_SOURCE, but source is of wrong type.")
                false
            }

            SourceType.AIRSPY -> if (source is AirspySource) {
                source.open(this, callback) == true
            } else {
                Log.e(TAG, "openSourceForDevice: sourceType is AIRSPY, but source is of wrong type.")
                false
            }

            SourceType.HYDRASDR -> if (source is HydraSdrSource) {
                source.open(this, callback) == true
            } else {
                Log.e(TAG, "openSourceForDevice: sourceType is HYDRASDR, but source is of wrong type.")
                false
            }
        }
    }

    // Legacy createSource() and openSource() methods removed - now use createSourceForType() and openSourceForDevice()

    /**
     * Start recording for a specific device
     */
    fun startRecordingForDevice(deviceId: String) {
        val pipeline = activePipelines[deviceId] ?: run {
            Log.w(TAG, "startRecordingForDevice: Device $deviceId not found")
            return
        }

        val recordingStartedTimestamp = System.currentTimeMillis()
        val filename = "${deviceId}_ongoing_recording.iq"

        // Check if user has selected custom directory via SAF
        val customDirUriStr = appStateRepository.recordingDirectoryUri.value

        val outputStreamAndRef: Pair<BufferedOutputStream, Any>? = if (customDirUriStr.isNotEmpty()) {
            // Use SAF to write to user-selected directory
            try {
                val treeUri = android.net.Uri.parse(customDirUriStr)
                val docTree = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri)

                if (docTree == null || !docTree.canWrite()) {
                    Log.e(TAG, "startRecording: Cannot write to selected directory")
                    appStateRepository.emitAnalyzerEvent(
                        AppStateRepository.AnalyzerEvent.SourceFailure(
                            "Cannot write to recording directory. Please re-select folder in Recording tab."
                        )
                    )
                    return
                }

                // Delete existing file if it exists
                val existingFile = docTree.findFile(filename)
                existingFile?.delete()

                // Create new file
                val docFile = docTree.createFile("application/octet-stream", filename)
                if (docFile == null) {
                    Log.e(TAG, "startRecording: Failed to create file in selected directory")
                    appStateRepository.emitAnalyzerEvent(
                        AppStateRepository.AnalyzerEvent.SourceFailure(
                            "Failed to create recording file. Please re-select folder in Recording tab."
                        )
                    )
                    return
                }

                val outputStream = contentResolver.openOutputStream(docFile.uri)
                if (outputStream == null) {
                    Log.e(TAG, "startRecording: Failed to open output stream for SAF file")
                    return
                }

                Log.i(TAG, "startRecording: Recording to custom directory: ${docFile.uri}")
                Pair(BufferedOutputStream(outputStream), docFile.uri)
            } catch (e: Exception) {
                Log.e(TAG, "startRecording: Error accessing custom directory: ${e.message}", e)
                appStateRepository.emitAnalyzerEvent(
                    AppStateRepository.AnalyzerEvent.SourceFailure(
                        "Error accessing recording directory: ${e.message}"
                    )
                )
                return
            }
        } else {
            // Use internal storage (existing behavior)
            val filepath = "$RECORDINGS_DIRECTORY/$filename"
            Log.i(TAG, "startRecording: Recording to internal storage: $filepath")
            val file = File(this.filesDir, filepath)
            Pair(BufferedOutputStream(FileOutputStream(file)), file)
        }

        if (outputStreamAndRef == null) {
            Log.e(TAG, "startRecording: Failed to create output stream")
            return
        }

        val (bufferedOutputStream, fileRef) = outputStreamAndRef

        var maxRecordingTimeMilliseconds: Long? = null
        var maxRecordingFileSizeBytes: Long? = null
        when(appStateRepository.recordingstopAfterUnit.value) {
            StopAfterUnit.NEVER -> Unit
            StopAfterUnit.MB -> maxRecordingFileSizeBytes = appStateRepository.recordingStopAfterThreshold.value*(1024L*1024L)
            StopAfterUnit.GB -> maxRecordingFileSizeBytes = appStateRepository.recordingStopAfterThreshold.value*(1024L*1024L*1024L)
            StopAfterUnit.SEC -> maxRecordingTimeMilliseconds = appStateRepository.recordingStopAfterThreshold.value*1000L
            StopAfterUnit.MIN -> maxRecordingTimeMilliseconds = appStateRepository.recordingStopAfterThreshold.value*1000L*60L
        }
        pipeline.scheduler.startRecording(
            bufferedOutputStream = bufferedOutputStream,
            onlyWhenSquelchIsSatisfied = appStateRepository.recordOnlyWhenSquelchIsSatisfied.value,
            maxRecordingTime = maxRecordingTimeMilliseconds,
            maxRecordingFileSize = maxRecordingFileSizeBytes,
            onRecordingStopped = { finalSize ->
                appStateRepository.emitAnalyzerEvent(AppStateRepository.AnalyzerEvent.RecordingFinished(finalSize, fileRef))
                // Check if all devices stopped recording
                if (activePipelines.values.none { it.scheduler.isRecording }) {
                    appStateRepository.recordingRunning.set(false)
                }
            },
            onFileSizeUpdate = appStateRepository.recordingCurrentFileSize::set
        )

        Log.i(TAG, "startRecordingForDevice: Started recording for device $deviceId")
    }

    /**
     * Start recording for all active devices
     */
    fun startRecording() {
        if (appStateRepository.recordingRunning.value) {
            Log.w(TAG, "startRecording: Recording is already running. do nothing..")
            return
        }

        if (activePipelines.isEmpty()) {
            Log.w(TAG, "startRecording: No active devices to record from")
            return
        }

        Log.i(TAG, "startRecording: Starting recording for ${activePipelines.size} devices")

        // Start recording for all active devices
        activePipelines.keys.forEach { deviceId ->
            startRecordingForDevice(deviceId)
        }

        // update ui
        appStateRepository.recordingStartedTimestamp.set(System.currentTimeMillis())
        appStateRepository.recordingRunning.set(true)
    }

    /**
     * Stop recording for a specific device
     */
    fun stopRecordingForDevice(deviceId: String) {
        val pipeline = activePipelines[deviceId] ?: run {
            Log.w(TAG, "stopRecordingForDevice: Device $deviceId not found")
            return
        }

        pipeline.scheduler.stopRecording()
        Log.i(TAG, "stopRecordingForDevice: Stopped recording for device $deviceId")
    }

    /**
     * Stop recording for all devices
     */
    fun stopRecording() {
        Log.i(TAG, "stopRecording: Stopping recording for all devices")
        activePipelines.keys.forEach { deviceId ->
            stopRecordingForDevice(deviceId)
        }
        appStateRepository.recordingRunning.set(false)
    }

    private fun handleAppStateChanges() {
        val s = serviceScope
        val asr = appStateRepository

        // source tab
        s.collectAppState(asr.sourceFrequency) { source?.frequency = it }
        s.collectAppState(asr.sourceSampleRate) { source?.sampleRate = it.toInt() }
        s.collectAppState(asr.hackrfVgaGainIndex) { (source as? HackrfSource)?.vgaRxGain = asr.hackrfVgaGainSteps[it] }
        s.collectAppState(asr.hackrfLnaGainIndex) { (source as? HackrfSource)?.lnaGain = asr.hackrfLnaGainSteps[it] }
        s.collectAppState(asr.hackrfAmplifierEnabled) { (source as? HackrfSource)?.setAmplifier(it) }
        s.collectAppState(asr.hackrfAntennaPowerEnabled) { (source as? HackrfSource)?.setAntennaPower(it) }
        s.collectAppState(asr.hackrfConverterOffset) { (source as? HackrfSource)?.frequencyOffset = it.toInt() }
        s.collectAppState(asr.rtlsdrGainIndex) { (source as? RtlsdrSource)?.gain = asr.rtlsdrGainSteps.value[it] }
        s.collectAppState(asr.rtlsdrIFGainIndex) { (source as? RtlsdrSource)?.ifGain = asr.rtlsdrIFGainSteps.value[it] }
        s.collectAppState(asr.rtlsdrAgcEnabled) { (source as? RtlsdrSource)?.isAutomaticGainControl = it }
        s.collectAppState(asr.rtlsdrManualGainEnabled) {
            (source as? RtlsdrSource)?.isManualGain = it
            if (it) {
                (source as? RtlsdrSource)?.apply {
                    gain = asr.rtlsdrGainSteps.value[asr.rtlsdrGainIndex.value]
                    ifGain = asr.rtlsdrIFGainSteps.value[asr.rtlsdrIFGainIndex.value]
                }
            }
        }
        s.collectAppState(asr.rtlsdrConverterOffset) { (source as? RtlsdrSource)?.frequencyOffset = it.toInt() }
        s.collectAppState(asr.rtlsdrFrequencyCorrection) { (source as? RtlsdrSource)?.frequencyCorrection = it }
        s.collectAppState(asr.airspyAdvancedGainEnabled) { (source as? AirspySource)?.advancedGainEnabled = it }
        s.collectAppState(asr.airspyVgaGain) { (source as? AirspySource)?.vgaGain = it }
        s.collectAppState(asr.airspyLnaGain) { (source as? AirspySource)?.lnaGain = it }
        s.collectAppState(asr.airspyMixerGain) { (source as? AirspySource)?.mixerGain = it }
        s.collectAppState(asr.airspyLinearityGain) { (source as? AirspySource)?.linearityGain = it }
        s.collectAppState(asr.airspySensitivityGain) { (source as? AirspySource)?.sensitivityGain = it }
        s.collectAppState(asr.airspyRfBiasEnabled) { (source as? AirspySource)?.rfBias = it }
        s.collectAppState(asr.airspyConverterOffset) { (source as? AirspySource)?.frequencyOffset = it.toInt() }
        s.collectAppState(asr.hydraSdrAdvancedGainEnabled) { (source as? HydraSdrSource)?.advancedGainEnabled = it }
        s.collectAppState(asr.hydraSdrVgaGain) { (source as? HydraSdrSource)?.vgaGain = it }
        s.collectAppState(asr.hydraSdrLnaGain) { (source as? HydraSdrSource)?.lnaGain = it }
        s.collectAppState(asr.hydraSdrMixerGain) { (source as? HydraSdrSource)?.mixerGain = it }
        s.collectAppState(asr.hydraSdrLinearityGain) { (source as? HydraSdrSource)?.linearityGain = it }
        s.collectAppState(asr.hydraSdrSensitivityGain) { (source as? HydraSdrSource)?.sensitivityGain = it }
        s.collectAppState(asr.hydraSdrRfBiasEnabled) { (source as? HydraSdrSource)?.rfBias = it }
        s.collectAppState(asr.hydraSdrRfPort) { (source as? HydraSdrSource)?.rfPort = it }
        s.collectAppState(asr.hydraSdrConverterOffset) { (source as? HydraSdrSource)?.frequencyOffset = it.toInt() }
        s.collectAppState(asr.filesourceFileFormat) { (source as? FileIQSource)?.fileFormat = it.ordinal }
        s.collectAppState(asr.filesourceRepeatEnabled) { (source as? FileIQSource)?.isRepeat = it }

        // view tab
        s.collectAppState(asr.fftSize) { scheduler?.fftSize = it }
        s.collectAppState(asr.waterfallSpeed) { fftProcessor?.waterfallSpeed = it }
        s.collectAppState(asr.fftPeakHold) { fftProcessor?.fftPeakHold = it }

        // demodulation tab
        s.collectAppState(asr.demodulationMode) { applyNewDemodulationMode(it) }
        s.collectAppState(asr.channelFrequency) { scheduler?.channelFrequency = if(asr.demodulationMode.value == DemodulationMode.CW) it - Demodulator.CW_OFFSET_FREQUENCY else it }
        s.collectAppState(asr.channelWidth) { demodulator?.channelWidth = it }
        s.collectAppState(asr.squelchSatisfied) { scheduler?.squelchSatisfied = it }
        s.collectAppState(asr.effectiveAudioVolumeLevel) { demodulator?.audioVolumeLevel = it }

        // settings tab
        s.collectAppState(asr.loggingEnabled) {
            if (it)
                LogcatLogger.startLogging(this@AnalyzerService)
            else if (LogcatLogger.isLogging)
                LogcatLogger.stopLogging()
        }
        s.collectAppState(asr.rtlsdrAllowOutOfBoundFrequency) {
            (source as? RtlsdrSource)?.isAllowOutOfBoundFrequency = it || appStateRepository.rtlsdrBlogV4connected.value
            appStateRepository.sourceMinimumFrequency.set(source?.minFrequency ?: 0)
            appStateRepository.sourceMaximumFrequency.set(source?.maxFrequency ?: 0)
        }
        s.collectAppState(asr.rtlsdrBlogV4connected) {
            (source as? RtlsdrSource)?.isAllowOutOfBoundFrequency = it || appStateRepository.rtlsdrAllowOutOfBoundFrequency.value
            appStateRepository.sourceMinimumFrequency.set(source?.minFrequency ?: 0)
            appStateRepository.sourceMaximumFrequency.set(source?.maxFrequency ?: 0)
        }
    }
}
