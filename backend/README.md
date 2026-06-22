# AlignFit Backend

AI-based body posture analysis API. Receives a posture image, detects pose landmarks via MediaPipe Pose, analyzes simple shoulder and hip alignment signals, and returns exercise recommendations.

> This service provides simple posture analysis for educational purposes only. It is not a medical diagnostic tool.

## Requirements

- Python 3.11+
- pip

## Local Setup

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload
```

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

## ML movement-quality demo (MobiPhysio)

A second, ML-based flow analyzes a short **exercise video** (not a single image).
It ports the MobiPhysio Colab feature extractor (`mobiphysio_colab_extract_v2.py`,
Cell 4): video → MediaPipe PoseLandmarker (Tasks API, lite) → a 69-dim summary
feature (15 joint angles × mean/std/range/max + 9 symmetry features). Two trained
MLPs then run on it:

- `models/mobi_mlp_binary_correctness.pt` → movement quality (correct / incorrect)
- `models/mobi_mlp_exercise_9class.pt` → exercise class (E01–E09)

The predicted exercise is mapped to a body region + posture pattern and linked to
homecare items via the shared recommendation DB (`src.recommendation_db`), not the
image flow's hard-coded rules. This reuses the repo-root `src/` ML package, so run
from the repo root (`C:\AlignFit-intelligent`) with both backend and ML deps
(including `torch`) installed.

> On first call, the MediaPipe pose model (`pose_landmarker_lite.task`, ~10 MB) is
> downloaded to `models/` automatically.

### POST /predict

Upload an exercise video; receive the ML prediction and recommendations.

| Field | Type | Description |
|-------|------|-------------|
| video | file | Short exercise video (mp4/avi/mov/mkv) |

```bash
curl -X POST http://localhost:8000/predict \
  -F "video=@/path/to/exercise.mp4"
```

Example response:

```json
{
  "pose_detected": true,
  "frames_used": 570,
  "movement_quality": { "label": "incorrect", "status": "needs_attention", "confidence": 0.565 },
  "predicted_exercise": {
    "exercise_id": "E01", "exercise_name": "Abduction", "confidence": 0.638,
    "target_region": "upper_shoulder", "posture_pattern": "shoulder_asymmetry"
  },
  "note": "이번 분석에서 ... 이는 의학적 진단이 아닙니다.",
  "recommendations": [ { "exercise_name": "Shoulder blade squeeze", "...": "..." } ],
  "caution_message": "This is a simple posture/movement analysis for educational purposes, not a medical diagnosis. ..."
}
```

### GET /demo

A minimal HTML page (`http://localhost:8000/demo`) to upload a video in the
browser and view the prediction + recommendations.

## Docker

```bash
# Build
docker build -t alignfit-backend .

# Run
docker run -p 8000:8000 alignfit-backend
```
