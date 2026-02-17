# WearOS Data Layer Communication - Issue Resolution Documentation

## 🎯 Problem Summary

Messages were being sent successfully from Samsung Watch 6 Classic to Android phone (Redmi 12 5G), but **Google Play Services was blocking delivery** with the error:

```
W WearableService: Mismatched certificate: AppKey[<hidden>,48b597f93e6c34ae1523a82f01398ed7ad46d640]
W WearableService: Failed to deliver message to AppKey; Event[onMessageReceived, 
event=requestId=17125, action=/text-path, dataSize=20, source=be02beed]
```

## 🔍 Root Cause Analysis

### The Core Issue: **Certificate Mismatch**

Google Play Services uses **app signing certificates** to verify that WearOS messages are coming from trusted paired applications. When the phone app and watch app have **different signing certificates**, Google Play Services **rejects the messages** for security reasons.

### Why This Happened

1. **Multiple Rebuild Cycles**: During development, we rebuilt the apps multiple times
2. **Auto-generated Debug Keys**: Each build was using Android's auto-generated debug keystore from different locations:
   - `~/.android/debug.keystore` (default)
   - Gradle's temporary keystores
3. **Timing Differences**: Phone and watch apps were built at different times with different auto-generated keys
4. **Cached Certificates**: Google Play Services on the phone cached the certificate from the first installation

### Technical Details

#### How WearOS Security Works:
```
Watch App (Certificate A) → Message → Google Play Services → Verification → Phone App (Certificate B?)
                                                              ↓
                                                         ❌ Mismatch = Message Rejected
```

Google Play Services verifies:
- Both apps have the same `applicationId` (✅ com.firsttrial)
- Both apps are signed with the **same certificate** (❌ FAILED initially)
- Apps are on paired/connected devices (✅ Working)

#### The AppKey Hash
The error showed `AppKey[<hidden>,48b597f93e6c34ae1523a82f01398ed7ad46d640]`

This hash represents the **expected certificate** (from watch app), but the phone app had a **different certificate hash**, causing the mismatch.

## ✅ The Solution

### Step 1: Create a Shared Debug Keystore

Created a single keystore that both apps would use:

```bash
cd android
keytool -genkeypair -v \
  -keystore debug.keystore \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass android \
  -keypass android \
  -dname "CN=Android Debug,O=Android,C=US"
```

### Step 2: Configure Both Apps to Use the Same Keystore

#### Phone App (`android/app/build.gradle`):
```groovy
signingConfigs {
    debug {
        storeFile file('../debug.keystore')  // Shared keystore
        storePassword 'android'
        keyAlias 'androiddebugkey'
        keyPassword 'android'
    }
}
buildTypes {
    debug {
        signingConfig signingConfigs.debug
    }
}
```

#### Watch App (`android/wear/build.gradle.kts`):
```kotlin
signingConfigs {
    getByName("debug") {
        storeFile = file("../debug.keystore")  // Same shared keystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}
buildTypes {
    debug {
        signingConfig = signingConfigs.getByName("debug")
    }
}
```

### Step 3: Clean Build with Matching Certificates

```bash
./gradlew clean :app:assembleDebug :wear:assembleDebug
```

This ensures both APKs are signed with the **identical certificate** in a single build operation.

### Step 4: Complete Reinstallation

```bash
# Uninstall old apps (with old certificates)
adb -s <phone> shell pm uninstall com.firsttrial
adb -s <watch> shell pm uninstall com.firsttrial

# Install new apps (with matching certificates)
adb -s <phone> install app-debug.apk
adb -s <watch> install wear-debug.apk
```

## 📊 Verification of Success

### Before Fix:
```
02-17 19:47:47.690  W WearableService: Mismatched certificate
02-17 19:47:47.690  W WearableService: Failed to deliver message
02-17 00:36:14.599  E WEAR_DATA: 📡 Watch capability nodes: 0  ❌
```

### After Fix:
```
02-17 20:14:02.970  E WEAR_DATA: 🔥🔥🔥 MainActivity DataListener TRIGGERED! ✅
02-17 20:14:02.971  E WEAR_DATA: 🔥 MainActivity: Data: Hello from Watch ⌚
02-17 20:14:02.990  E WEAR_DATA: ✅ WearMessageService Message: Received
02-17 20:14:06.338  E WEAR_DATA: 📡 Watch capability nodes: 1  ✅
```

## 🎓 Key Learnings

### 1. Certificate Management in WearOS
- **Critical**: All companion apps (phone + watch) MUST share the same signing certificate
- This applies to both debug and release builds
- Google Play Services enforces this for security

### 2. Why Rebooting Didn't Help
- Google Play Services caches certificates in **persistent storage**, not just memory
- Rebooting clears memory but not the certificate database
- Only a complete app uninstall removes the cached certificate

### 3. Debug vs Release Signing
- **Debug builds**: Use debug.keystore (predictable, shared across development)
- **Release builds**: Use release keystore (must be carefully managed and backed up)
- **Play Store**: Google Play Signing handles this automatically for published apps

### 4. Multi-Module Android Projects
When you have multiple Android modules (app + wear):
```
project/
├── android/
│   ├── debug.keystore          ← Shared at root
│   ├── app/
│   │   └── build.gradle        ← Points to ../debug.keystore
│   └── wear/
│       └── build.gradle.kts    ← Points to ../debug.keystore
```

## 🛠️ Best Practices Going Forward

### For Development:
1. **Always** use a single, version-controlled `debug.keystore`
2. Build both modules together: `./gradlew :app:assembleDebug :wear:assembleDebug`
3. After changing certificates, **completely uninstall** old apps before reinstalling

### For Production:
1. Use the same **release keystore** for both phone and wear modules
2. Store keystore securely (never commit to git)
3. Consider using Google Play App Signing for automatic certificate management

### Certificate Verification Command:
```bash
# Check certificate fingerprint of an APK
keytool -printcert -jarfile app-debug.apk

# Both APKs should show identical:
# - SHA256 fingerprint
# - Certificate fingerprint
```

## 📝 Additional Context

### Why Multiple Listeners?
The implementation uses both `MainActivity` listeners and `WearMessageService`:

```kotlin
// MainActivity - Programmatic listeners (foreground)
messageClient.addListener { messageEvent -> ... }
dataClient.addListener { dataEventBuffer -> ... }

// WearMessageService - Background service
override fun onMessageReceived(messageEvent: MessageEvent) { ... }
override fun onDataChanged(dataEvents: DataEventBuffer) { ... }
```

**Reason**: Redundancy for reliability
- MainActivity listeners work when app is in foreground
- WearMessageService works when app is in background
- Dual approach (MessageClient + DataLayer) for maximum compatibility

### Why Capabilities Show After Fix?

Before: `📡 Watch capability nodes: 0`
After: `📡 Watch capability nodes: 1`

**Capabilities require trusted communication**. When certificates mismatched:
- Google Play Services rejected all WearOS traffic
- Capability registration failed silently
- Apps couldn't discover each other

After certificate fix:
- Capabilities properly registered
- Apps can discover each other
- Full bi-directional communication works

## 🚀 Running the Applications

### Prerequisites
1. Both devices (phone and watch) connected to development machine
2. USB debugging enabled on phone
3. ADB wireless debugging enabled on watch (paired via companion app)
4. Metro bundler ready to start

### Step-by-Step Startup Process

#### Step 1: Verify Device Connections
```bash
adb devices -l
```

Expected output:
```
List of devices attached
304d40b45cd5           device usb:0-1 product:sky_in model:23076RN4BI    (Phone)
192.168.0.106:34313    device product:wise6ulxx model:SM_R965F           (Watch via WiFi)
adb-RZAY6001JAZ-...    device product:wise6ulxx model:SM_R965F           (Watch via Bluetooth)
```

**Note**: You can use either WiFi or Bluetooth connection for the watch. WiFi (IP address) is generally more stable.

#### Step 2: Start Metro Bundler
Metro bundler serves the JavaScript bundle to the React Native app on the phone.

```bash
# From project root
npm start
```

Or run in background:
```bash
npm start &
```

Wait for output:
```
Welcome to Metro
  Fast - Scalable - Integrated

 LOGGING  Running Metro on port 8081
```

**What Metro does**:
- Bundles JavaScript code
- Enables hot reloading
- Serves source maps for debugging
- Required for React Native development

#### Step 3: Setup Metro Port Forwarding (Phone Only)
The phone needs to connect to Metro running on your development machine:

```bash
adb -s 304d40b45cd5 reverse tcp:8081 tcp:8081
```

**Output**: `8081`

**What this does**:
- Maps phone's localhost:8081 → computer's localhost:8081
- Allows React Native app to fetch JavaScript bundle
- Only needed for USB-connected devices

#### Step 4: Start Phone Application

```bash
adb -s 304d40b45cd5 shell am start -n com.firsttrial/.MainActivity
```

**Output**:
```
Starting: Intent { cmp=com.firsttrial/.MainActivity }
```

**What happens**:
1. MainActivity launches
2. React Native initializes
3. Connects to Metro bundler (via port 8081)
4. Downloads JavaScript bundle
5. Registers WearOS listeners (MessageClient + DataClient)
6. App UI appears with "Test Connection" button

#### Step 5: Start Watch Application

```bash
# Using WiFi connection
adb -s 192.168.0.106:34313 shell am start -n com.firsttrial/.presentation.MainActivity

# OR using Bluetooth connection
adb -s "adb-RZAY6001JAZ-mgaGVq._adb-tls-connect._tcp" shell am start -n com.firsttrial/.presentation.MainActivity
```

**Output**:
```
Starting: Intent { cmp=com.firsttrial/.presentation.MainActivity }
```

**What happens**:
1. Watch MainActivity launches
2. Compose UI renders
3. "Send Message" button appears
4. Connects to Google Play Services for WearOS communication

#### Step 6: Verify Application Startup

Check phone logs to confirm successful initialization:

```bash
adb -s 304d40b45cd5 logcat -d | grep -E "WEAR_DATA|ReactNative" | tail -20
```

**Expected logs**:
```
E WEAR_DATA: 🚀 MainActivity onCreate - Registering listeners...
E WEAR_DATA: ✅ MainActivity: Both listeners REGISTERED
E WEAR_DATA: 📱 MainActivity onResume - Listener should be active
I ReactNativeJS: Running "firstTrial" with {"rootTag":1,"initialProps":{},"fabric":true}
```

#### Step 7: Test Message Sending

1. On the **watch**, tap the **"Send Message"** button
2. Watch should show: "✅ Message sent successfully"
3. Phone should receive the message

Monitor phone logs in real-time:
```bash
adb -s 304d40b45cd5 logcat | grep WEAR_DATA
```

**Expected output when message received**:
```
E WEAR_DATA: 🔥🔥🔥 MainActivity listener TRIGGERED! Path: /text-path
E WEAR_DATA: 🔥 MainActivity: Message data: Hello from Watch ⌚
E WEAR_DATA: ✅ MainActivity: Sent to React Native
E WEAR_DATA: 🔥🔥🔥 WearMessageService: onMessageReceived called!
E WEAR_DATA: ✅ WearMessageService Message: Received: Hello from Watch ⌚
E WEAR_DATA: ✅ Successfully sent to React Native!
```

### 🔄 Quick Restart Commands

If you need to restart the apps during development:

#### Restart Phone App Only
```bash
adb -s 304d40b45cd5 shell am force-stop com.firsttrial && \
adb -s 304d40b45cd5 reverse tcp:8081 tcp:8081 && \
adb -s 304d40b45cd5 shell am start -n com.firsttrial/.MainActivity
```

#### Restart Watch App Only
```bash
adb -s 192.168.0.106:34313 shell am force-stop com.firsttrial && \
adb -s 192.168.0.106:34313 shell am start -n com.firsttrial/.presentation.MainActivity
```

#### Restart Both Apps
```bash
# Stop both
adb -s 304d40b45cd5 shell am force-stop com.firsttrial
adb -s 192.168.0.106:34313 shell am force-stop com.firsttrial

# Restart phone
adb -s 304d40b45cd5 reverse tcp:8081 tcp:8081
adb -s 304d40b45cd5 shell am start -n com.firsttrial/.MainActivity

# Restart watch
adb -s 192.168.0.106:34313 shell am start -n com.firsttrial/.presentation.MainActivity
```

#### Restart Metro Bundler
```bash
# Kill existing Metro
lsof -ti:8081 | xargs kill -9 2>/dev/null

# Start new Metro
npm start
```

### 🐛 Troubleshooting Common Startup Issues

#### Issue 1: "Metro Connection Failed"
**Symptoms**: Red screen on phone saying "Could not connect to development server"

**Solution**:
```bash
# Re-establish port forwarding
adb -s 304d40b45cd5 reverse tcp:8081 tcp:8081

# Restart app
adb -s 304d40b45cd5 shell am force-stop com.firsttrial
adb -s 304d40b45cd5 shell am start -n com.firsttrial/.MainActivity
```

#### Issue 2: Watch Disconnected
**Symptoms**: `adb devices` doesn't show watch

**Solution**:
```bash
# For WiFi: Reconnect
adb connect 192.168.0.106:34313

# For Bluetooth: Re-pair through companion app
# Settings → Developer Options → Wireless Debugging
```

#### Issue 3: App Crashes on Startup
**Symptoms**: App opens then immediately closes

**Solution**:
```bash
# Check crash logs
adb -s 304d40b45cd5 logcat -d | grep -E "AndroidRuntime|FATAL"

# Common fix: Clear app data
adb -s 304d40b45cd5 shell pm clear com.firsttrial

# Reinstall if needed
adb -s 304d40b45cd5 install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Issue 4: No Messages Received
**Symptoms**: Watch sends but phone doesn't receive

**Check**:
1. Both apps have matching certificates (see main solution above)
2. Apps are actually running (check with `adb shell ps | grep firsttrial`)
3. Google Play Services is running on both devices
4. Watch and phone are paired in Wear OS companion app

```bash
# Verify pairing
adb -s 304d40b45cd5 shell dumpsys activity service WearableService | grep -i "connected"

# Test connection from phone app
# Tap "🔍 Test Connection" button in phone app
# Should show: "✅ Connected nodes: 1"
```

### 📱 Development Workflow

**Typical development session**:

```bash
# 1. Start Metro (terminal 1)
npm start

# 2. Connect devices and setup (terminal 2)
adb devices
adb -s 304d40b45cd5 reverse tcp:8081 tcp:8081

# 3. Start both apps
adb -s 304d40b45cd5 shell am start -n com.firsttrial/.MainActivity
adb -s 192.168.0.106:34313 shell am start -n com.firsttrial/.presentation.MainActivity

# 4. Monitor logs (terminal 3, optional)
adb -s 304d40b45cd5 logcat | grep WEAR_DATA

# 5. Make code changes, save (Metro will auto-reload phone app)

# 6. For watch changes: rebuild and reinstall
cd android && ./gradlew :wear:assembleDebug
adb -s 192.168.0.106:34313 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

### 🔍 Monitoring and Debugging

#### Real-time Log Monitoring

**Phone logs** (all WEAR_DATA messages):
```bash
adb -s 304d40b45cd5 logcat | grep WEAR_DATA
```

**Watch logs** (message sending):
```bash
adb -s 192.168.0.106:34313 logcat | grep WEAR
```

**Filter for errors only**:
```bash
adb -s 304d40b45cd5 logcat | grep -E "WEAR_DATA|AndroidRuntime"
```

**Clear logs before testing**:
```bash
adb -s 304d40b45cd5 logcat -c
```

#### Check App Status

**Is app running?**
```bash
adb -s 304d40b45cd5 shell ps | grep firsttrial
```

**Current activity**:
```bash
adb -s 304d40b45cd5 shell dumpsys activity | grep "mFocusedActivity"
```

**Test connection from UI**:
1. Open phone app
2. Tap "🔍 Test Connection" button
3. Should display:
   ```
   ✅ Connected nodes: 1
     - Galaxy Watch6 Classic (1JAZ) (be02beed)
   📡 Watch capability nodes: 1
   ```

## 🚀 Result

✅ **Messages are now successfully transmitted from watch to phone**
✅ **Both MainActivity and WearMessageService receive messages**
✅ **Messages are forwarded to React Native successfully**
✅ **Capability discovery working (nodes: 1)**
✅ **No more certificate mismatch errors**

## 🔗 References

- [WearOS Data Layer Documentation](https://developer.android.com/training/wearables/data/data-layer)
- [Android App Signing](https://developer.android.com/studio/publish/app-signing)
- [Debugging WearOS Communication](https://developer.android.com/training/wearables/data/debugging)

---

**Date**: February 17, 2026
**Devices**: Samsung Watch 6 Classic (Wear OS 4+) → Redmi 12 5G (MIUI/HyperOS)
**Framework**: React Native (New Architecture) + Kotlin Compose (Wear OS)
