# RF Analyzer - User-Defined Recording Storage & Notifications

## Overview

Successfully implemented Storage Access Framework (SAF) support to allow users to choose where RF Analyzer stores recordings, plus recording finished notifications for smartwatch alerts. **No permissions required!**

## ✅ Fully Implemented Features

### Phase 1: Core SAF Support (COMPLETED & TESTED)
- ✅ Choose custom recording directory
- ✅ Record to custom directory using SAF
- ✅ Record to internal storage (backward compatible)
- ✅ Automatic file renaming with metadata
- ✅ Persist directory selection across app restarts

### Phase 2: File Operations (COMPLETED & TESTED)
- ✅ Delete recordings from custom directory
- ✅ Share recordings from custom directory
- ✅ Save recordings to storage from custom directory
- ✅ Play recordings from custom directory

### Phase 3: Recording Notifications (COMPLETED & TESTED)
- ✅ High-priority notification when recording finishes
- ✅ Vibration pattern for smartwatch alerts
- ✅ Notification shows recording name and file size
- ✅ Tap notification to open app

## Implementation Details

### 1. Storage Access Framework Support

#### **AppStateRepository.kt** (Line 240, 297)
- Added `recordingDirectoryUri` setting to store user's selected directory
- Empty string = internal storage, non-empty = SAF content:// URI
- Changed `RecordingFinished` event signature to accept `recordingFileRef: Any`

#### **MainActivity.kt** (Lines 131, 460, 461-485, 491-526, 868-900)
- Added `selectRecordingDirLauncher` using `OpenDocumentTree` contract
- Takes persistable URI permissions automatically
- Integrated with UiAction system via `OnChooseRecordingDirectoryClicked`
- **Delete support**: Handles both File and DocumentFile deletion
- **Share support**: Shares content:// URIs directly with FLAG_GRANT_READ_URI_PERMISSION
- **Save support**: Copies files using ContentResolver for both storage types
- **Notification support**: Posts high-priority notifications with vibration

#### **RecordingTab.kt** (Lines 94, 115, 144-166)
- Added "Choose Recording Folder" button in Recording tab
- Shows "Recording Folder: Custom" when directory is selected

#### **AnalyzerTabsComposable.kt** (Lines 146, 279)
- Wired up `recordingDirectoryUri` state flow
- Passed to RecordingTabComposable

#### **MainViewModel.kt** (Lines 114, 126, 155-173, 459, 536-564, 646-712, 755-773)
- Added `OnChooseRecordingDirectoryClicked` UiAction
- Added `ShowRecordingFinishedNotification` UiAction
- Connected recording tab action to launcher
- **File rename logic**: Uses tree URI to find and rename files (avoids UnsupportedOperationException)
- **Play recording**: Extracts filename from both File paths and content:// URIs using DocumentFile
- Triggers notification when recording finishes

#### **AnalyzerService.kt** (Lines 558-653)
- **Major change**: Complete rewrite of `startRecording()` method
- Detects custom directory URI and switches between SAF and File APIs
- Creates files using DocumentFile API for SAF
- Handles errors gracefully with user-friendly messages
- Returns either File or Uri reference to recording finished handler

#### **RFAnalyzerApplication.kt** (Lines 42-64)
- Added notification channel `RECORDING_CHANNEL` with IMPORTANCE_HIGH
- Enabled vibration with custom pattern: 250ms on, 250ms off, 250ms on
- Separate from foreground service channel (which is IMPORTANCE_LOW)

### 2. Dependencies Added

#### **gradle/libs.versions.toml**
```toml
documentfile = "1.0.1"
```

#### **app/build.gradle.kts**
```kotlin
implementation(libs.androidx.documentfile)
```

## How It Works

### Recording to Custom Directory

**User Flow:**
1. User taps **"Choose Recording Folder"** button in Recording tab
2. Android system picker appears (works on SD cards, USB drives, cloud storage)
3. User selects folder
4. App takes persistable URI permission
5. Toast confirms folder selected
6. Future recordings go to that folder

**Recording Flow:**
1. App checks `recordingDirectoryUri` setting
2. **If empty**: Uses internal storage (existing behavior)
3. **If set**: Uses SAF DocumentFile API to create file in user's folder
4. Records to `ongoing_recording.iq` temporary filename
5. When finished, uses tree URI to find and rename file to: `YYYYMMDD-HHmmss_Name_Format_Freq_SampleRate.iq`
6. Stores content:// URI in database
7. Shows notification with vibration

### File Operations

All file operations support both internal storage (File API) and custom directories (SAF/DocumentFile API):

**Delete:**
- Detects `content://` prefix in filePath
- Uses `DocumentFile.fromSingleUri()` to get file handle
- Calls `delete()` method
- Removes from database after snackbar dismissed without undo

**Share:**
- Detects `content://` prefix in filePath
- Creates Intent with `ACTION_SEND` and content URI
- Adds `FLAG_GRANT_READ_URI_PERMISSION` for other apps to read
- Launches share chooser

**Save to Storage:**
- Detects `content://` prefix in filePath
- Opens input stream from source URI
- Opens output stream to destination URI
- Copies data using `inputStream.copyTo(outputStream)`

**Play Recording:**
- Detects `content://` prefix in filePath
- Uses `DocumentFile.fromSingleUri()` to get filename
- Sets `filesourceUri` to content:// URI
- FileIQSource already supports content:// URIs via ContentResolver

### Recording Finished Notification

**When recording stops:**
1. Recording is inserted into database
2. `ShowRecordingFinishedNotification` UiAction is sent
3. Notification is posted to `RECORDING_CHANNEL`
4. Notification includes:
   - Title: "Recording Finished"
   - Content: Recording name and formatted file size
   - High priority (appears on smartwatch)
   - Vibration pattern (250ms on, 250ms off, 250ms on)
   - Tap to open app
   - Auto-dismiss on tap

## Testing Results

### ✅ Tested and Working
- [x] Button appears in Recording tab
- [x] Tapping button opens folder picker
- [x] Selecting folder shows toast message
- [x] Button text changes to "Recording Folder: Custom"
- [x] Recording to custom folder works
- [x] Recording to internal storage still works
- [x] File appears in chosen folder with correct name
- [x] Recording metadata saved to database
- [x] Delete recordings from custom folder
- [x] Share recordings from custom folder
- [x] Save recordings to storage from custom folder
- [x] Play recordings from custom folder
- [x] Notification appears when recording finishes
- [x] Smartwatch vibrates on recording finished

### Known Issues Resolved
1. ✅ **File rename failure**: Fixed by using tree URI instead of single file URI
   - Issue: `SingleDocumentFile.renameTo()` throws `UnsupportedOperationException`
   - Solution: Use `DocumentFile.fromTreeUri()` to get tree, then `findFile()` to get file handle

2. ✅ **Delete not working**: Fixed by implementing SAF delete logic
   - Issue: Only File.delete() was called, didn't support content:// URIs
   - Solution: Detect URI scheme and use DocumentFile API for SAF files

3. ✅ **Missing imports**: Added `androidx.documentfile` dependency and R import

4. ✅ **Context injection**: Added @ApplicationContext to MainViewModel constructor

## Architecture Notes

### Why Tree URI for Rename?
Android's DocumentFile API has two classes:
- **SingleDocumentFile**: Created from single file URI, does NOT support `renameTo()`
- **TreeDocumentFile**: Created from tree/directory URI, DOES support `renameTo()`

Solution: Store directory URI in settings, use it to find files for rename operations.

### URI Permissions
The app uses `takePersistableUriPermission()` which means:
- ✅ Permissions survive app restarts
- ❌ Permissions lost on app reinstall (user must re-select folder)
- ✅ No runtime permissions needed (handled by SAF)

### Backward Compatibility
All changes are backward compatible:
- Empty `recordingDirectoryUri` = use internal storage (existing behavior)
- File operations check for `content://` prefix before choosing API
- Database stores either file path or content:// URI as string

## File Locations

All modified files:
```
app/src/main/java/com/mantz_it/rfanalyzer/
├── RFAnalyzerApplication.kt (notification channels)
├── analyzer/AnalyzerService.kt (major changes - recording)
├── database/AppStateRepository.kt (new setting + event signature)
├── ui/
│   ├── MainActivity.kt (major changes - file ops + notifications)
│   ├── MainViewModel.kt (major changes - rename logic + notifications)
│   └── composable/
│       ├── AnalyzerTabsComposable.kt (state wiring)
│       └── RecordingTab.kt (UI button)
gradle/libs.versions.toml (new dependency)
app/build.gradle.kts (new dependency)
```

## Important Notes

1. **No Android permissions required** - SAF handles everything
2. **Backwards compatible** - Existing internal storage recordings still work
3. **User can switch back** - Just don't set a custom directory
4. **URI permissions persist** - Survive app restarts (but not reinstalls)
5. **Works with cloud storage** - Google Drive, Dropbox, etc. if mounted
6. **Smartwatch compatible** - High priority notifications with vibration

## Commit Message Suggestion

```
feat: Add user-defined recording storage and notifications

Implement Storage Access Framework to allow users to choose where recordings
are stored. Add high-priority notifications for recording completion that
trigger smartwatch vibration. No permissions required.

Features:
- New "Choose Recording Folder" button in Recording tab
- Automatic URI permission management
- Full file operation support (delete, share, save, play)
- Recording finished notifications with vibration
- Backward compatible with internal storage
- Graceful error handling
- Persists across app restarts

Changes:
- Added androidx.documentfile dependency
- Created RECORDING_CHANNEL for high-priority notifications
- Rewrote startRecording() to support SAF
- Updated all file operations to handle content:// URIs
- Fixed file rename using tree URI approach

🤖 Generated with Claude Code

Co-Authored-By: Claude <noreply@anthropic.com>
```

## Future Enhancements (Optional)

These features were not implemented but could be added:

1. **Directory validation before recording**
   - Check available space
   - Verify write permissions still valid
   - Show warning if space running low

2. **"Reset to Internal Storage" button**
   - Clear `recordingDirectoryUri` setting
   - Convenient way to switch back

3. **Migration tool for existing recordings**
   - Copy/move recordings from internal to custom directory
   - Update database paths

4. **Display available space**
   - Show free space in custom directory
   - Warning when nearing capacity

5. **Recording started notification**
   - Currently only notifies when recording stops
   - Could add notification when recording starts
