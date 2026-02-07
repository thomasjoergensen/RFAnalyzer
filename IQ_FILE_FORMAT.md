# RF Analyzer IQ File Format Documentation

## Overview

RF Analyzer records raw IQ samples directly from SDR hardware with **no file header or metadata in the file itself**. All metadata (frequency, sample rate, format type) is stored separately in the app's Room database.

## File Structure

```
[Raw IQ Samples Only - No Header]
├─ Sample 0: I, Q
├─ Sample 1: I, Q
├─ Sample 2: I, Q
└─ ... continues to end of file
```

**Important**: Files contain only raw interleaved IQ sample data. There is no file header, no metadata section, and no framing bytes.

## Supported Sample Formats

RF Analyzer supports 4 different sample formats based on the SDR hardware type:

### 1. HACKRF Format (8-bit Signed)

- **Format ID**: `HACKRF`
- **Bytes per sample**: 2 (1 byte I + 1 byte Q)
- **Data type**: Signed 8-bit integers
- **Byte layout**: `I₀, Q₀, I₁, Q₁, I₂, Q₂, ...`
- **Value range**: -128 to 127
- **Normalization formula**: `(byte - 128) / 128.0` → [-1.0, 1.0)

**Memory layout example**:
```
Byte:    [0]  [1]  [2]  [3]  [4]  [5]
Sample:   I₀   Q₀   I₁   Q₁   I₂   Q₂
Type:   int8 int8 int8 int8 int8 int8
```

### 2. RTLSDR Format (8-bit Unsigned)

- **Format ID**: `RTLSDR`
- **Bytes per sample**: 2 (1 byte I + 1 byte Q)
- **Data type**: Unsigned 8-bit integers
- **Byte layout**: `I₀, Q₀, I₁, Q₁, I₂, Q₂, ...`
- **Value range**: 0 to 255
- **Normalization formula**: `(byte - 127.4) / 128.0` → [-1.0, 1.0)

**Memory layout example**:
```
Byte:    [0]  [1]  [2]  [3]  [4]  [5]
Sample:   I₀   Q₀   I₁   Q₁   I₂   Q₂
Type:   uint8 uint8 uint8 uint8 uint8 uint8
```

### 3. AIRSPY Format (16-bit Signed, Little-Endian)

- **Format ID**: `AIRSPY`
- **Bytes per sample**: 4 (2 bytes I + 2 bytes Q)
- **Data type**: Signed 16-bit integers, little-endian
- **Byte layout**: `I₀_lo, I₀_hi, Q₀_lo, Q₀_hi, I₁_lo, I₁_hi, Q₁_lo, Q₁_hi, ...`
- **Value range**: -32768 to 32767
- **Normalization formula**: `int16 / 32768.0` → [-1.0, 1.0)

**Memory layout example**:
```
Byte:    [0]    [1]    [2]    [3]    [4]    [5]    [6]    [7]
Sample:  I₀_lo  I₀_hi  Q₀_lo  Q₀_hi  I₁_lo  I₁_hi  Q₁_lo  Q₁_hi
Type:    int16 (little-endian)      int16 (little-endian)
```

**Reading 16-bit values**:
```
I_value = (bytes[i] & 0xFF) | (bytes[i+1] << 8)    // little-endian
Q_value = (bytes[i+2] & 0xFF) | (bytes[i+3] << 8)
```

### 4. HYDRASDR Format (16-bit Signed, Little-Endian)

- **Format ID**: `HYDRASDR`
- **Bytes per sample**: 4 (2 bytes I + 2 bytes Q)
- **Identical to AIRSPY format in all respects**

## File Naming Convention

Recorded files follow this naming pattern:

```
{timestamp}_{name}_{format}_{frequency}_{sampleRate}.iq
```

**Components**:
- `timestamp`: `yyyyMMdd-HHmmss` format
- `name`: User-defined recording name
- `format`: One of HACKRF, RTLSDR, AIRSPY, HYDRASDR
- `frequency`: Center frequency with unit (e.g., "100MHz", "2.4GHz")
- `sampleRate`: Sample rate with unit (e.g., "6MSps", "10MSps")

**Example filename**:
```
20250111-143022_MyRecording_AIRSPY_100MHz_6MSps.iq
```

## Data Rate and File Size Calculations

### 6 MSps Example (16-bit format)

For a recording at **6 million samples per second** with 16-bit samples:

- **Data rate**: 6,000,000 samples/sec × 4 bytes/sample = **24 MB/sec**
- **File size per minute**: 24 MB/sec × 60 sec = **1.44 GB/min**
- **File size per hour**: 1.44 GB/min × 60 min = **86.4 GB/hour**
- **Sample period**: 1/6,000,000 = ~167 nanoseconds

### General Formulas

**8-bit formats** (HACKRF, RTLSDR):
```
Data rate (bytes/sec) = Sample_rate × 2
File size (bytes) = Sample_rate × 2 × Duration_in_seconds
```

**16-bit formats** (AIRSPY, HYDRASDR):
```
Data rate (bytes/sec) = Sample_rate × 4
File size (bytes) = Sample_rate × 4 × Duration_in_seconds
```

## Reading IQ Files

### Python Example (NumPy)

```python
import numpy as np

def read_iq_8bit_signed(filename):
    """Read 8-bit signed IQ file (HACKRF format)"""
    data = np.fromfile(filename, dtype=np.int8)
    i_samples = data[0::2]
    q_samples = data[1::2]
    iq_samples = ((i_samples - 128) + 1j * (q_samples - 128)) / 128.0
    return iq_samples

def read_iq_8bit_unsigned(filename):
    """Read 8-bit unsigned IQ file (RTLSDR format)"""
    data = np.fromfile(filename, dtype=np.uint8)
    i_samples = data[0::2]
    q_samples = data[1::2]
    iq_samples = ((i_samples - 127.4) + 1j * (q_samples - 127.4)) / 128.0
    return iq_samples

def read_iq_16bit_signed(filename):
    """Read 16-bit signed little-endian IQ file (AIRSPY/HYDRASDR format)"""
    data = np.fromfile(filename, dtype=np.int16)
    i_samples = data[0::2]
    q_samples = data[1::2]
    iq_samples = (i_samples + 1j * q_samples) / 32768.0
    return iq_samples

# Usage example
samples = read_iq_16bit_signed('20250111-143022_MyRecording_AIRSPY_100MHz_6MSps.iq')
sample_rate = 6_000_000  # 6 MSps
duration = len(samples) / sample_rate
print(f"Recording duration: {duration:.2f} seconds")
```

### C/C++ Example

```c
#include <stdio.h>
#include <stdint.h>
#include <complex.h>

// Read 16-bit signed IQ file
void read_iq_16bit(const char* filename, float complex* output, size_t num_samples) {
    FILE* fp = fopen(filename, "rb");
    if (!fp) return;

    int16_t buffer[2];  // I and Q
    for (size_t i = 0; i < num_samples; i++) {
        fread(buffer, sizeof(int16_t), 2, fp);
        float i_val = buffer[0] / 32768.0f;
        float q_val = buffer[1] / 32768.0f;
        output[i] = i_val + q_val * I;  // Complex number
    }

    fclose(fp);
}
```

### GNU Radio

The format is compatible with GNU Radio's **File Source** block:

- **For 8-bit signed** (HACKRF): Use `byte` type, then multiply by (1/128.0)
- **For 8-bit unsigned** (RTLSDR): Use `byte` type, subtract 127.4, multiply by (1/128.0)
- **For 16-bit signed** (AIRSPY/HYDRASDR): Use `short` type, then multiply by (1/32768.0)

Set **Output Type** to `Complex` and configure **Repeat** as needed.

## Metadata Storage

Metadata is stored in the app's Room database, not in the file. The Recording entity contains:

```kotlin
data class Recording(
    val id: Long,
    val name: String,
    val frequency: Long,        // Center frequency in Hz
    val sampleRate: Long,       // Sample rate in Hz
    val date: Long,             // Unix timestamp
    val fileFormat: FilesourceFileFormat,  // HACKRF, RTLSDR, AIRSPY, or HYDRASDR
    val sizeInBytes: Long,
    val filePath: String,
    val favorite: Boolean
)
```

## Implementation Details

### Recording Process

The recording is handled by `Scheduler.kt:154-188`:

1. Raw byte packets from the IQ source are written directly to a `BufferedOutputStream`
2. No conversion or processing occurs during recording
3. File size is tracked in bytes as data is written
4. Recording stops when:
   - Maximum recording time is reached
   - Maximum file size is reached
   - User manually stops
   - Scheduler shuts down

### Source Code References

- **IQ Converters**: `app/src/main/java/com/mantz_it/rfanalyzer/source/`
  - `Signed8BitIQConverter.java` - HACKRF format
  - `Unsigned8BitIQConverter.java` - RTLSDR format
  - `Signed16BitIQConverter.kt` - AIRSPY/HYDRASDR format
- **Recording Logic**: `app/src/main/java/com/mantz_it/rfanalyzer/analyzer/Scheduler.kt:108-124`
- **Database Schema**: `app/src/main/java/com/mantz_it/rfanalyzer/database/RecordingDao.kt:45-55`
- **Format Enum**: `app/src/main/java/com/mantz_it/rfanalyzer/ui/composable/SourceTab.kt:68-73`

## Compatibility

This raw IQ format is compatible with many SDR analysis tools:

- **GNU Radio** - File Source block
- **URH (Universal Radio Hacker)** - Import IQ recording
- **Inspectrum** - Waterfall analysis
- **MATLAB/Octave** - `fread()` with appropriate data types
- **Python SciPy/NumPy** - `np.fromfile()`
- **Custom DSP applications** - Standard binary I/Q format

## Notes

1. **Endianness**: 16-bit formats use little-endian byte order (Intel/x86 convention)
2. **No padding**: Samples are tightly packed with no alignment padding
3. **Continuous data**: No time gaps or frame markers in the file
4. **DC offset**: Files may contain DC offset characteristic of the hardware
5. **I/Q imbalance**: Hardware-specific I/Q gain and phase imbalances may be present
