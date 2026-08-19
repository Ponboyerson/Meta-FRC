# Unity Project Setup Guide

**Target:** Meta Quest 3S  
**Unity Version:** 6 (6000.0.29f1) LTS  
**Track:** B — Meta FRC Localization

---

## Prerequisites

### 1. Install Unity Hub & Engine

1. Download Unity Hub from https://unity.com/download
2. Install Unity 6 (6000.0.29f1) LTS
3. **CRITICAL:** During installation, check these boxes:
   - ✅ Android Build Support
   - ✅ OpenJDK
   - ✅ Android SDK & NDK Tools

### 2. Acquire Meta SDKs (Free from Unity Asset Store)

You must "purchase" (free) these SDKs from the Unity Asset Store and add them to your account:

1. **Meta XR All-in-One SDK**
   - https://assetstore.unity.com/packages/tools/integration/meta-xr-all-in-one-sdk-269873
   
2. **Meta XR Interaction SDK Essentials**
   - https://assetstore.unity.com/packages/tools/integration/meta-xr-interaction-sdk-essentials-269874
   
3. **Meta MR Utility Kit**
   - https://assetstore.unity.com/packages/tools/integration/meta-mr-utility-kit-269875

After "purchasing," they will be available for import via Unity Package Manager.

### 3. Download MessagePack Plugin

The C# Network Tables 4 (NT4) library requires MessagePack for serialization:

1. Go to https://github.com/QuestNav/QuestNav/releases (or main QuestNav repo releases)
2. Download the latest `.unitypackage` file for MessagePack
3. Save it to a known location (e.g., `~/Downloads/MessagePack.unitypackage`)

---

## Project Initialization

### Step 1: Clone the Repository

```bash
cd /path/to/workspace
git clone https://github.com/YOUR_TEAM/meta-frc.git
cd meta-frc
```

### Step 2: Open in Unity Hub

1. Open Unity Hub
2. Click **"Add project from disk"** (or "Locate" if already listed)
3. Navigate to `/path/to/workspace/unity` folder
4. Select the folder and click "Add Project"
5. The project should now appear in your Unity Hub projects list

### Step 3: Open the Project

1. Click on the project in Unity Hub
2. Select Unity 6 (6000.0.29f1) LTS
3. Click "Open Project"
4. Wait for initial import and compilation (may take 5-10 minutes first time)

---

## Dependency Setup

### Import MessagePack Plugin

1. In Unity Editor, go to `Assets > Import Package > Custom Package...`
2. Navigate to where you saved `MessagePack.unitypackage`
3. Select it and click "Open"
4. In the import dialog, click **"All"** to select all assets
5. Click **"Import"**
6. Wait for import to complete

### Import Meta SDKs

1. Go to `Window > Package Manager`
2. Click the **+** button in top-left
3. Select **"Add package from git URL"** or **"Add package by name"** (depending on Unity version)
4. Add each Meta SDK:
   - Meta XR All-in-One SDK
   - Meta XR Interaction SDK Essentials
   - Meta MR Utility Kit
5. Wait for each to install

Alternatively, if using downloaded packages:
1. Go to `Assets > Import Package > Custom Package...`
2. Import each Meta SDK `.unitypackage` file

---

## Project Configuration

### Step 1: Switch Build Platform to Android

1. Go to `File > Build Profiles...` (or `File > Build Settings` in older Unity)
2. Find **Android** in the platform list
3. Click **"Switch Platform"**
4. Wait for Unity to reimport assets for Android (may take several minutes)

### Step 2: Run Meta Project Setup Tool

1. In Unity menu, go to `Meta > Tools > Project Setup Tool`
2. The tool will scan your project and show recommended fixes
3. Click **"Apply All"** or fix issues one by one
4. Continue until status reports **"XR Ready for Android"**

Common fixes applied:
- XR plugin initialization
- Android manifest permissions
- Player settings for VR
- Graphics API configuration (Vulkan/Metal)
- Input system configuration

### Step 3: Configure Build Settings

1. Go to `File > Build Profiles...` (or `File > Build Settings`)
2. Under Android settings:
   - **Scripting Backend:** IL2CPP
   - **Target Architect:** ARM64
   - **Minimum API Level:** Android 11.0 (API level 30) or higher (Quest 3S requirement)
   - **Target API Level:** Highest installed

3. **CRITICAL:** Check **"Development Build"**
   - ⚠️ **WARNING:** If Development Build is NOT checked, the app will crash on launch!
   - This enables necessary debugging features and NT4 communication

4. **Recommended:** Check **"Script Debugging"** for development

5. Click **"Player Settings..."** to open:
   - **Company Name:** Your team name (e.g., "FRC 11269")
   - **Product Name:** "Meta FRC Localization" or similar
   - **Version:** 1.0.0
   - **Bundle Identifier:** `com.yourteam.metafrc` (must be unique)
   
6. In **XR Settings** (under Player Settings):
   - Ensure **OpenXR** is enabled
   - Verify Meta XR plugins are listed

---

## QuestNav-Specific Configuration

### Locate MotionStreamer Script

The core pose-streaming functionality is in `MotionStreamer.cs` (or similar):

1. Navigate to `Assets/Scripts/` or search for `MotionStreamer`
2. Review NT4 publishing configuration:
   - Default publish rate: 90 Hz (matches display refresh)
   - Can be increased to 120 Hz if desired
   - Topic name: Typically `/questnav/pose` or similar

### Configure NetworkTables Settings

1. Find the NT4 client configuration (likely in a `NetworkTablesManager` or similar script)
2. Set the team number (for automatic IP resolution) or static IP
3. Verify topic names match what robot code expects

---

## Build the APK

### Step 1: Prepare for Build

1. Ensure no compilation errors in Console
2. Save all scenes (`Ctrl+S` or `Cmd+S`)
3. Go to `File > Build Profiles...`

### Step 2: Configure Build

1. Under Android platform:
   - **Build Type:** Development Build ✅ (CRITICAL!)
   - **Script Debugging:** ✅ (recommended for testing)
   - **Compression Method:** LZ4 (faster) or ZIP (smaller)

2. Click **"Build"** (not "Build and Run" unless device connected)

### Step 3: Choose Output Location

1. Create a `Builds/` folder in project root if it doesn't exist
2. Navigate to `meta-frc/unity/Builds/`
3. Enter filename: `MetaFRC_Localization.apk`
4. Click **"Save"**

### Step 4: Wait for Build

Unity will compile the project. This may take 5-15 minutes depending on hardware.

**Success:** You'll see `Build Succeeded` message and APK in output folder.

**Failure:** Check Console for errors. Common issues:
- Missing SDK components
- Incorrect Android SDK/NDK paths
- Compilation errors in scripts
- Insufficient disk space

---

## Install on Quest 3S

### Method 1: SideQuest (Recommended for Beginners)

1. Install SideQuest: https://sidequestvr.com/setup-howto
2. Enable Developer Mode on Quest 3S:
   - Use Meta Horizon mobile app
   - Go to Devices → [Your Headset] → Developer Mode → Enable
3. Connect Quest 3S to PC via USB-C cable
4. Put on headset and approve "Allow USB Debugging" prompt
5. Open SideQuest
6. Click the APK icon (top-right) → "Install APK file from folder"
7. Select your built APK
8. Wait for installation confirmation

### Method 2: ADB Command Line

1. Install Android Platform Tools: https://developer.android.com/studio/releases/platform-tools
2. Enable Developer Mode on Quest 3S (see above)
3. Connect via USB-C, approve debugging prompt
4. Open terminal/command prompt:
   ```bash
   adb devices  # Verify device is detected
   adb install -r path/to/MetaFRC_Localization.apk
   ```
5. Wait for "Success" message

### Method 3: Quest Deploy (Unity Plugin)

If Quest Deploy plugin is installed:
1. Connect Quest 3S via USB-C
2. Approve debugging prompt in headset
3. In Unity, go to `Meta > Tools > Quest Deploy`
4. Click **"Deploy"**
5. App will build and install automatically

---

## Initial Setup on Headset

### First Launch

1. Put on Quest 3S
2. Navigate to Apps → Unknown Sources (or Library → Unknown)
3. Find "Meta FRC Localization" app
4. Launch it

### Disable Wi-Fi and Bluetooth (CRITICAL)

**These commands persist through reboot but must be run initially:**

```bash
# Connect to headset via ADB
adb connect <headset-ip>  # Or ensure USB connection

# Disable Wi-Fi (persists through reboot)
adb shell svc wifi disable

# Disable Bluetooth (persists through reboot)
adb shell svc bluetooth disable

# Verify status
adb shell svc wifi status      # Should report "Wi-Fi is disabled"
adb shell svc bluetooth status # Should report "Bluetooth is disabled"
```

⚠️ **IMPORTANT:** These settings persist through normal reboots but may reset after:
- Factory reset
- Major OS updates
- Certain troubleshooting procedures

**Verify before every competition!**

### Disable Guardian/Experimental Features

Per QuestNav setup documentation:

```bash
# Disable Guardian boundary system (optional, use with caution)
adb shell setprop debug.oculus.guardian_pause 1

# Disable other experimental spaces features as needed
# Refer to QuestNav docs for specific commands
```

---

## Testing Pose Streaming

### Step 1: Connect to Robot Network

1. Plug USB-C-to-Ethernet adapter into Quest 3S
2. Connect Ethernet cable to robot network switch
3. Ensure adapter has link lights active

### Step 2: Verify Network Connection

```bash
# From pit laptop connected to same network:
adb connect <quest-static-ip>  # If using static IP

# Or verify via router/switch client list
```

### Step 3: Test NT4 Communication

From robot code or NT4 client (e.g., AdvantageScope, Shuffleboard):

1. Connect to NT4 server (roboRIO or local test server)
2. Look for QuestNav pose topic (e.g., `/questnav/pose`)
3. Verify data is publishing at expected rate (90-120 Hz)
4. Check pose values change when moving headset

### Troubleshooting

**No NT4 data:**
- Verify Development Build was checked
- Check NetworkTables IP configuration
- Ensure firewall allows NT4 traffic (port 1735)
- Verify Quest is on same network subnet

**App crashes on launch:**
- Almost always means Development Build was not checked
- Rebuild with Development Build ✅
- Check `adb logcat` for crash details

**Pose data seems wrong:**
- Verify coordinate system matches robot expectations
- Check if origin/offset needs calibration
- Review QuestNav documentation for coordinate conventions

---

## Updating the App

### Incremental Updates

For subsequent builds:

1. Make code changes in Unity
2. Rebuild APK (same process as above)
3. Install over existing version:
   ```bash
   adb install -r path/to/new.apk
   ```
4. The `-r` flag reinstalls while keeping data

### Version Tracking

Update version in:
- Player Settings → Other Settings → Version
- Consider adding version display in-app for debugging

---

## Known Issues & Workarounds

| Issue | Cause | Workaround |
|-------|-------|------------|
| App crashes immediately | Not Development Build | Rebuild with Development Build ✅ |
| Wi-Fi re-enables after update | OS update reset settings | Re-run `adb shell svc wifi disable` |
| No NT4 data | Wrong IP config | Verify team number or static IP |
| Low frame rate | Thermal throttling | Ensure ventilation, reduce workload |
| Tracking loss | Poor lighting/features | Improve environment lighting |

---

## Resources

- **QuestNav Documentation:** https://github.com/QuestNav/QuestNav
- **Meta XR SDK Docs:** https://developer.meta.com/docs/sdk/quest/
- **Unity Android Build Docs:** https://docs.unity3d.com/Manual/android-BuildProcess.html
- **NetworkTables 4 Spec:** https://github.com/wpilibsuite/allwpilib/blob/main/wpilibj/src/main/java/edu/wpi/first/networktables/README.md

---

## Next Steps

After successful Unity build and installation:

1. ✅ Phase 0 Complete: Wired link + pose streaming working
2. ➡️ Move to **Phase 1**: Implement pose fusion in `java-robot/` WPILib code
3. See `../java-robot/README.md` for robot code integration

---

## Revision History

- **Revision 1** (Current): Initial setup guide based on QuestNav patterns and project plan
