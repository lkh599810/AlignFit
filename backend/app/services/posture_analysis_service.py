import math

VISIBILITY_THRESHOLD = 0.6
FACE_VISIBILITY_THRESHOLD = 0.5
# A landmark counts as detected for metric analysis when visibility >= 0.5.
DETECTION_VISIBILITY_THRESHOLD = 0.5

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

HEAD_TILT_DEGREE_THRESHOLD = 3.0
TRUNK_TILT_MILD_DEGREES = 3.0
TRUNK_TILT_NOTICEABLE_DEGREES = 7.0
FOOT_DIFF_MILD_DEGREES = 8.0
FOOT_DIFF_NOTICEABLE_DEGREES = 15.0

# Direction enums shared with the API schema.
DIRECTION_NOT_DETECTED = "not_detected"
DIRECTION_BALANCED = "balanced"


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


def _is_detected(landmark) -> bool:
    visibility = getattr(landmark, "visibility", None)
    if visibility is None:
        # Fallback: rely on coordinate availability when visibility is missing.
        return landmark.x is not None and landmark.y is not None
    return visibility >= DETECTION_VISIBILITY_THRESHOLD


def _person_left_sign(landmarks) -> int:
    """Returns +1 if the person's left side is toward +x in the image,
    -1 if toward -x, 0 if orientation cannot be determined (e.g. side view).
    MediaPipe landmark ids are anatomical, so shoulder x positions reveal
    how the person is oriented in the image."""
    left_shoulder = landmarks[11]
    right_shoulder = landmarks[12]
    if not (_is_detected(left_shoulder) and _is_detected(right_shoulder)):
        return 0
    dx = left_shoulder.x - right_shoulder.x
    if abs(dx) < 0.05:
        return 0
    return 1 if dx > 0 else -1


def _low_visibility_landmarks(landmarks) -> list[str]:
    low_visibility = []
    for name, index in KEY_LANDMARKS.items():
        visibility = getattr(landmarks[index], "visibility", None)
        if visibility is not None and visibility < VISIBILITY_THRESHOLD:
            low_visibility.append(name)
    return low_visibility


def _severity_to_natural_korean(diff: float) -> str | None:
    """Returns a soft Korean adverb describing how noticeable the given
    normalized difference is, or None if it is negligible."""
    if diff < 0.005:
        return None
    elif diff < 0.015:
        return "살짝"
    elif diff < 0.03:
        return "약간"
    else:
        return "꽤"


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


def _height_direction(left_landmark, right_landmark, part_name: str, particle: str) -> tuple[str, str]:
    """Signed height comparison. In image coordinates a smaller y means higher.
    Landmark ids are anatomical so left/right refer to the person's body."""
    diff = left_landmark.y - right_landmark.y
    level = _severity_to_natural_korean(abs(diff))
    if level is None:
        return DIRECTION_BALANCED, f"양쪽 {part_name} 높이는 큰 차이 없이 비슷해 보입니다."
    if diff > 0:
        return "right_higher", f"오른쪽 {part_name}{particle} 왼쪽보다 {level} 높게 위치해 보입니다."
    return "left_higher", f"왼쪽 {part_name}{particle} 오른쪽보다 {level} 높게 위치해 보입니다."


def _analyze_head_tilt(landmarks) -> dict:
    # Prefer eyes; fall back to ears.
    candidate_pairs = [
        (landmarks[2], landmarks[5]),
        (landmarks[7], landmarks[8]),
    ]
    for left, right in candidate_pairs:
        if _is_detected(left) and _is_detected(right):
            # abs(dx) keeps the angle near 0° regardless of which side of the
            # image the right-side landmark is on. The sign then comes purely
            # from the y comparison: positive = the person's right side is
            # lower = head tilted toward the person's right.
            dx = abs(right.x - left.x)
            if dx < 0.0001:
                break
            angle = math.degrees(math.atan2(right.y - left.y, dx))
            if abs(angle) < HEAD_TILT_DEGREE_THRESHOLD:
                direction = DIRECTION_BALANCED
                summary = "머리 기울기는 큰 차이 없이 비교적 균형 잡혀 보입니다."
            elif angle > 0:
                direction = "right"
                summary = (
                    "머리가 오른쪽으로 살짝 기울어진 것처럼 보입니다. "
                    "다만 촬영 각도나 자세에 따라 다르게 보일 수도 있습니다."
                )
            else:
                direction = "left"
                summary = (
                    "머리가 왼쪽으로 살짝 기울어진 것처럼 보입니다. "
                    "다만 촬영 각도나 자세에 따라 다르게 보일 수도 있습니다."
                )
            return {
                "detected": True,
                "angle": round(angle, 1),
                "direction": direction,
                "summary": summary,
            }
    return {
        "detected": False,
        "angle": None,
        "direction": DIRECTION_NOT_DETECTED,
        "summary": "사진에서 머리 위치가 충분히 보이지 않아 머리 기울기는 분석하기 어렵습니다.",
    }


def _analyze_trunk_centerline(landmarks, person_left_sign: int) -> dict:
    left_shoulder = landmarks[11]
    right_shoulder = landmarks[12]
    left_hip = landmarks[23]
    right_hip = landmarks[24]

    if not all(_is_detected(p) for p in (left_shoulder, right_shoulder, left_hip, right_hip)):
        return {
            "detected": False,
            "angle": None,
            "direction": DIRECTION_NOT_DETECTED,
            "summary": "사진에서 몸통 위치가 충분히 보이지 않아 몸통 중심선은 분석하기 어렵습니다.",
        }

    shoulder_mid_x = _center_x(left_shoulder, right_shoulder)
    shoulder_mid_y = _center_y(left_shoulder, right_shoulder)
    hip_mid_x = _center_x(left_hip, right_hip)
    hip_mid_y = _center_y(left_hip, right_hip)

    dx = shoulder_mid_x - hip_mid_x
    dy = hip_mid_y - shoulder_mid_y
    angle = math.degrees(math.atan2(dx, abs(dy)))
    abs_angle = abs(angle)

    if abs_angle < TRUNK_TILT_MILD_DEGREES:
        return {
            "detected": True,
            "angle": round(angle, 1),
            "direction": DIRECTION_BALANCED,
            "summary": "몸통 중심선은 비교적 가운데에 가깝게 보입니다.",
        }

    # Map the image-based shift onto the person's left/right when the body
    # orientation is known; otherwise fall back to image-based wording.
    if person_left_sign != 0:
        toward_person_left = dx * person_left_sign > 0
        direction = "left" if toward_person_left else "right"
        side_text = "왼쪽" if toward_person_left else "오른쪽"
        frame_note = ""
    else:
        direction = "right" if dx > 0 else "left"
        side_text = "오른쪽" if dx > 0 else "왼쪽"
        frame_note = "(사진 기준) "

    if abs_angle < TRUNK_TILT_NOTICEABLE_DEGREES:
        summary = f"몸통 중심선이 {frame_note}{side_text}으로 살짝 치우쳐 보일 수 있습니다."
    else:
        summary = (
            f"몸통 중심선이 {frame_note}{side_text}으로 기울어진 것으로 보입니다. "
            "정확한 상태는 전문가와 상담해보는 것을 권장합니다."
        )
    return {"detected": True, "angle": round(angle, 1), "direction": direction, "summary": summary}


def _analyze_foot_direction(landmarks, person_left_sign: int) -> dict:
    left_heel = landmarks[29]
    right_heel = landmarks[30]
    left_foot_index = landmarks[31]
    right_foot_index = landmarks[32]

    if not all(_is_detected(p) for p in (left_heel, right_heel, left_foot_index, right_foot_index)):
        return {
            "detected": False,
            "left_angle": None,
            "right_angle": None,
            "difference": None,
            "status": DIRECTION_NOT_DETECTED,
            "summary": "사진에서 발 위치가 충분히 보이지 않아 발 방향 차이는 분석하기 어렵습니다.",
        }

    left_dx = left_foot_index.x - left_heel.x
    left_dy = left_foot_index.y - left_heel.y
    right_dx = right_foot_index.x - right_heel.x
    right_dy = right_foot_index.y - right_heel.y

    left_angle = math.degrees(math.atan2(left_dy, left_dx))
    right_angle = math.degrees(math.atan2(right_dy, right_dx))
    difference = abs(left_angle - right_angle) % 360.0
    if difference > 180.0:
        difference = 360.0 - difference

    # Outward deviation: angle of each foot vector from straight-down in the
    # image, signed toward the person's outside. Requires known body
    # orientation and feet pointing generally downward in the image.
    reliable = person_left_sign != 0 and left_dy > 0 and right_dy > 0
    if reliable:
        left_deviation = math.degrees(math.atan2(left_dx, left_dy))
        right_deviation = math.degrees(math.atan2(right_dx, right_dy))
        left_outward = left_deviation * person_left_sign
        right_outward = -right_deviation * person_left_sign
        outward_difference = left_outward - right_outward
    else:
        outward_difference = None

    if difference < FOOT_DIFF_MILD_DEGREES:
        status = DIRECTION_BALANCED
        summary = "양쪽 발 방향은 큰 차이 없이 비슷해 보입니다."
    elif outward_difference is not None and abs(outward_difference) >= FOOT_DIFF_MILD_DEGREES:
        if outward_difference > 0:
            status = "left_more_outward"
            summary = (
                "왼쪽 발이 오른쪽보다 바깥쪽으로 조금 더 벌어진 것으로 보입니다. "
                "다만 촬영 각도에 따라 차이가 생길 수 있습니다."
            )
        else:
            status = "right_more_outward"
            summary = (
                "오른쪽 발이 왼쪽보다 바깥쪽으로 조금 더 벌어진 것으로 보입니다. "
                "다만 촬영 각도에 따라 차이가 생길 수 있습니다."
            )
    else:
        status = "uncertain"
        summary = "발 방향은 양쪽이 조금 다르게 벌어진 것으로 추정됩니다. 다만 촬영 각도에 따라 차이가 생길 수 있습니다."

    return {
        "detected": True,
        "left_angle": round(left_angle, 1),
        "right_angle": round(right_angle, 1),
        "difference": round(difference, 1),
        "status": status,
        "summary": summary,
    }


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

    person_left_sign = _person_left_sign(landmarks)

    head_tilt = _analyze_head_tilt(landmarks)
    trunk_centerline = _analyze_trunk_centerline(landmarks, person_left_sign)
    foot_direction = _analyze_foot_direction(landmarks, person_left_sign)

    shoulder_direction, shoulder_direction_summary = _height_direction(
        left_shoulder, right_shoulder, "어깨", "가"
    )
    hip_direction, hip_direction_summary = _height_direction(left_hip, right_hip, "골반", "이")

    low_visibility = _low_visibility_landmarks(landmarks)

    summary_parts = []

    shoulder_level = _severity_to_natural_korean(shoulder_diff)
    if shoulder_level:
        summary_parts.append(shoulder_direction_summary)

    hip_level = _severity_to_natural_korean(hip_diff)
    if hip_level:
        summary_parts.append(hip_direction_summary)

    if shoulder_hip_center_offset > CENTER_OFFSET_THRESHOLD:
        summary_parts.append(
            "어깨 중심과 골반 중심 사이에 약간 차이가 있어, 몸통 중심선이 한쪽으로 살짝 치우쳐 보일 수 있습니다."
        )

    if head_tilt["detected"] and head_tilt["direction"] not in (DIRECTION_BALANCED,):
        summary_parts.append(head_tilt["summary"])

    if head_center_offset is not None and head_center_offset > HEAD_CENTER_OFFSET_THRESHOLD:
        summary_parts.append("머리 중심이 어깨 중심에서 약간 벗어나 보입니다.")

    if foot_direction["detected"] and foot_direction["status"] != DIRECTION_BALANCED:
        summary_parts.append(foot_direction["summary"])

    if not summary_parts:
        summary = "사진상 어깨와 골반 정렬은 큰 불균형 없이 비교적 안정적으로 보입니다."
    else:
        summary = " ".join(summary_parts)

    if low_visibility:
        summary += " 사진에서 일부 부위가 잘 보이지 않아 분석 결과가 제한적일 수 있습니다."

    summary += " 이 결과는 사진 한 장을 바탕으로 한 참고용 분석입니다. 실제 통증 원인은 움직임, 생활 습관, 운동량 등과 함께 확인하는 것이 좋습니다."

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
        "low_visibility_landmarks": low_visibility,
        "simple_summary": summary,
        "shoulder_direction": shoulder_direction,
        "shoulder_direction_summary": shoulder_direction_summary,
        "hip_direction": hip_direction,
        "hip_direction_summary": hip_direction_summary,
        "head_tilt_detected": head_tilt["detected"],
        "head_tilt_angle_degrees": head_tilt["angle"],
        "head_tilt_direction": head_tilt["direction"],
        "head_tilt_summary": head_tilt["summary"],
        "trunk_centerline_detected": trunk_centerline["detected"],
        "trunk_centerline_angle_degrees": trunk_centerline["angle"],
        "trunk_tilt_direction": trunk_centerline["direction"],
        "trunk_centerline_summary": trunk_centerline["summary"],
        "foot_direction_detected": foot_direction["detected"],
        "left_foot_angle_degrees": foot_direction["left_angle"],
        "right_foot_angle_degrees": foot_direction["right_angle"],
        "foot_direction_difference_degrees": foot_direction["difference"],
        "foot_direction_status": foot_direction["status"],
        "foot_direction_summary": foot_direction["summary"],
    }
