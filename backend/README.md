# AlignFit Backend

AI-based posture analysis API with two flows:

- `POST /analyze/image` — posture **photo** → MediaPipe Pose landmarks → rule-based alignment analysis (shoulder, hip, head, trunk, feet) → homecare tips + annotated image.
- `POST /predict` — exercise **video** → MediaPipe PoseLandmarker → 69-d movement features → two trained PyTorch MLPs (movement quality, exercise type) → recommendations from a SQLite DB.

> This service provides simple posture analysis for educational purposes only. It is not a medical diagnostic tool.

## Requirements

- Python 3.10–3.11 (the Docker image uses 3.11)
- pip

## Local Setup

```bash
cd backend
python -m venv .venv && .venv/Scripts/activate      # Linux/macOS: source .venv/bin/activate
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements-dev.txt
uvicorn app.main:app --reload
python -m pytest -q tests                           # API contract tests
```

`GET /health` → `{"status": "ok", "video_model_loaded": true}`. If the video
model assets are missing or fail their checksum, the server still starts, the
image endpoint keeps working, `video_model_loaded` is `false` and `/predict`
returns 503.

The API will be available at `http://localhost:8000`.

Interactive docs: `http://localhost:8000/docs`

## Endpoint

### POST /analyze/image

Upload a body posture image and receive posture analysis and exercise recommendations.

**Request** - multipart form data:

| Field | Type | Description |
|-------|------|-------------|
| image | file | JPEG or PNG body posture photo |

**Response**:

```json
{
  "landmark_detected": true,
  "landmark_count": 33,
  "shoulder_height_difference": 0.0412,
  "hip_height_difference": 0.0187,
  "simple_summary": "Posture analysis complete. A shoulder height difference was detected; the shoulder line appears tilted.",
  "recommendations": [
    "Shoulder mobility exercise: perform shoulder rolls and cross-body arm stretches.",
    "Gentle upper back stretching: try a doorway chest stretch and thoracic spine rotation.",
    "Posture reset: stand tall, gently stack shoulders over hips, and retake the photo from the front."
  ],
  "caution_message": "This result is a simple posture analysis for educational purposes. It is not a medical diagnosis. If pain continues or worsens, consult a medical professional."
}
```

If no pose is detected:

```json
{
  "landmark_detected": false,
  "landmark_count": 0,
  "shoulder_height_difference": 0.0,
  "hip_height_difference": 0.0,
  "simple_summary": "No pose landmarks detected in the image.",
  "recommendations": [
    "Upload a clear, well-lit full-body posture photo for analysis."
  ],
  "caution_message": "This result is a simple posture analysis for educational purposes. It is not a medical diagnosis. If pain continues or worsens, consult a medical professional."
}
```

If the uploaded file cannot be decoded as an image, the API returns `400 Bad Request`:

```json
{
  "detail": "Invalid image file. Please upload a valid JPEG or PNG image."
}
```

## Test with curl

```bash
curl -X POST http://localhost:8000/analyze/image \
  -F "image=@/path/to/posture_photo.jpg"
```

## Video movement-quality evaluation (MobiPhysio)

Production inference lives in `app/services/ml_quality_service.py` and is
self-contained (it does not import the research `src/` package). It ports the
MobiPhysio feature extractor verbatim: PoseLandmarker (Tasks API, lite, IMAGE
mode) on every 2nd frame at original resolution → 15 joint angles ×
mean/std/range/max + 9 symmetry features = 69-d → two MLPs:

| File (`backend/models/`) | Role |
|---|---|
| `mobi_mlp_binary_correctness.pt` | movement quality: correct / incorrect |
| `mobi_mlp_exercise_9class.pt` | exercise class E01–E09 |
| `pose_landmarker_lite.task` | MediaPipe pose model (Google, Apache-2.0) |
| `SHA256SUMS` | checked before loading (`torch.load` unpickles, so only trusted files load) |

The predicted exercise maps to a body region + posture pattern, looked up in
`backend/data/recommendation.sqlite` (read-only copy of the research
`db/recommendation.sqlite`). Checkpoints are the original training outputs; they
were not retrained or replaced. Override locations with `ALIGNFIT_MODELS_DIR` /
`ALIGNFIT_RECOMMENDATION_DB`.

### POST /predict

| Field | Type | Description |
|-------|------|-------------|
| video | file | mp4 / mov / m4v / avi / mkv / webm / 3gp, ≤ 100MB, ≤ 60s |

Limits: `ALIGNFIT_MAX_VIDEO_MB` (100), `ALIGNFIT_MAX_VIDEO_SECONDS` (60),
`ALIGNFIT_MAX_IMAGE_MB` (20, for `/analyze/image`).

| Status | When |
|---|---|
| 200 | analysed; `pose_detected: false` with a retry message if fewer than 3 frames had a pose |
| 400 | empty, unreadable/corrupt video, or longer than the limit |
| 413 | file larger than the limit |
| 415 | not a video (extension and content type both wrong) |
| 503 | model assets missing / checksum mismatch / load failure |

Errors are `{"detail": "<Korean message>"}`. Processing time is roughly
1.5–2× the video length on a desktop CPU (≈ 65s for a 38s 1080p clip).

```bash
curl -X POST http://localhost:8000/predict -F "video=@/path/to/exercise.mp4"
```

```json
{
  "pose_detected": true,
  "frames_used": 570,
  "movement_quality": {"label": "incorrect", "status": "needs_attention", "confidence": 0.565},
  "predicted_exercise": {
    "exercise_id": "E01", "exercise_name": "Abduction", "exercise_name_ko": "어깨 벌림 동작",
    "confidence": 0.638, "target_region": "upper_shoulder", "target_region_ko": "어깨/견갑 부위",
    "posture_pattern": "shoulder_asymmetry", "posture_pattern_ko": "좌우 어깨 움직임 비대칭 가능성"
  },
  "message": "이번 분석에서 ... 이는 의학적 진단이 아닙니다.",
  "recommendations": [{"exercise_name": "Shoulder blade squeeze", "related_body_part": "...",
                       "reason": "...", "cautions": "...", "youtube_query": "...", "verify_citation": "..."}],
  "caution_message": "This is a simple posture/movement analysis for educational purposes, not a medical diagnosis. ..."
}
```

### GET /demo

Browser test page for `/predict` (`http://localhost:8000/demo`). The Android app
uses `/predict` directly with native screens.

## Docker

```bash
# Build (context = backend/; models and DB are copied into the image)
docker build -t alignfit-backend .

# Run (non-root uid 10001; HEALTHCHECK polls /health)
docker run -p 8000:8000 alignfit-backend
```
