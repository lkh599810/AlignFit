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

## Backend URL

The app connects to the FastAPI backend at:

```
http://10.0.2.2:8000/
```

`10.0.2.2` is the Android emulator's alias for the host machine's `localhost`.

If you run the app on a **physical device**, update `BASE_URL` in:

```
app/src/main/java/com/alignfit/app/network/retrofit_client.kt
```

Replace `10.0.2.2` with your machine's local IP address (e.g., `192.168.x.x`).

Make sure the backend is running first:

```
cd C:\AlignFit\backend
uvicorn app.main:app --reload
```

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
