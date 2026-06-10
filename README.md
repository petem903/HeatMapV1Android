# HeatMapV1Android
<img width="1254" height="1254" alt="Image" src="https://github.com/user-attachments/assets/7edf97da-586f-4732-a2b0-c0cd783ac558" />


Android thermal viewer for older controller tablets, built for live drone video instead of simulation.

## Quick Try

- Prebuilt APK: `releases/HeatMapV1Android-v2.1.apk`
- Target hardware: older Android 5.1/6.0 controller tablets
- Current public release: `v2.1`

The APK is stored with Git LFS because it is larger than GitHub's normal web file limit.

If you only want to test the app, install the APK from the `releases/` folder.

## What It Does

- Runs on older Android controller hardware with `minSdk 22`.
- Connects to a live drone or ground-station video stream with Android `MediaPlayer`.
- Samples video frames and maps brightness into a configurable pseudo-thermal range.
- Shows spot, line, area, hot, and cold measurements over the live view.
- Supports palette changes, blend modes, capture, and session review.

## Current Direction

This app is meant to be a practical field viewer on the controller tablet.

- Real video feed only.
- No simulated thermal source.
- No flight control in-app.
- Designed to work alongside the controller's existing sticks and flight UI.

## Important Limitation

The current pipeline is not true radiometric thermal decoding.
It maps visible-frame luminance to a temperature range chosen in Settings.

That means the app is useful as a live inspection and measurement aid, but it still needs deeper camera or SDK integration for accurate thermal science.

## Help Wanted

Contributions are especially useful in these areas:

- Better RTSP and MJPEG compatibility on older Android 5.1/6.0 devices.
- OpenCV-based hot-spot segmentation and contour extraction.
- Edge enhancement, denoising, CLAHE, and contrast normalization for low-detail feeds.
- Multi-frame object tracking and measurement stability.
- Better temperature calibration workflows.
- Optional vendor camera integration when a legal SDK path exists.
- UI polish for field use on controller-sized touchscreens.

## Tech Stack

- Kotlin
- Jetpack Compose Material 3
- OpenCV 4.9
- Ktor
- Gson
- ZXing
- iText 7
- Android SDK 34

## Build

### Requirements

- Android Studio with Android SDK 34
- JDK 17
- Git LFS if you want the checked-in APK file from `releases/`

### Standard Public Build

This repository now defaults to the normal public Android repositories:

- Google Maven
- Maven Central
- Gradle Plugin Portal

Use either of these commands:

```bat
gradlew.bat assembleRelease
```

or

```bat
gradlew.bat assembleDebug
```

If you cloned without Git LFS, run:

```bat
git lfs install
git lfs pull
```

### Local Build

```bat
gradlew.bat assembleRelease
```

or

```bat
build_release.bat
```

`build_release.bat` is mainly for the original locked-down environment where Gradle traffic had to go through `mirror_proxy.ps1`.

### Public Build Notes

- `gradle-wrapper.jar` is committed so the wrapper works out of the box.
- Local signing files are not included; if no release keystore is configured the app can still be built locally with debug signing.
- The repo includes helper scripts for downloading Android SDK pieces on Windows if needed.
- The repo does not require any proprietary vendor SDK to compile.

## Configuration Notes

- The default stream URL is configured in-app and can be changed in Settings.
- Local signing material is intentionally not included in this repository.
- Optional local vendor SDK drop-ins, if used later, should stay local and should not be committed.
- In the original restricted environment, set `HEATMAP_USE_MIRROR=1` and run `mirror_proxy.ps1` before Gradle commands.

## Repository Contents

- `releases/HeatMapV1Android-v2.1.apk` — installable build for testers, stored with Git LFS
- `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` — wrapper files for standard builds
- `download_sdk.ps1` and related scripts — Windows helpers for SDK/bootstrap setup
- Full Kotlin source, resources, tests, and build scripts

## For Contributors

If you open an issue or PR, include:

- Device model
- Android version
- Stream type and URL format
- Whether the feed is RTSP, HTTP, MJPEG, or vendor-specific
- A short screen recording or screenshots if the problem is visual

## Roadmap Candidates

- Radiometric camera support
- Better export and reporting tools
- Batch capture review
- OpenCV measurement helpers
- Live threshold masks
- Hot-object alarms
- Perspective-aware area measurement

## Status

Current release line: `v2.1`

- Parse-error fix for older controller tablets: done
- Public-repo packaging for testers and builders: in repo
- More OpenCV tooling: still open
