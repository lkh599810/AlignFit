import math

VISIBILITY_THRESHOLD = 0.6
FACE_VISIBILITY_THRESHOLD = 0.5

KEY_LANDMARKS = {
    "왼쪽 어깨": 11,
    "오른쪽 어깨": 12,
    "왼쪽 골반": 23,
    "오른쪽 골반": 24,
}

SLOPE_THRESHOLD = 0.08
CENTER_OFFSET_THRESHOLD = 0.04
HEAD_TILT_THRESHOLD = 0.015
HEAD_CENTER_OFFSET_THRESHOLD = 0.05
FOOT_DIFF_THRESHOLD = 15.0


def _slope(left_landmark, right_landmark) -> float:
    x_difference = right_landmark.x - left_landmark.x
    if abs(x_difference) < 0.0001:
        return 0.0
    return (right_landmark.y - left_landmark.y) / x_difference


def _center_x(left_landmark, right_landmark) -> float:
    return (left_landmark.x + right_landmark.x) / 2


def _center_y(left_landmark, right_landmark) -> float:
    return (left_landmark.y + right_landmark.y) / 2


def _is_visible(landmark, threshold: float) -> bool:
    return getattr(landmark, "visibility", 1.0) >= threshold


def _low_visibility_landmarks(landmarks) -> list[str]:
    low_visibility = []
    for name, index in KEY_LANDMARKS.items():
        visibility = getattr(landmarks[index], "visibility", None)
        if visibility is not None and visibility < VISIBILITY_THRESHOLD:
            low_visibility.append(name)
    return low_visibility


def _diff_level(diff: float) -> str | None:
    """Returns Korean adverb for the given normalized difference value, or None if negligible."""
    if diff < 0.005:
        return None
    elif diff < 0.015:
        return "약간"
    elif diff < 0.03:
        return "뚜렷하게"
    else:
        return "크게"


def _head_tilt_score(landmarks) -> float | None:
    left_eye = landmarks[2]
    right_eye = landmarks[5]
    if _is_visible(left_eye, FACE_VISIBILITY_THRESHOLD) and _is_visible(right_eye, FACE_VISIBILITY_THRESHOLD):
        return abs(left_eye.y - right_eye.y)

    left_ear = landmarks[7]
    right_ear = landmarks[8]
    if _is_visible(left_ear, FACE_VISIBILITY_THRESHOLD) and _is_visible(right_ear, FACE_VISIBILITY_THRESHOLD):
        return abs(left_ear.y - right_ear.y)

    return None


def _head_center_offset(landmarks, shoulder_center_x: float) -> float | None:
    nose = landmarks[0]
    if not _is_visible(nose, FACE_VISIBILITY_THRESHOLD):
        return None
    return abs(nose.x - shoulder_center_x)


def _foot_angle(heel, foot_index) -> float | None:
    if not _is_visible(heel, VISIBILITY_THRESHOLD) or not _is_visible(foot_index, VISIBILITY_THRESHOLD):
        return None
    dx = foot_index.x - heel.x
    dy = foot_index.y - heel.y
    return math.degrees(math.atan2(dx, dy))


def analyze_posture(landmarks) -> dict:
    left_shoulder = landmarks[11]
    right_shoulder = landmarks[12]
    left_hip = landmarks[23]
    right_hip = landmarks[24]

    shoulder_diff = abs(left_shoulder.y - right_shoulder.y)
    hip_diff = abs(left_hip.y - right_hip.y)
    shoulder_slope = _slope(left_shoulder, right_shoulder)
    hip_slope = _slope(left_hip, right_hip)
    shoulder_center_x = _center_x(left_shoulder, right_shoulder)
    shoulder_center_y = _center_y(left_shoulder, right_shoulder)
    hip_center_x = _center_x(left_hip, right_hip)
    hip_center_y = _center_y(left_hip, right_hip)
    shoulder_hip_center_offset = abs(shoulder_center_x - hip_center_x)

    head_tilt_score = _head_tilt_score(landmarks)
    head_center_offset = _head_center_offset(landmarks, shoulder_center_x)

    left_foot_angle = _foot_angle(landmarks[29], landmarks[31])
    right_foot_angle = _foot_angle(landmarks[30], landmarks[32])
    foot_angle_diff = (
        abs(left_foot_angle - right_foot_angle)
        if left_foot_angle is not None and right_foot_angle is not None
        else None
    )

    low_visibility = _low_visibility_landmarks(landmarks)

    summary_parts = []

    shoulder_level = _diff_level(shoulder_diff)
    if shoulder_level:
        summary_parts.append(f"올려주신 사진에서는 어깨 높이 차이가 {shoulder_level} 관찰됩니다.")

    hip_level = _diff_level(hip_diff)
    if hip_level:
        summary_parts.append(f"올려주신 사진에서는 골반 높이 차이가 {hip_level} 관찰됩니다.")

    if shoulder_hip_center_offset > CENTER_OFFSET_THRESHOLD:
        summary_parts.append(
            "어깨 중심과 골반 중심이 약간 어긋나 보여 몸통 중심선이 한쪽으로 치우쳐 보일 수 있습니다."
        )

    if head_tilt_score is not None and head_tilt_score > HEAD_TILT_THRESHOLD:
        summary_parts.append("얼굴 landmark 기준으로 고개 기울어짐 패턴이 약간 보일 수 있습니다.")

    if head_center_offset is not None and head_center_offset > HEAD_CENTER_OFFSET_THRESHOLD:
        summary_parts.append("머리 중심이 어깨 중심에서 약간 벗어나 보입니다.")

    if foot_angle_diff is not None and foot_angle_diff > FOOT_DIFF_THRESHOLD:
        summary_parts.append(
            "발 방향 추정에서 좌우 차이가 관찰됩니다. "
            "발 방향은 사진상 보이는 landmark 기준의 대략적인 추정입니다."
        )

    if not summary_parts:
        summary = "올려주신 사진으로 판단해보자면 주요 어깨, 골반 높이 차이가 관찰되지 않았습니다."
    else:
        summary = " ".join(summary_parts)

    if low_visibility:
        summary += " 일부 landmark의 신뢰도가 낮아 분석 결과가 제한적일 수 있습니다."

    summary += " 이 분석은 단일 2D 이미지 기반 참고 분석이며 모든 자세 패턴을 평가하지는 않습니다."

    return {
        "shoulder_height_difference": round(shoulder_diff, 4),
        "hip_height_difference": round(hip_diff, 4),
        "shoulder_slope": round(shoulder_slope, 4),
        "hip_slope": round(hip_slope, 4),
        "shoulder_center_x": round(shoulder_center_x, 4),
        "shoulder_center_y": round(shoulder_center_y, 4),
        "hip_center_x": round(hip_center_x, 4),
        "hip_center_y": round(hip_center_y, 4),
        "shoulder_hip_center_offset": round(shoulder_hip_center_offset, 4),
        "head_tilt_score": round(head_tilt_score, 4) if head_tilt_score is not None else None,
        "head_center_offset": round(head_center_offset, 4) if head_center_offset is not None else None,
        "left_foot_angle": round(left_foot_angle, 2) if left_foot_angle is not None else None,
        "right_foot_angle": round(right_foot_angle, 2) if right_foot_angle is not None else None,
        "foot_angle_diff": round(foot_angle_diff, 2) if foot_angle_diff is not None else None,
        "low_visibility_landmarks": low_visibility,
        "simple_summary": summary,
    }
