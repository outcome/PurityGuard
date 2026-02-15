# PurityGuard

PurityGuard is an **Android adult-content blocker** that uses local VPN DNS enforcement to block pornographic and explicit domains before they load.

## What it does
- Blocks adult-content domains at DNS level on-device
- Lets you choose between multiple DNS filtering providers:
  - Cloudflare Family (`1.1.1.3`, `1.0.0.3`)
  - CleanBrowsing Adult Filter (`185.228.168.10`, `185.228.169.11`)
  - OpenDNS FamilyShield (`208.67.222.123`, `208.67.220.123`)
- Provides a custom blocked-page experience with motivational content
- Supports IPv6-aware DNS handling

## Screenshots
<p>
  <img src="media/home.jpg" alt="Home" width="220" />
  <img src="media/blocked.jpg" alt="Blocked screen" width="220" />
  <img src="media/browser-notice.jpg" alt="Browser notice" width="220" />
</p>

## Download
- https://github.com/outcome/PurityGuard/releases/latest

## Build Guide (Windows)

### 1) Install prerequisites
- Install **Java 17** (JDK)
- Install **Android SDK** (full SDK, not just `adb` from scrcpy)
  - Easiest method: install Android Studio, then open **SDK Manager** and install:
    - Android SDK Platform
    - Android SDK Build-Tools
    - Android SDK Platform-Tools

### 2) Find your SDK path
Typical SDK location on Windows:

```text
C:\Users\Administrator\AppData\Local\Android\Sdk
```

Your SDK root should contain folders like:
- `platform-tools`
- `build-tools`
- `platforms`

### 3) Build from repo root
Use the helper script with explicit SDK path:

```bat
gradle-build.bat C:\Users\Administrator\AppData\Local\Android\Sdk
```

If your SDK is elsewhere, replace that path with your actual SDK root.

### 4) Output APKs
After a successful build:
- Release APK: `app\build\outputs\apk\release\app-release.apk`
- Debug APK: `app\build\outputs\apk\debug\app-debug.apk`

## Alternative build command (without helper script)
```bat
set "ANDROID_SDK_ROOT=C:\Users\Administrator\AppData\Local\Android\Sdk" && set "ANDROID_HOME=%ANDROID_SDK_ROOT%" && .\gradlew.bat assembleRelease assembleDebug
```

## Common issue
If you get **"SDK location not found"**, your SDK path is wrong or incomplete. Make sure you point to the full Android SDK root (the folder that contains `platform-tools`, `build-tools`, and `platforms`).
