CAUTION_MESSAGE = (
    "결과는 단일 이미지 기반의 자세 참고 분석이며 의학적 진단이 아닙니다. "
    "통증이 지속되거나 악화되면 의료 전문가와 상담하세요."
)

SHOULDER_THRESHOLD = 0.005
HIP_THRESHOLD = 0.005
SLOPE_THRESHOLD = 0.08
CENTER_OFFSET_THRESHOLD = 0.04
HEAD_TILT_THRESHOLD = 0.015
HEAD_CENTER_OFFSET_THRESHOLD = 0.05
FOOT_DIFF_THRESHOLD = 15.0


def generate_recommendations(
    shoulder_diff: float,
    hip_diff: float,
    shoulder_slope: float = 0.0,
    hip_slope: float = 0.0,
    shoulder_hip_center_offset: float = 0.0,
    low_visibility_landmarks: list[str] | None = None,
    head_tilt_score: float | None = None,
    head_center_offset: float | None = None,
    foot_angle_diff: float | None = None,
) -> dict:
    exercises = []
    low_visibility_landmarks = low_visibility_landmarks or []

    if head_tilt_score is not None and head_tilt_score > HEAD_TILT_THRESHOLD:
        exercises.append("가벼운 목 가동성 운동: 천천히 고개를 좌우로 기울이는 스트레칭을 시도해 보세요.")
        exercises.append("한쪽으로 오래 기울어진 자세는 피하는 것이 좋습니다.")

    if shoulder_diff > SHOULDER_THRESHOLD:
        exercises.append("어깨 가동성 운동: 어깨 돌리기와 팔 가로 스트레칭을 시도해 보세요.")
        exercises.append("등 상부 스트레칭: 가슴 스트레칭과 흉추 회전 동작을 해보세요.")

    if hip_diff > HIP_THRESHOLD:
        exercises.append("고관절 가동성 운동: 양쪽 무릎 런지 스트레칭을 시도해 보세요.")
        exercises.append("글루트 브릿지: 무릎을 구부리고 누워 엉덩이를 들어 올리세요.")

    if (
        abs(shoulder_slope) > SLOPE_THRESHOLD
        or abs(hip_slope) > SLOPE_THRESHOLD
        or shoulder_hip_center_offset > CENTER_OFFSET_THRESHOLD
        or (head_center_offset is not None and head_center_offset > HEAD_CENTER_OFFSET_THRESHOLD)
    ):
        exercises.append("가벼운 몸통 회전 가동성 운동을 시도해 보세요.")
        exercises.append("코어 안정화 운동을 통해 체간 정렬을 도울 수 있습니다.")

    if foot_angle_diff is not None and foot_angle_diff > FOOT_DIFF_THRESHOLD:
        exercises.append("양발 방향 대칭을 확인해 보세요.")
        exercises.append("발목 가동성 운동을 시도해 보세요.")

    if low_visibility_landmarks:
        exercises.append(
            "사진 품질 확인: 어깨와 골반이 잘 보이는 밝고 선명한 전신 사진을 사용해 주세요."
        )

    if not exercises:
        exercises.append(
            "올려주신 사진에서는 주요 자세 차이가 관찰되지 않았습니다. 하루 종일 규칙적인 움직임을 유지하세요."
        )
        exercises.append("장시간 같은 자세를 피하고 가벼운 전신 스트레칭을 권장합니다.")

    return {
        "exercises": exercises,
        "caution_message": CAUTION_MESSAGE,
    }
