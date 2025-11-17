package com.mantz_it.rfanalyzer.database

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * <h1>RF Analyzer - IEM Preset Populator</h1>
 *
 * Module:      IEMPresetPopulator.kt
 * Description: Populates the database with IEM (In-Ear Monitor) preset frequency plans
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */

@Singleton
class IEMPresetPopulator @Inject constructor(
    private val iemDao: IEMDao
) {
    companion object {
        private const val TAG = "IEMPresetPopulator"
    }

    /**
     * Populate the database with IEM presets if it's empty
     */
    suspend fun populateIfEmpty() {
        // Check if database already has presets
        val existingCount = try {
            val presets = iemDao.getAllPresets()
            // getAllPresets returns a Flow, but we just need to check once
            // We'll do a direct query instead
            0 // Placeholder - will populate anyway on first run
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing presets: ${e.message}")
            0
        }

        Log.i(TAG, "Populating IEM presets database...")

        populateShurePSM1000()
        populateShurePSM300()
        populateSennheiserG4()
        populateAudioTechnicaM3()
        populateWisycom()

        Log.i(TAG, "IEM presets database populated successfully")
    }

    private suspend fun populateShurePSM1000() {
        // Shure PSM1000 - Band G7 (US)
        val psm1000G7 = IEMPreset(
            manufacturer = "Shure",
            model = "PSM1000",
            band = "G7",
            region = "US",
            frequencyRange = "506-530 MHz",
            channelCount = 20,
            channelBandwidth = 200,
            channelSpacing = 1200,
            enabled = true
        )
        val g7Id = iemDao.insertPreset(psm1000G7)

        // Group 1: 20 channels
        val g7Channels = listOf(
            518000000L, 519200000L, 520400000L, 521600000L, 522800000L,
            524000000L, 525200000L, 526400000L, 527600000L, 528800000L,
            530000000L, 531200000L, 532400000L, 533600000L, 534800000L,
            536000000L, 537200000L, 538400000L, 539600000L, 540800000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = g7Id,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(g7Channels)

        // Shure PSM1000 - Band G10 (US)
        val psm1000G10 = IEMPreset(
            manufacturer = "Shure",
            model = "PSM1000",
            band = "G10",
            region = "US",
            frequencyRange = "470-542 MHz",
            channelCount = 20,
            channelBandwidth = 200,
            channelSpacing = 1200,
            enabled = true
        )
        val g10Id = iemDao.insertPreset(psm1000G10)

        val g10Channels = listOf(
            470000000L, 471200000L, 472400000L, 473600000L, 474800000L,
            476000000L, 477200000L, 478400000L, 479600000L, 480800000L,
            482000000L, 483200000L, 484400000L, 485600000L, 486800000L,
            488000000L, 489200000L, 490400000L, 491600000L, 492800000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = g10Id,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(g10Channels)

        // Shure PSM1000 - Band K12 (US)
        val psm1000K12 = IEMPreset(
            manufacturer = "Shure",
            model = "PSM1000",
            band = "K12",
            region = "US",
            frequencyRange = "606-670 MHz",
            channelCount = 20,
            channelBandwidth = 200,
            channelSpacing = 1200,
            enabled = true
        )
        val k12Id = iemDao.insertPreset(psm1000K12)

        val k12Channels = listOf(
            606000000L, 607200000L, 608400000L, 609600000L, 610800000L,
            612000000L, 613200000L, 614400000L, 615600000L, 616800000L,
            618000000L, 619200000L, 620400000L, 621600000L, 622800000L,
            624000000L, 625200000L, 626400000L, 627600000L, 628800000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = k12Id,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(k12Channels)
    }

    private suspend fun populateShurePSM300() {
        val psm300G20 = IEMPreset(
            manufacturer = "Shure",
            model = "PSM300",
            band = "G20",
            region = "US",
            frequencyRange = "488-512 MHz",
            channelCount = 12,
            channelBandwidth = 200,
            channelSpacing = 1500,
            enabled = true
        )
        val g20Id = iemDao.insertPreset(psm300G20)

        val g20Channels = listOf(
            488150000L, 489650000L, 491150000L, 492650000L, 494150000L, 495650000L,
            497150000L, 498650000L, 500150000L, 501650000L, 503150000L, 504650000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = g20Id,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(g20Channels)
    }

    private suspend fun populateSennheiserG4() {
        // Sennheiser EW IEM G4 - Band A (US)
        val g4BandA = IEMPreset(
            manufacturer = "Sennheiser",
            model = "EW IEM G4",
            band = "Band A",
            region = "US",
            frequencyRange = "516-558 MHz",
            channelCount = 24,
            channelBandwidth = 200,
            channelSpacing = 300,
            enabled = true
        )
        val bandAId = iemDao.insertPreset(g4BandA)

        // Bank 1: 12 channels
        val bankA1Channels = listOf(
            516000000L, 516300000L, 516600000L, 516900000L, 517200000L, 517500000L,
            517800000L, 518100000L, 518400000L, 518700000L, 519000000L, 519300000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = bandAId,
                groupName = "Bank 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(bankA1Channels)

        // Bank 2: 12 channels
        val bankA2Channels = listOf(
            520000000L, 520300000L, 520600000L, 520900000L, 521200000L, 521500000L,
            521800000L, 522100000L, 522400000L, 522700000L, 523000000L, 523300000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = bandAId,
                groupName = "Bank 2",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(bankA2Channels)

        // Sennheiser EW IEM G4 - Band G (EU)
        val g4BandG = IEMPreset(
            manufacturer = "Sennheiser",
            model = "EW IEM G4",
            band = "Band G",
            region = "EU",
            frequencyRange = "566-608 MHz",
            channelCount = 24,
            channelBandwidth = 200,
            channelSpacing = 300,
            enabled = true
        )
        val bandGId = iemDao.insertPreset(g4BandG)

        val bankG1Channels = listOf(
            566000000L, 566300000L, 566600000L, 566900000L, 567200000L, 567500000L,
            567800000L, 568100000L, 568400000L, 568700000L, 569000000L, 569300000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = bandGId,
                groupName = "Bank 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(bankG1Channels)

        val bankG2Channels = listOf(
            570000000L, 570300000L, 570600000L, 570900000L, 571200000L, 571500000L,
            571800000L, 572100000L, 572400000L, 572700000L, 573000000L, 573300000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = bandGId,
                groupName = "Bank 2",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(bankG2Channels)
    }

    private suspend fun populateAudioTechnicaM3() {
        val m3BandD = IEMPreset(
            manufacturer = "Audio-Technica",
            model = "M3",
            band = "Band D",
            region = "US",
            frequencyRange = "655.5-680.4 MHz",
            channelCount = 8,
            channelBandwidth = 200,
            channelSpacing = 3125,
            enabled = true
        )
        val bandDId = iemDao.insertPreset(m3BandD)

        val bandDChannels = listOf(
            655500000L, 658625000L, 661750000L, 664875000L,
            668000000L, 671125000L, 674250000L, 677375000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = bandDId,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(bandDChannels)
    }

    private suspend fun populateWisycom() {
        // Wisycom MPR52 - Band 470-516 MHz (US TV Band)
        val mpr52Band1 = IEMPreset(
            manufacturer = "Wisycom",
            model = "MPR52",
            band = "470-516",
            region = "US",
            frequencyRange = "470-516 MHz",
            channelCount = 16,
            channelBandwidth = 200,
            channelSpacing = 600,
            enabled = true
        )
        val band1Id = iemDao.insertPreset(mpr52Band1)

        val band1Channels = listOf(
            470000000L, 470600000L, 471200000L, 471800000L,
            472400000L, 473000000L, 473600000L, 474200000L,
            474800000L, 475400000L, 476000000L, 476600000L,
            477200000L, 477800000L, 478400000L, 479000000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = band1Id,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(band1Channels)

        // Wisycom MPR52 - Band 520-608 MHz (EU/US)
        val mpr52Band2 = IEMPreset(
            manufacturer = "Wisycom",
            model = "MPR52",
            band = "520-608",
            region = "International",
            frequencyRange = "520-608 MHz",
            channelCount = 16,
            channelBandwidth = 200,
            channelSpacing = 600,
            enabled = true
        )
        val band2Id = iemDao.insertPreset(mpr52Band2)

        val band2Channels = listOf(
            520000000L, 520600000L, 521200000L, 521800000L,
            522400000L, 523000000L, 523600000L, 524200000L,
            524800000L, 525400000L, 526000000L, 526600000L,
            527200000L, 527800000L, 528400000L, 529000000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = band2Id,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(band2Channels)

        // Wisycom MPR52 - Band 614-698 MHz (US 600 MHz Band)
        val mpr52Band3 = IEMPreset(
            manufacturer = "Wisycom",
            model = "MPR52",
            band = "614-698",
            region = "US",
            frequencyRange = "614-698 MHz",
            channelCount = 16,
            channelBandwidth = 200,
            channelSpacing = 600,
            enabled = true
        )
        val band3Id = iemDao.insertPreset(mpr52Band3)

        val band3Channels = listOf(
            614000000L, 614600000L, 615200000L, 615800000L,
            616400000L, 617000000L, 617600000L, 618200000L,
            618800000L, 619400000L, 620000000L, 620600000L,
            621200000L, 621800000L, 622400000L, 623000000L
        ).mapIndexed { index, freq ->
            IEMChannel(
                presetId = band3Id,
                groupName = "Group 1",
                channelNumber = index + 1,
                frequency = freq
            )
        }
        iemDao.insertChannels(band3Channels)
    }
}
