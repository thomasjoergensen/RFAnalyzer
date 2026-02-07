# IEM (In-Ear Monitor) Frequency Plans

This document contains frequency plans for popular wireless IEM systems to be included in RF Analyzer.

## Signal Characteristics

- **Bandwidth**: ~200 kHz per channel (stereo FM modulation)
- **Channel Spacing**: 300-600 kHz between channels (to avoid interference)
- **Typical Signal Strength**: -30 to -60 dBm (strong signals at venues)
- **Detection**: Use PEAK_ONLY mode with narrow bandwidth analysis

## Implementation Status

✅ **Database schema created** (version 4)
✅ **Auto-population on first launch** via RFAnalyzerApplication
✅ **10 presets with 204 total channels**

## Systems Implemented (MVP)

### 1. Shure PSM1000 (Most Popular Professional System)

**Band G7 (US)** - 506-530 MHz
- Group 1: 20 channels from 518.000 to 541.975 MHz
- Channel spacing: ~1.2 MHz

**Band G10 (US)** - 470-542 MHz
- Group 1: 20 channels

**Band K12 (US)** - 606-670 MHz
- Group 1: 20 channels

### 2. Shure PSM300 (Budget-Friendly)

**Band G20** - 488-512 MHz
- 12 channels from 488.150 to 505.850 MHz
- Channel spacing: ~1.5 MHz

### 3. Sennheiser EW IEM G4

**Band A (516-558 MHz)**
- Bank 1: 12 channels
- Bank 2: 12 channels

**Band G (566-608 MHz)** - Popular in EU
- Bank 1: 12 channels
- Bank 2: 12 channels

**Band B (626-668 MHz)**
- Bank 1: 12 channels
- Bank 2: 12 channels

### 4. Audio-Technica M3

**Band D (655.500-680.375 MHz)**
- Group 1: 8 channels
- Channel spacing: 3.125 MHz

### 5. Wisycom MPR52 (High-End Professional)

**Band 470-516 MHz (US TV Band)**
- Group 1: 16 channels
- Channel spacing: 600 kHz

**Band 520-608 MHz (International)**
- Group 1: 16 channels
- Channel spacing: 600 kHz

**Band 614-698 MHz (US 600 MHz Band)**
- Group 1: 16 channels
- Channel spacing: 600 kHz

## Data Structure

```
Manufacturer
  └─ System (Model + Band)
      └─ Group/Bank
          └─ Channels (with frequencies)
```

## Scan Optimization for IEM

1. **Known frequencies**: Jump directly to preset frequencies (no continuous sweep)
2. **Narrow analysis**: Use ±100 kHz window around each frequency
3. **Batch scanning**: Group frequencies within same FFT bandwidth
4. **Fast dwell time**: 100ms sufficient for strong IEM signals

## Sources

- Shure Wireless Frequency Guide: https://www.shure.com/frequency-guide
- Sennheiser Frequency Allocation: https://www.sennheiser.com
- Audio-Technica Frequency Charts
- Professional audio forum recommendations
