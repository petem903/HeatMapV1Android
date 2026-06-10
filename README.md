# HeatMapV1Android

Android thermal viewer for older controller tablets, built for live drone video instead of simulation.

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

### Local Build

```bat
gradlew.bat assembleRelease
```

or

```bat
build_release.bat
```

## Configuration Notes

- The default stream URL is configured in-app and can be changed in Settings.
- Local signing material is intentionally not included in this repository.
- Proprietary vendor SDK drop-ins, if used, should stay local and should not be committed.

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
- Public-repo cleanup docs: done
- More OpenCV tooling: still open
