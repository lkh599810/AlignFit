from fastapi import FastAPI, File, HTTPException, UploadFile

from app.schemas.analysis_schema import AnalysisResponse
from app.services.annotation_service import annotate_image
from app.services.pose_service import INVALID_IMAGE, NO_POSE_DETECTED, extract_landmarks
from app.services.posture_analysis_service import analyze_posture
from app.services.recommendation_service import CAUTION_MESSAGE, generate_recommendations

app = FastAPI(title="AlignFit Backend")

_NOT_ANALYZABLE = "사진에서 자세를 인식하지 못해 이 항목은 분석하기 어렵습니다."

_NO_POSE_RESPONSE = AnalysisResponse(
    landmark_detected=False,
    landmark_count=0,
    shoulder_height_difference=0.0,
    hip_height_difference=0.0,
    simple_summary=(
        "사진에서 자세를 인식하지 못했습니다. "
        "밝고 선명한 전신 정면 사진으로 다시 시도해 주세요."
    ),
    recommendations=["분석을 위해 밝고 선명한 전신 자세 사진을 업로드해 주세요."],
    caution_message=CAUTION_MESSAGE,
    shoulder_direction="not_detected",
    shoulder_direction_summary=_NOT_ANALYZABLE,
    hip_direction="not_detected",
    hip_direction_summary=_NOT_ANALYZABLE,
    head_tilt_detected=False,
    head_tilt_direction="not_detected",
    head_tilt_summary=_NOT_ANALYZABLE,
    trunk_centerline_detected=False,
    trunk_tilt_direction="not_detected",
    trunk_centerline_summary=_NOT_ANALYZABLE,
    foot_direction_detected=False,
    foot_direction_status="not_detected",
    foot_direction_summary=_NOT_ANALYZABLE,
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
        foot_angle_diff=analysis["foot_direction_difference_degrees"],
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
        shoulder_direction=analysis["shoulder_direction"],
        shoulder_direction_summary=analysis["shoulder_direction_summary"],
        hip_direction=analysis["hip_direction"],
        hip_direction_summary=analysis["hip_direction_summary"],
        head_tilt_detected=analysis["head_tilt_detected"],
        head_tilt_angle_degrees=analysis["head_tilt_angle_degrees"],
        head_tilt_direction=analysis["head_tilt_direction"],
        head_tilt_summary=analysis["head_tilt_summary"],
        trunk_centerline_detected=analysis["trunk_centerline_detected"],
        trunk_centerline_angle_degrees=analysis["trunk_centerline_angle_degrees"],
        trunk_tilt_direction=analysis["trunk_tilt_direction"],
        trunk_centerline_summary=analysis["trunk_centerline_summary"],
        foot_direction_detected=analysis["foot_direction_detected"],
        left_foot_angle_degrees=analysis["left_foot_angle_degrees"],
        right_foot_angle_degrees=analysis["right_foot_angle_degrees"],
        foot_direction_difference_degrees=analysis["foot_direction_difference_degrees"],
        foot_direction_status=analysis["foot_direction_status"],
        foot_direction_summary=analysis["foot_direction_summary"],
    )
