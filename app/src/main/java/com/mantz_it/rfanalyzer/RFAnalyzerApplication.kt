package com.mantz_it.rfanalyzer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

/**
 * <h1>RF Analyzer - Application Class</h1>
 *
 * Module:      RFAnalyzerApplication.kt
 * Description: The application class of the app (uses Hilt for dependency injection).
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


@HiltAndroidApp
class RFAnalyzerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        // Channel for foreground service (low priority, no vibration)
        val serviceChannel = NotificationChannel(
            "SERVICE_CHANNEL",
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(serviceChannel)

        // Channel for recording events (high priority, enables vibration for smartwatch)
        val recordingChannel = NotificationChannel(
            "RECORDING_CHANNEL",
            "Recording Events",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for recording start/stop events"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)  // Vibrate: 250ms on, 250ms off, 250ms on
        }
        manager.createNotificationChannel(recordingChannel)
    }
}
