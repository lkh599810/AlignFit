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

## Docker

```bash
# Build
docker build -t alignfit-backend .

# Run
docker run -p 8000:8000 alignfit-backend
```
