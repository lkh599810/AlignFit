from fastapi import FastAPI, File, HTTPException, UploadFile

from app.schemas.analysis_schema import AnalysisResponse
from app.services.annotation_service import annotate_image
from app.services.pose_service import INVALID_IMAGE, NO_POSE_DETECTED, extract_landmarks
from app.services.posture_analysis_service import analyze_posture
from app.services.recommendation_service import CAUTION_MESSAGE, generate_recommendations

app = FastAPI(title="AlignFit Backend")

_NO_POSE_RESPONSE = AnalysisResponse(
    landmark_detected=False,
    landmark_count=0,
    shoulder_height_difference=0.0,
    hip_height_difference=0.0,
    simple_summary=(
        "이미지에서 자세 landmark를 감지하지 못했습니다. "
        "밝고 선명한 전신 정면 사진을 사용해 주세요."
    ),
    recommendations=["분석을 위해 밝고 선명한 전신 자세 사진을 업로드해 주세요."],
    caution_message=CAUTION_MESSAGE,
)


@app.post("/analyze/image", response_model=AnalysisResponse)
async def analyze_image(image: UploadFile = File(...)):
    image_bytes = await image.read()
    landmarks = extract_landmarks(image_bytes)

    if landmarks == INVALID_IMAGE:
        raise HTTPException(
            status_code=400,
            detail="유효하지 않은 이미지 파일입니다. JPEG 또는 PNG 형식의 이미지를 업로드해 주세요.",
        )

    if landmarks == NO_POSE_DETECTED:
        return _NO_POSE_RESPONSE

    analysis = analyze_posture(landmarks)
    recommendations = generate_recommendations(
        analysis["shoulder_height_difference"],
        analysis["hip_height_difference"],
        analysis["shoulder_slope"],
        analysis["hip_slope"],
        analysis["shoulder_hip_center_offset"],
        analysis["low_visibility_landmarks"],
        head_tilt_score=analysis["head_tilt_score"],
        head_center_offset=analysis["head_center_offset"],
        foot_angle_diff=analysis["foot_angle_diff"],
    )
    annotated_b64 = annotate_image(image_bytes, landmarks, analysis)

    return AnalysisResponse(
        landmark_detected=True,
        landmark_count=len(landmarks),
        shoulder_height_difference=analysis["shoulder_height_difference"],
        hip_height_difference=analysis["hip_height_difference"],
        simple_summary=analysis["simple_summary"],
        recommendations=recommendations["exercises"],
        caution_message=recommendations["caution_message"],
        annotated_image_base64=annotated_b64 or None,
    )
