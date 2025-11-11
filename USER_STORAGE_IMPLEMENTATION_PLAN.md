# Implementation Plan: User-Defined Recording Storage

## Current Implementation Analysis

### Current Storage Location
- **Internal app storage**: `Context.filesDir/recordings/`
  - Location: `/data/data/com.mantz_it.rfanalyzer/files/recordings/`
  - **Advantages**: No permissions required, private to app, automatically cleaned on uninstall
  - **Disadvantages**: Not user-accessible without root, limited by app storage quota, deleted on uninstall

### Current File Operations
1. **Recording**: Files are written directly to internal storage (`AnalyzerService.kt:565-568`)
2. **Sharing**: Uses FileProvider to share via intents (`MainActivity.kt:741-749`)
3. **Export**: Users can manually export recordings one-by-one using SAF CreateDocument (`HelperComposables.kt:1162-1172`)

### Current Permissions
```xml
<!-- No storage permissions currently required -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Implementation Options

### Option 1: Storage Access Framework (SAF) - RECOMMENDED ✓

**Description**: Use Android's Storage Access Framework to let users choose a persistent directory for recordings.

#### Advantages
- ✅ **No permissions required** - Works on all Android versions without storage permissions
- ✅ **User control** - Users pick exactly where files go
- ✅ **Scoped storage compliant** - Works on Android 11+ (API 30+)
- ✅ **Survives app uninstall** - Files remain after uninstall
- ✅ **Works with cloud storage** - Can save to Google Drive, Dropbox, etc.

#### Disadvantages
- ⚠️ Slightly more complex implementation
- ⚠️ User must grant directory access each app install (URI permissions don't survive reinstall)
- ⚠️ Some performance overhead compared to direct file access

#### Required Changes

**1. Add Persistent URI Permission Storage**

Store the selected directory URI in DataStore:

```kotlin
// In AppStateRepository.kt
val recordingDirectoryUri = persistentState(
    key = "recording_directory_uri",
    default = ""  // Empty = use internal storage
)
```

**2. Add Directory Picker UI**

In the Recording tab settings, add a button to choose directory:

```kotlin
// In RecordingTab.kt or SettingsTab.kt
Button(onClick = {
    // Launch OpenDocumentTree intent
    settingsTabActions.onChooseRecordingDirectory()
}) {
    Text("Choose Recording Folder")
}
```

**3. Implement Directory Selection**

```kotlin
// In MainActivity.kt
private val selectRecordingDirLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
) { uri: Uri? ->
    uri?.let {
        // Take persistable URI permission
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        // Save to DataStore
        lifecycleScope.launch {
            appStateRepository.recordingDirectoryUri.set(uri.toString())
        }

        Toast.makeText(this, "Recording folder set", Toast.LENGTH_SHORT).show()
    }
}
```

**4. Modify Recording Logic**

Update `AnalyzerService.kt:558-589` to use DocumentFile API:

```kotlin
fun startRecording() {
    if (appStateRepository.recordingRunning.value) return

    val recordingStartedTimestamp = System.currentTimeMillis()
    val filename = "ongoing_recording.iq"

    // Check if user has selected custom directory
    val customDirUriStr = appStateRepository.recordingDirectoryUri.value

    val outputStream = if (customDirUriStr.isNotEmpty()) {
        // Use SAF to write to user-selected directory
        val treeUri = Uri.parse(customDirUriStr)
        val docTree = DocumentFile.fromTreeUri(this, treeUri)

        if (docTree == null || !docTree.canWrite()) {
            Log.e(TAG, "Cannot write to selected directory")
            appStateRepository.emitAnalyzerEvent(
                AppStateRepository.AnalyzerEvent.SourceFailure(
                    "Cannot write to recording directory. Please re-select folder."
                )
            )
            return
        }

        // Create or get existing file
        val existingFile = docTree.findFile(filename)
        val docFile = existingFile?.also { it.delete() }
            ?: docTree.createFile("application/octet-stream", filename)

        if (docFile == null) {
            Log.e(TAG, "Failed to create file in selected directory")
            return
        }

        contentResolver.openOutputStream(docFile.uri)
    } else {
        // Use internal storage (existing behavior)
        val filepath = "$RECORDINGS_DIRECTORY/$filename"
        val file = File(this.filesDir, filepath)
        FileOutputStream(file)
    }

    if (outputStream == null) {
        Log.e(TAG, "Failed to open output stream")
        return
    }

    val bufferedOutputStream = BufferedOutputStream(outputStream)

    // Rest of recording logic remains the same...
    scheduler?.startRecording(
        bufferedOutputStream = bufferedOutputStream,
        // ... other parameters
    )
}
```

**5. Update Recording Finished Handler**

Modify recording completion to handle both storage types:

```kotlin
// In MainViewModel.kt, around line 636
is AppStateRepository.AnalyzerEvent.RecordingFinished -> {
    val customDirUri = appStateRepository.recordingDirectoryUri.value

    val finalPath = if (customDirUri.isNotEmpty()) {
        // File is in user directory - store URI reference
        val treeUri = Uri.parse(customDirUri)
        val docTree = DocumentFile.fromTreeUri(application, treeUri)
        val finalFilename = // calculate filename from metadata
        val docFile = docTree?.findFile("ongoing_recording.iq")

        // Rename to final name
        docFile?.renameTo(finalFilename)
        docFile?.uri?.toString() ?: ""
    } else {
        // File is in internal storage - rename as before
        val finalFile = // existing renaming logic
        finalFile.absolutePath
    }

    val newRecording = Recording(
        // ... other fields
        filePath = finalPath,  // Can be file path or content:// URI
        // ...
    )
}
```

**6. Update File Access Throughout App**

All places that access recordings need to handle both:
- File paths (internal storage): `/data/data/.../files/recordings/...`
- Content URIs (SAF): `content://com.android.externalstorage.documents/tree/...`

Affected locations:
- `MainActivity.kt:460-461` - Delete recording
- `MainActivity.kt:470` - Share recording
- `MainActivity.kt:723-739` - Save to user directory
- `MainViewModel.kt:528-541` - Play recording

Helper function:
```kotlin
private fun getInputStreamForRecording(recording: Recording): InputStream? {
    return if (recording.filePath.startsWith("content://")) {
        contentResolver.openInputStream(Uri.parse(recording.filePath))
    } else {
        File(recording.filePath).inputStream()
    }
}
```

#### Minimal Changes (Scoped Implementation)

For a simpler first implementation:
1. Only add directory picker for **new recordings**
2. Keep existing recordings in internal storage
3. Add "Use custom folder" toggle in recording settings
4. When toggled ON, show directory picker
5. When toggled OFF, use internal storage

---

### Option 2: External Storage with Permissions (NOT RECOMMENDED)

**Description**: Request MANAGE_EXTERNAL_STORAGE or scoped storage permissions.

#### Why Not Recommended
- ❌ **MANAGE_EXTERNAL_STORAGE** requires special approval from Google Play
- ❌ **Scoped storage** (API 29+) is restrictive and doesn't give true directory access
- ❌ Permissions denied by many users
- ❌ Deprecated approach on modern Android
- ❌ More maintenance burden for different Android versions

#### Required Permissions (if you still want this approach)

For Android 10+ (API 29+):
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                 android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                 android:maxSdkVersion="29" />

<!-- For Android 13+ (API 33+), need more granular permissions -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
```

For unrestricted access (requires Google Play approval):
```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

---

## Recommended Implementation Path

### Phase 1: Basic SAF Support (Minimal Changes)
1. Add `recordingDirectoryUri` to AppStateRepository
2. Add "Choose Recording Folder" button in Settings tab
3. Implement `OpenDocumentTree` launcher in MainActivity
4. Modify `AnalyzerService.startRecording()` to support both storage types
5. Test with both internal and external storage

**Estimated effort**: 4-6 hours

### Phase 2: Full Integration
1. Update recording finished handler to rename files properly
2. Update all file access points (play, share, delete)
3. Add indicator in UI showing current recording location
4. Handle edge cases (permission revoked, directory deleted, etc.)
5. Add migration option to move existing recordings

**Estimated effort**: 6-8 hours

### Phase 3: Polish & UX
1. Show available space in selected directory
2. Add "Reset to default (internal)" option
3. Validate directory has write access before recording
4. Better error messages when directory access fails
5. Documentation updates

**Estimated effort**: 2-4 hours

---

## Testing Checklist

### Functional Tests
- [ ] Select custom directory via SAF
- [ ] Record to custom directory successfully
- [ ] Recording appears in chosen folder (verify with file manager)
- [ ] Playback recording from custom location
- [ ] Share recording from custom location
- [ ] Delete recording from custom location
- [ ] Switch back to internal storage
- [ ] Recording survives app restart
- [ ] Handle directory permission revoked
- [ ] Handle directory deleted externally

### Android Version Tests
- [ ] Android 11+ (API 30+) - Primary target
- [ ] Android 10 (API 29) - Scoped storage
- [ ] Android 9 and below (API 28) - Legacy storage

### Edge Cases
- [ ] No SD card available
- [ ] SD card ejected during recording
- [ ] Out of space on external storage
- [ ] User selects read-only directory
- [ ] User selects cloud storage (Google Drive)
- [ ] App reinstall (permissions don't persist)
- [ ] Directory path contains special characters

---

## Code References

### Files to Modify
1. **app/src/main/java/com/mantz_it/rfanalyzer/database/AppStateRepository.kt**
   - Add `recordingDirectoryUri` state

2. **app/src/main/java/com/mantz_it/rfanalyzer/analyzer/AnalyzerService.kt**
   - Modify `startRecording()` (lines 558-589)
   - Support both File and DocumentFile APIs

3. **app/src/main/java/com/mantz_it/rfanalyzer/ui/MainActivity.kt**
   - Add `selectRecordingDirLauncher`
   - Update file operations to handle content URIs
   - Lines to modify: 243-246, 456-470, 723-739

4. **app/src/main/java/com/mantz_it/rfanalyzer/ui/MainViewModel.kt**
   - Update recording finished handler (line 636)
   - Add directory picker action

5. **app/src/main/java/com/mantz_it/rfanalyzer/ui/composable/SettingsTab.kt** or **RecordingTab.kt**
   - Add "Choose Recording Folder" UI

6. **app/src/main/java/com/mantz_it/rfanalyzer/database/RecordingDao.kt**
   - Recording.filePath can now be either file path or content:// URI
   - No schema changes needed

### New Dependencies
None required - DocumentFile is part of AndroidX (already included).

---

## Alternative: getExternalFilesDir() Approach

A simpler middle-ground option:

```kotlin
val externalRecordingsDir = getExternalFilesDir(null)?.resolve("recordings")
```

**Characteristics**:
- ✅ No permissions required
- ✅ User can access via file manager: `/sdcard/Android/data/com.mantz_it.rfanalyzer/files/recordings/`
- ✅ Simple implementation (just change one path)
- ⚠️ Still deleted on app uninstall
- ⚠️ User cannot choose location
- ⚠️ Requires external storage to be mounted

**Implementation**:
```kotlin
// In MainActivity.kt, line 243
val recordingsDir = getExternalFilesDir(null)?.resolve(RECORDINGS_DIRECTORY)
    ?: File(filesDir, RECORDINGS_DIRECTORY)  // Fallback to internal
```

This is the **quickest option** if you just want user-accessible files without full directory selection.

---

## Recommendation Summary

**Best approach**: **Option 1 (SAF)** with **Phase 1** implementation first.

**Why**:
1. No permissions needed
2. Modern Android-compliant approach
3. Users get full control
4. Can be implemented incrementally
5. Files survive app uninstall

**Quick alternative**: Use `getExternalFilesDir()` if you just need basic external access without user choice.

**Avoid**: Permission-based approaches (Option 2) - deprecated and problematic on modern Android.
