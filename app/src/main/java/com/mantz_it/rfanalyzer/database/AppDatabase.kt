package com.mantz_it.rfanalyzer.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * <h1>RF Analyzer - Database</h1>
 *
 * Module:      AppDatabase.kt
 * Description: The room-based database.
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


@Database(
    entities = [
        Recording::class,
        ScanResult::class,
        DiscoveredSignal::class,
        IEMPreset::class,
        IEMChannel::class,
        IEMScanResult::class,
        IEMDetectedChannel::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun scanDao(): ScanDao
    abstract fun iemDao(): IEMDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create scan_results table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scan_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                startFrequency INTEGER NOT NULL,
                endFrequency INTEGER NOT NULL,
                threshold REAL NOT NULL,
                stepSize INTEGER NOT NULL,
                dwellTime INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Create discovered_signals table (without bandwidth and isGrouped)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS discovered_signals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scanResultId INTEGER NOT NULL,
                frequency INTEGER NOT NULL,
                peakStrength REAL NOT NULL,
                averageStrength REAL NOT NULL,
                FOREIGN KEY(scanResultId) REFERENCES scan_results(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Create index on scanResultId for faster queries
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_discovered_signals_scanResultId ON discovered_signals(scanResultId)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add bandwidth and isGrouped columns to discovered_signals table
        db.execSQL("ALTER TABLE discovered_signals ADD COLUMN bandwidth INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE discovered_signals ADD COLUMN isGrouped INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create IEM presets table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS iem_presets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                manufacturer TEXT NOT NULL,
                model TEXT NOT NULL,
                band TEXT NOT NULL,
                region TEXT NOT NULL,
                frequencyRange TEXT NOT NULL,
                channelCount INTEGER NOT NULL,
                channelBandwidth INTEGER NOT NULL,
                channelSpacing INTEGER NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        // Create IEM channels table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS iem_channels (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                presetId INTEGER NOT NULL,
                groupName TEXT NOT NULL,
                channelNumber INTEGER NOT NULL,
                frequency INTEGER NOT NULL,
                channelName TEXT,
                FOREIGN KEY(presetId) REFERENCES iem_presets(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_iem_channels_presetId ON iem_channels(presetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_iem_channels_frequency ON iem_channels(frequency)")

        // Create IEM scan results table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS iem_scan_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                scanDuration INTEGER NOT NULL,
                scannedPresetIds TEXT NOT NULL,
                detectedCount INTEGER NOT NULL,
                averageSignalStrength REAL NOT NULL
            )
            """.trimIndent()
        )

        // Create IEM detected channels table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS iem_detected_channels (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scanResultId INTEGER NOT NULL,
                channelId INTEGER NOT NULL,
                peakStrength REAL NOT NULL,
                averageStrength REAL NOT NULL,
                detectionConfidence REAL NOT NULL DEFAULT 1.0,
                FOREIGN KEY(scanResultId) REFERENCES iem_scan_results(id) ON DELETE CASCADE,
                FOREIGN KEY(channelId) REFERENCES iem_channels(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_iem_detected_channels_scanResultId ON iem_detected_channels(scanResultId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_iem_detected_channels_channelId ON iem_detected_channels(channelId)")
    }
}

