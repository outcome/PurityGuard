param(
  [string]$SdkPath
)

$ErrorActionPreference = 'Stop'

function Resolve-SdkPath {
  param([string]$InputPath)

  if ($InputPath -and (Test-Path $InputPath)) { return (Resolve-Path $InputPath).Path }
  if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return (Resolve-Path $env:ANDROID_SDK_ROOT).Path }
  if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return (Resolve-Path $env:ANDROID_HOME).Path }

  $adb = (Get-Command adb -ErrorAction SilentlyContinue)?.Source
  if ($adb) {
    return (Resolve-Path (Join-Path (Split-Path $adb -Parent) '..')).Path
  }

  return $null
}

$sdk = Resolve-SdkPath -InputPath $SdkPath
if (-not $sdk) {
  Write-Host '[ERROR] Could not find Android SDK.' -ForegroundColor Red
  Write-Host 'Try: .\gradle-build.ps1 -SdkPath "C:\Users\YourUser\AppData\Local\Android\Sdk"'
  exit 1
}

if (-not (Test-Path (Join-Path $sdk 'platform-tools\adb.exe'))) {
  Write-Host "[ERROR] SDK path looks wrong: $sdk" -ForegroundColor Red
  exit 1
}

$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
Write-Host "[INFO] Using Android SDK: $sdk"

if (-not (Test-Path '.\gradlew.bat')) {
  Write-Host '[ERROR] Run this from repo root where gradlew.bat exists.' -ForegroundColor Red
  exit 1
}

& .\gradlew.bat assembleRelease assembleDebug
exit $LASTEXITCODE
