package com.mantz_it.rfanalyzer.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * <h1>RF Analyzer - Scan DAO</h1>
 *
 * Module:      ScanDao.kt
 * Description: Data Access Object for the Scan Results in the room database.
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

@Entity(tableName = "scan_results")
data class ScanResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val startFrequency: Long,
    val endFrequency: Long,
    val threshold: Float,
    val stepSize: Long,
    val dwellTime: Long
)

@Entity(
    tableName = "discovered_signals",
    foreignKeys = [
        ForeignKey(
            entity = ScanResult::class,
            parentColumns = ["id"],
            childColumns = ["scanResultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scanResultId")]
)
data class DiscoveredSignal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanResultId: Long,
    val frequency: Long,
    val peakStrength: Float,
    val averageStrength: Float,
    val bandwidth: Long = 0, // Bandwidth in Hz, 0 means single frequency detection
    val isGrouped: Boolean = false // True if this is a grouped signal from multiple detections
)

data class ScanResultWithSignals(
    val scanResult: ScanResult,
    val signals: List<DiscoveredSignal>
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_results ORDER BY timestamp DESC")
    fun getAllScanResults(): Flow<List<ScanResult>>

    @Query("SELECT * FROM scan_results ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentScanResults(limit: Int = 10): List<ScanResult>

    @Query("SELECT * FROM discovered_signals WHERE scanResultId = :scanResultId ORDER BY peakStrength DESC")
    suspend fun getSignalsForScan(scanResultId: Long): List<DiscoveredSignal>

    @Transaction
    suspend fun getScanResultWithSignals(scanResultId: Long): ScanResultWithSignals? {
        val scanResult = getScanResult(scanResultId) ?: return null
        val signals = getSignalsForScan(scanResultId)
        return ScanResultWithSignals(scanResult, signals)
    }

    @Query("SELECT * FROM scan_results WHERE id = :id")
    suspend fun getScanResult(id: Long): ScanResult?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanResult(scanResult: ScanResult): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscoveredSignal(signal: DiscoveredSignal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscoveredSignals(signals: List<DiscoveredSignal>)

    @Transaction
    suspend fun saveScanWithSignals(scanResult: ScanResult, signals: List<DiscoveredSignal>): Long {
        val scanId = insertScanResult(scanResult)
        val signalsWithScanId = signals.map { it.copy(scanResultId = scanId) }
        insertDiscoveredSignals(signalsWithScanId)
        return scanId
    }

    @Query("DELETE FROM scan_results WHERE id = :id")
    suspend fun deleteScanResult(id: Long)

    @Query("DELETE FROM scan_results")
    suspend fun deleteAllScanResults()

    @Query("DELETE FROM discovered_signals WHERE id = :id")
    suspend fun deleteDiscoveredSignal(id: Long)

    @Delete
    suspend fun delete(scanResult: ScanResult)
}
