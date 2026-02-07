# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RF Analyzer is an Android app for real-time spectrum analysis using SDR hardware (HackRF, RTL-SDR, Airspy, HydraSDR). It provides live FFT and waterfall plots of the radio frequency spectrum with support for demodulation (CW, AM, nFM, wFM, LSB, USB), recording raw IQ data, and playback from files.

**Recent Features (v2.1+):**
- User-defined recording storage via Storage Access Framework (no permissions required)
- Support for SD cards, USB drives, and cloud storage for recordings
- Recording finished notifications with smartwatch vibration support
- Full file operations (play, share, delete, save) on custom directory recordings

**Key Technologies:**
- Android SDK (minSdk 28, targetSdk 36)
- Kotlin + Jetpack Compose for UI
- Native C/C++ (JNI/CMake) for DSP operations
- Hilt for dependency injection
- Room for database
- DataStore for preferences

## Build & Development Commands

### Building the App

```bash
# Build debug APK (uses MockedBillingRepository)
./gradlew assembleDebug

# Build release APK with ProGuard/R8
./gradlew assembleRelease

# Build specific flavor
./gradlew assemblePlayDebug    # Google Play version
./gradlew assembleFossDebug    # FOSS version (no billing)
```

### Running Tests

```bash
# Run unit tests (note: currently no unit tests exist in the project)
./gradlew test

# Run instrumented tests on connected device/emulator
./gradlew connectedAndroidTest

# Run specific instrumented test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mantz_it.rfanalyzer.dsp.RationalResamplerTest
```

### Documentation

```bash
# Build MkDocs documentation site (embedded in app assets)
mkdocs build --clean --no-directory-urls --site-dir build_site

# Serve documentation locally for development
mkdocs serve

# Note: Documentation is automatically built and copied to app/src/main/assets/docs during preBuild
```

### Cleaning

```bash
# Clean build artifacts
./gradlew clean
```

## Architecture Overview

### Multi-Module Structure

The project consists of several Gradle modules:

- **app**: Main application module containing UI, business logic, and SDR source implementations
- **nativedsp**: Native DSP library with PFFFT (Fast Fourier Transform) implementation
- **libairspy**: Airspy SDR driver wrapper (C++ JNI binding to libairspy)
- **libhydrasdr**: HydraSDR driver wrapper (C++ JNI binding to libhydrasdr)
- **libusb**: libusb library for USB device communication (used by libairspy and libhydrasdr)

### Signal Processing Pipeline

The core architecture follows a producer-consumer pattern orchestrated by `AnalyzerService`:

1. **IQ Source** (`IQSourceInterface` implementations):
   - `HackrfSource`, `RtlsdrSource`, `AirspySource`, `HydraSdrSource`: Hardware SDR sources
   - `FileIQSource`: Playback from recorded files
   - Produce raw IQ sample packets at various sample rates

2. **Scheduler** (`Scheduler.kt`):
   - Manages the processing queue between source and consumers
   - Coordinates downsampling when source rate exceeds processing requirements
   - Uses `RationalResampler` for sample rate conversion

3. **DSP Chain**:
   - **FftProcessor** (`FftProcessor.kt`): FFT analysis for spectrum display using native PFFFT
   - **Demodulator** (`Demodulator.kt`): Audio demodulation (AM, FM, SSB, CW) with filtering
   - Both consume sample packets from the scheduler independently

4. **Audio Output** (`AudioSink.java`):
   - Plays demodulated audio via Android AudioTrack

### Key Design Patterns

**Foreground Service Architecture:**
- `AnalyzerService` runs as a foreground service to ensure continuous signal processing
- Bound service pattern: `MainActivity` binds to `AnalyzerService` to control the processing pipeline
- Service lifecycle independent of activity lifecycle for background operation

**Dependency Injection:**
- Hilt provides singletons for `AppDatabase`, `AppStateRepository`, `BillingRepository`
- Flavor-specific injection: Debug/FOSS builds use `MockedBillingRepository`

**State Management:**
- `MainViewModel` contains UI state and business logic
- `AppStateRepository` persists user preferences via DataStore
- `RecordingDao` manages recording metadata in Room database

**Native DSP:**
- JNI bindings in `nativedsp/src/main/cpp/nativedsp.cpp`
- PFFFT library for high-performance FFT
- CMake build system for native modules

### Product Flavors

Two flavors control billing implementation:
- **play**: Google Play version with real billing (`BillingRepository`)
- **foss**: FOSS version without billing (`MockedBillingRepository`)

Both debug builds and foss builds use mocked billing (see `AppModule.kt:86`).

## Important Development Notes

### Recording Storage (Storage Access Framework)

The app supports user-defined recording directories via Android's Storage Access Framework (SAF):
- **AppStateRepository.recordingDirectoryUri**: Stores selected directory URI (empty = internal storage)
- **File operations**: All file ops (delete, share, save, play) detect `content://` prefix and use appropriate API
- **Recording flow**: AnalyzerService creates files via DocumentFile when custom directory set
- **Rename operation**: Uses tree URI (not single file URI) to avoid UnsupportedOperationException
- **Dependencies**: Requires `androidx.documentfile:documentfile:1.0.1`

Key implementation details:
- `SingleDocumentFile` does NOT support `renameTo()` - must use `TreeDocumentFile`
- Store directory URI, use `findFile()` to get file handle for rename
- Check `filePath.startsWith("content://")` to determine storage type
- FileIQSource already supports content:// URIs via ContentResolver

See IMPLEMENTATION_SUMMARY.md for complete details.

### Recording Notifications

High-priority notifications are posted when recordings finish:
- **Channel**: `RECORDING_CHANNEL` with `IMPORTANCE_HIGH` (enables smartwatch vibration)
- **Pattern**: 250ms on, 250ms off, 250ms on
- **Trigger**: `ShowRecordingFinishedNotification` UiAction in MainViewModel
- **Handler**: `showRecordingFinishedNotification()` in MainActivity

### Sample Rate Handling

The app uses a fixed audio sample rate of **48000 Hz** (see commit message mentioning switch from 31250). The `RationalResampler` converts arbitrary SDR sample rates to this target rate using rational approximation with limited denominator to keep filter lengths manageable.

### DSP Filter Delays

When working with DSP components (especially `RationalResampler` and `FirFilter`), be aware of filter group delays. Tests account for empirically determined delays (e.g., 51 samples in `RationalResamplerTest.kt:82`).

### Native Library Dependencies

- Native modules require CMake 3.22.1+ and NDK with 16KB page size support
- Use `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` flag for Android 15+ compatibility
- libairspy and libhydrasdr depend on libusb module

### USB Device Handling

SDR hardware access requires USB OTG support. The app handles:
- USB permission requests via Android USB Host API
- Device attachment/detachment broadcasts
- Multiple device types with different VID/PIDs

### Recording Format

Recordings are stored as raw IQ files with metadata in Room database. Supported formats (see `FilesourceFileFormat` enum):
- Signed 8-bit
- Unsigned 8-bit
- Signed 16-bit (default)
- Float 32-bit

## Testing Notes

Instrumented tests are in `app/src/androidTest/`:
- `RationalResamplerTest.kt`: Tests resampler accuracy and rational approximation
- `ResamplerTest.kt`: Additional resampler validation
- `ApplicationTest.kt`: Basic application test

No unit tests currently exist. When adding tests, place them in `app/src/test/`.

## License Restrictions

The app is GPL v2+, but the following are NOT covered by GPL:
- Application name "RF Analyzer"
- Logo and app icon
- Branding elements

Do not use these in forks without permission from the author (Dennis Mantz, DM4NTZ).
