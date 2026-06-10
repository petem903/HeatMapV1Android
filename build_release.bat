@echo off
REM Build HeatMapV1Android release APK using a local Gradle install
setlocal enabledelayedexpansion

set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=!JAVA_HOME!\bin;!PATH!
set GRADLE_DIR=%LOCALAPPDATA%\gradle-8.4
set GRADLE_BIN=!GRADLE_DIR!\gradle-8.4\bin\gradle.bat
set HEATMAP_USE_MIRROR=1

echo Verifying Java...
"%JAVA_HOME%\bin\java.exe" -version
echo.

if not exist "!GRADLE_BIN!" (
    echo Downloading and extracting Gradle 8.4 ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $zip=$env:TEMP+'\gradle-8.4-bin.zip'; if(-not (Test-Path $zip)){ (New-Object System.Net.WebClient).DownloadFile('https://services.gradle.org/distributions/gradle-8.4-bin.zip',$zip) }; Expand-Archive -Path $zip -DestinationPath $env:LOCALAPPDATA\gradle-8.4 -Force"
)

if not exist "!GRADLE_BIN!" (
    echo FAILED: Gradle not available at !GRADLE_BIN!
    exit /b 1
)

echo.
echo Building release APK...
call "!GRADLE_BIN!" assembleRelease --no-daemon --refresh-dependencies --stacktrace --info > build_diag.log 2>&1
type build_diag.log | findstr /C:"Caused by" /C:"Exception" /C:"PKIX" /C:"certificate" /C:"SSL" /C:"valid certification" /C:"Could not" /C:"BUILD"

echo.
if exist app\build\outputs\apk\release\app-release.apk (
    echo SUCCESS: app\build\outputs\apk\release\app-release.apk
    dir app\build\outputs\apk\release\app-release.apk
) else (
    echo FAILED: APK not found
)

endlocal
