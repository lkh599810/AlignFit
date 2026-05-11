from fastapi import FastAPI, File, UploadFile

from app.schemas.analysis_schema import AnalysisResponse
from app.services.pose_service import extract_landmarks
from app.services.posture_analysis_service import analyze_posture
from app.services.recommendation_service import CAUTION_MESSAGE, generate_recommendations

app = FastAPI(title="AlignFit Backend")

_NO_POSE_RESPONSE = AnalysisResponse(
    landmark_detected=False,
    landmark_count=0,
    shoulder_height_difference=0.0,
    hip_height_difference=0.0,
    simple_summary="No pose landmarks detected in the image.",
    recommendations=[],
    caution_message=CAUTION_MESSAGE,
)


@app.post("/analyze/image", response_model=AnalysisResponse)
async def analyze_image(image: UploadFile = File(...)):
    image_bytes = await image.read()
    landmarks = extract_landmarks(image_bytes)

    if landmarks is None:
        return _NO_POSE_RESPONSE

    analysis = analyze_posture(landmarks)
    recommendations = generate_recommendations(
        analysis["shoulder_height_difference"],
        analysis["hip_height_difference"],
    )

    return AnalysisResponse(
        landmark_detected=True,
        landmark_count=len(landmarks),
        shoulder_height_difference=analysis["shoulder_height_difference"],
        hip_height_difference=analysis["hip_height_difference"],
        simple_summary=analysis["simple_summary"],
        recommendations=recommendations["exercises"],
        caution_message=recommendations["caution_message"],
    )
