@echo off
setlocal ENABLEDELAYEDEXPANSION

set "SDK="

REM 1) Optional explicit arg: gradle-build.bat C:\path\to\Sdk
if not "%~1"=="" set "SDK=%~1"

REM 2) Env vars
if "%SDK%"=="" if not "%ANDROID_SDK_ROOT%"=="" set "SDK=%ANDROID_SDK_ROOT%"
if "%SDK%"=="" if not "%ANDROID_HOME%"=="" set "SDK=%ANDROID_HOME%"

REM 3) Derive from adb on PATH
if "%SDK%"=="" (
  for /f "delims=" %%A in ('where adb 2^>nul') do (
    set "ADB=%%~fA"
    goto :got_adb
  )
)

:got_adb
if "%SDK%"=="" if not "%ADB%"=="" (
  for %%P in ("%ADB%") do set "SDK=%%~dpP.."
)

if "%SDK%"=="" (
  echo [ERROR] Could not find Android SDK.
  echo.
  echo Try one of these:
  echo   gradle-build.bat C:\Users\YourUser\AppData\Local\Android\Sdk
  echo   set ANDROID_SDK_ROOT=C:\Users\YourUser\AppData\Local\Android\Sdk
  echo.
  exit /b 1
)

REM Normalize path
for %%S in ("%SDK%") do set "SDK=%%~fS"

if not exist "%SDK%\platform-tools\adb.exe" (
  echo [ERROR] SDK path looks wrong: %SDK%
  echo Expected: %SDK%\platform-tools\adb.exe
  exit /b 1
)

set "ANDROID_SDK_ROOT=%SDK%"
set "ANDROID_HOME=%SDK%"
echo [INFO] Using Android SDK: %SDK%

if not exist ".\gradlew.bat" (
  echo [ERROR] Run this from repo root where gradlew.bat exists.
  exit /b 1
)

call .\gradlew.bat assembleRelease assembleDebug
exit /b %ERRORLEVEL%
