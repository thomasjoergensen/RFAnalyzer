package com.mantz_it.rfanalyzer.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * <h1>RF Analyzer - IEM Database Entities and DAO</h1>
 *
 * Module:      IEMDao.kt
 * Description: Database entities and data access object for IEM (In-Ear Monitor) preset scanning
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
 * Represents a wireless IEM system preset (e.g., Shure PSM1000 G7)
 */
@Entity(tableName = "iem_presets")
data class IEMPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val manufacturer: String,           // "Shure", "Sennheiser", "Wisycom", "Audio-Technica"
    val model: String,                  // "PSM1000", "PSM300", "EW IEM G4", "M3"
    val band: String,                   // "G7", "G10", "Band A", "Band G"
    val region: String,                 // "US", "EU", "UK", "International"
    val frequencyRange: String,         // "506-530 MHz" (for display)
    val channelCount: Int,              // Total number of channels
    val channelBandwidth: Int,          // Typical channel bandwidth in kHz (usually 200)
    val channelSpacing: Int,            // Typical spacing between channels in kHz
    val enabled: Boolean = true         // User can disable presets they don't use
)

/**
 * Represents an individual channel within an IEM preset
 */
@Entity(
    tableName = "iem_channels",
    foreignKeys = [
        ForeignKey(
            entity = IEMPreset::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("presetId"), Index("frequency")]
)
data class IEMChannel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,                 // Reference to IEMPreset
    val groupName: String,              // "Group 1", "Bank A", etc.
    val channelNumber: Int,             // 1-20 typically
    val frequency: Long,                // Frequency in Hz (e.g., 518000000 for 518.000 MHz)
    val channelName: String? = null     // Optional custom name (e.g., "Lead Vocals")
)

/**
 * Represents the results of an IEM preset scan
 */
@Entity(tableName = "iem_scan_results")
data class IEMScanResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,                // When the scan was performed
    val scanDuration: Long,             // How long the scan took (ms)
    val scannedPresetIds: String,       // Comma-separated list of preset IDs that were scanned
    val detectedCount: Int,             // Number of active channels detected
    val averageSignalStrength: Float    // Average signal strength of detected channels
)

/**
 * Represents a detected active channel from an IEM scan
 */
@Entity(
    tableName = "iem_detected_channels",
    foreignKeys = [
        ForeignKey(
            entity = IEMScanResult::class,
            parentColumns = ["id"],
            childColumns = ["scanResultId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IEMChannel::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scanResultId"), Index("channelId")]
)
data class IEMDetectedChannel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanResultId: Long,             // Reference to IEMScanResult
    val channelId: Long,                // Reference to IEMChannel
    val peakStrength: Float,            // Peak signal strength in dB
    val averageStrength: Float,         // Average signal strength in dB
    val detectionConfidence: Float = 1.0f // 0.0-1.0, future use for uncertain detections
)

/**
 * Data class combining preset, channel, and detection information for display
 */
data class IEMDetectedChannelInfo(
    @Embedded(prefix = "detected_") val detectedChannel: IEMDetectedChannel,
    @Embedded(prefix = "channel_") val channel: IEMChannel,
    @Embedded(prefix = "preset_") val preset: IEMPreset
)

@Dao
interface IEMDao {
    // ===== IEM Presets =====

    @Query("SELECT * FROM iem_presets WHERE enabled = 1 ORDER BY manufacturer, model, band")
    fun getAllEnabledPresets(): Flow<List<IEMPreset>>

    @Query("SELECT * FROM iem_presets ORDER BY manufacturer, model, band")
    fun getAllPresets(): Flow<List<IEMPreset>>

    @Query("SELECT * FROM iem_presets WHERE id = :id")
    suspend fun getPreset(id: Long): IEMPreset?

    @Query("SELECT * FROM iem_presets WHERE id IN (:ids)")
    suspend fun getPresets(ids: List<Long>): List<IEMPreset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: IEMPreset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<IEMPreset>)

    @Update
    suspend fun updatePreset(preset: IEMPreset)

    @Delete
    suspend fun deletePreset(preset: IEMPreset)

    @Query("UPDATE iem_presets SET enabled = :enabled WHERE id = :id")
    suspend fun setPresetEnabled(id: Long, enabled: Boolean)

    // ===== IEM Channels =====

    @Query("SELECT * FROM iem_channels WHERE presetId = :presetId ORDER BY groupName, channelNumber")
    suspend fun getChannelsForPreset(presetId: Long): List<IEMChannel>

    @Query("SELECT * FROM iem_channels WHERE presetId IN (:presetIds) ORDER BY frequency")
    suspend fun getChannelsForPresets(presetIds: List<Long>): List<IEMChannel>

    @Query("SELECT * FROM iem_channels WHERE frequency = :frequency LIMIT 1")
    suspend fun getChannelByFrequency(frequency: Long): IEMChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: IEMChannel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<IEMChannel>)

    @Update
    suspend fun updateChannel(channel: IEMChannel)

    @Delete
    suspend fun deleteChannel(channel: IEMChannel)

    // ===== IEM Scan Results =====

    @Query("SELECT * FROM iem_scan_results ORDER BY timestamp DESC LIMIT 50")
    fun getRecentScanResults(): Flow<List<IEMScanResult>>

    @Query("SELECT * FROM iem_scan_results WHERE id = :id")
    suspend fun getScanResult(id: Long): IEMScanResult?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanResult(scanResult: IEMScanResult): Long

    @Delete
    suspend fun deleteScanResult(scanResult: IEMScanResult)

    @Query("DELETE FROM iem_scan_results")
    suspend fun deleteAllScanResults()

    // ===== IEM Detected Channels =====

    @Query("SELECT * FROM iem_detected_channels WHERE scanResultId = :scanResultId")
    suspend fun getDetectedChannelsForScan(scanResultId: Long): List<IEMDetectedChannel>

    @Query("""
        SELECT
            dc.id AS detected_id,
            dc.scanResultId AS detected_scanResultId,
            dc.channelId AS detected_channelId,
            dc.peakStrength AS detected_peakStrength,
            dc.averageStrength AS detected_averageStrength,
            dc.detectionConfidence AS detected_detectionConfidence,
            c.id AS channel_id,
            c.presetId AS channel_presetId,
            c.groupName AS channel_groupName,
            c.channelNumber AS channel_channelNumber,
            c.frequency AS channel_frequency,
            c.channelName AS channel_channelName,
            p.id AS preset_id,
            p.manufacturer AS preset_manufacturer,
            p.model AS preset_model,
            p.band AS preset_band,
            p.region AS preset_region,
            p.frequencyRange AS preset_frequencyRange,
            p.channelCount AS preset_channelCount,
            p.channelBandwidth AS preset_channelBandwidth,
            p.channelSpacing AS preset_channelSpacing,
            p.enabled AS preset_enabled
        FROM iem_detected_channels dc
        INNER JOIN iem_channels c ON dc.channelId = c.id
        INNER JOIN iem_presets p ON c.presetId = p.id
        WHERE dc.scanResultId = :scanResultId
        ORDER BY dc.peakStrength DESC
    """)
    suspend fun getDetectedChannelInfoForScan(scanResultId: Long): List<IEMDetectedChannelInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedChannel(detectedChannel: IEMDetectedChannel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedChannels(detectedChannels: List<IEMDetectedChannel>)

    @Delete
    suspend fun deleteDetectedChannel(detectedChannel: IEMDetectedChannel)

    @Query("DELETE FROM iem_detected_channels WHERE scanResultId = :scanResultId")
    suspend fun deleteDetectedChannelsForScan(scanResultId: Long)

    // ===== Convenience Methods =====

    @Transaction
    suspend fun saveScanWithDetections(
        scanResult: IEMScanResult,
        detectedChannels: List<IEMDetectedChannel>
    ): Long {
        val scanId = insertScanResult(scanResult)
        val channelsWithScanId = detectedChannels.map { it.copy(scanResultId = scanId) }
        insertDetectedChannels(channelsWithScanId)
        return scanId
    }

    @Query("""
        SELECT DISTINCT p.manufacturer
        FROM iem_presets p
        WHERE p.enabled = 1
        ORDER BY p.manufacturer
    """)
    suspend fun getEnabledManufacturers(): List<String>

    @Query("""
        SELECT * FROM iem_presets
        WHERE enabled = 1 AND manufacturer = :manufacturer
        ORDER BY model, band
    """)
    suspend fun getPresetsForManufacturer(manufacturer: String): List<IEMPreset>
}
