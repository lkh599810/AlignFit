# AlignFit Android

Minimal Android MVP for the AlignFit posture analysis app.

## Requirements

- Android Studio Ladybug (2024.2) or later
- JDK 17
- Android SDK 35 (compileSdk)
- minSdk 24 (Android 7.0)

## How to open

1. Open Android Studio.
2. Choose **Open** and select the `android/` folder.
3. Let Gradle sync finish.

## Backend URL (build flavors)

The API base URL comes from the build flavor (`app/build.gradle.kts`); nothing is
hard-coded in Kotlin. Pick the variant in Android Studio's *Build Variants* panel.

| Variant | API | Package | Cleartext HTTP |
|---|---|---|---|
| `prodDebug` / `prodRelease` (default) | `https://alignfit-api.leekanghyun.co.kr/` | `com.alignfit.app` | blocked |
| `localDebug` | `http://127.0.0.1:8000/` | `com.alignfit.app.local` ("AlignFit (local)") | allowed |

`local` installs next to the production app instead of replacing it.

- USB phone + backend on the PC: `adb reverse tcp:8000 tcp:8000`, then run `localDebug`.
- Emulator: `./gradlew assembleLocalDebug -Palignfit.localApiUrl=http://10.0.2.2:8000/`
- Same Wi-Fi: `-Palignfit.localApiUrl=http://192.168.x.x:8000/`

Backend: `cd backend && uvicorn app.main:app --host 0.0.0.0 --port 8000`.

Command-line build: `./gradlew assembleProdDebug` (APK in `app/build/outputs/apk/prod/debug/`).
`prodRelease` is unsigned until a signing config is added.

## How to use

1. Launch the app on an emulator or device.
2. Tap **Select Image** and pick a body posture photo from the gallery.
3. Tap **Analyze Image** to upload the image to the backend.
4. The posture analysis result is displayed on screen.

## Features

- Gallery image selection
- Multipart upload to `POST /analyze/image`
- Displays: landmark count, shoulder/hip height differences, summary, recommendations, caution note

## Not implemented (future scope)

- Camera capture
- Login / authentication
- Local database
- LLM integration
