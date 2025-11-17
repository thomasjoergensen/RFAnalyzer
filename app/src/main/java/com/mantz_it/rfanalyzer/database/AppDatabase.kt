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
    entities = [Recording::class, ScanResult::class, DiscoveredSignal::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun scanDao(): ScanDao
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

