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

## Building from source
This repo uses the normal Android/Gradle project structure.

### Important files
- `settings.gradle.kts` → declares project modules (includes `:app`)
- `build.gradle.kts` → root Gradle configuration
- `gradle.properties` → Gradle build options
- `app/build.gradle.kts` → Android app module config (SDK/version/build types/dependencies)

### Build commands
From repo root:

```bash
./gradlew assembleRelease assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleRelease assembleDebug
```

Output APKs:
- `app/build/outputs/apk/release/`
- `app/build/outputs/apk/debug/`
