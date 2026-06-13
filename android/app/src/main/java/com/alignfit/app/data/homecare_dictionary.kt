package com.alignfit.app.data

import com.alignfit.app.network.AnalysisResponse

// ─── Data Structures ──────────────────────────────────────────────────────────

data class PainInputRegion(
    val id: String,
    val regionLabel: String,
    val locationLabel: String
) {
    val fullLabel: String get() = "$regionLabel $locationLabel"
}

data class PainRegionGroup(
    val regionLabel: String,
    val options: List<PainInputRegion>
)

data class SvgBodyRegion(
    val id: String,
    val label: String
)

data class PostureFlag(
    val id: String,
    val label: String
)

data class ExerciseItem(
    val id: String,
    val koreanName: String,
    val englishName: String,
    val reason: String,
    val caution: String,
    val youtubeQuery: String,
    // Fixed video id, if curated. Do not fill with unverified ids — when null,
    // the UI falls back to a placeholder preview plus the YouTube search link.
    val youtubeVideoId: String? = null
) {
    val youtubeUrl: String?
        get() = youtubeVideoId?.let { "https://www.youtube.com/watch?v=$it" }
    val youtubeThumbnailUrl: String?
        get() = youtubeVideoId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
}

data class RecommendationRule(
    val id: String,
    val priority: Int,
    val painIds: List<String> = emptyList(),
    val svgRegionIds: List<String> = emptyList(),
    val postureFlags: List<String> = emptyList(),
    val feedbackTitle: String,
    val feedbackBody: String,
    val exerciseIds: List<String>
)

data class ConsultationTemplate(
    val talkingPoints: List<String>,
    val redFlags: List<String>
)

data class RecommendationResult(
    val feedbackCards: List<RecommendationRule>,
    val exercises: List<ExerciseItem>
)

// ─── Dictionary ───────────────────────────────────────────────────────────────

object HomecareDictionary {

    // Matches the backend recommendation_service thresholds (normalized landmark units).
    const val POSTURE_DIFF_THRESHOLD = 0.005

    // Degree thresholds matching backend posture_analysis_service.
    const val HEAD_TILT_DEGREE_THRESHOLD = 3.0
    const val TRUNK_TILT_DEGREE_THRESHOLD = 3.0
    const val FOOT_DIFF_DEGREE_THRESHOLD = 8.0

    // ── Posture flag ids ──
    const val FLAG_SHOULDER_LEFT_HIGHER = "shoulder_left_higher"
    const val FLAG_SHOULDER_RIGHT_HIGHER = "shoulder_right_higher"
    const val FLAG_PELVIS_LEFT_HIGHER = "pelvis_left_higher"
    const val FLAG_PELVIS_RIGHT_HIGHER = "pelvis_right_higher"
    const val FLAG_TRUNK_LEFT_TILT = "trunk_left_tilt"
    const val FLAG_TRUNK_RIGHT_TILT = "trunk_right_tilt"
    const val FLAG_HEAD_LEFT_TILT = "head_left_tilt"
    const val FLAG_HEAD_RIGHT_TILT = "head_right_tilt"
    const val FLAG_FORWARD_HEAD = "forward_head_pattern"
    const val FLAG_ROUNDED_SHOULDER = "rounded_shoulder_pattern"
    const val FLAG_ANTERIOR_PELVIC_TILT = "anterior_pelvic_tilt_pattern"
    const val FLAG_FOOT_ASYMMETRY = "foot_direction_asymmetry"
    const val FLAG_NO_MAJOR_ISSUE = "no_major_posture_issue"

    // The current backend returns only absolute height differences (no left/right
    // direction), so these neutral flags are the ones actually inferred today.
    const val FLAG_SHOULDER_ASYMMETRY = "shoulder_height_asymmetry"
    const val FLAG_PELVIS_ASYMMETRY = "pelvis_height_asymmetry"

    val POSTURE_FLAGS = listOf(
        PostureFlag(FLAG_SHOULDER_LEFT_HIGHER, "왼쪽 어깨가 더 높아 보입니다"),
        PostureFlag(FLAG_SHOULDER_RIGHT_HIGHER, "오른쪽 어깨가 더 높아 보입니다"),
        PostureFlag(FLAG_PELVIS_LEFT_HIGHER, "왼쪽 골반이 더 높아 보입니다"),
        PostureFlag(FLAG_PELVIS_RIGHT_HIGHER, "오른쪽 골반이 더 높아 보입니다"),
        PostureFlag(FLAG_TRUNK_LEFT_TILT, "몸통이 왼쪽으로 기울어 보일 가능성이 있습니다"),
        PostureFlag(FLAG_TRUNK_RIGHT_TILT, "몸통이 오른쪽으로 기울어 보일 가능성이 있습니다"),
        PostureFlag(FLAG_HEAD_LEFT_TILT, "머리가 왼쪽으로 기울어 보일 가능성이 있습니다"),
        PostureFlag(FLAG_HEAD_RIGHT_TILT, "머리가 오른쪽으로 기울어 보일 가능성이 있습니다"),
        PostureFlag(FLAG_FORWARD_HEAD, "머리가 앞으로 나온 패턴 가능성이 있습니다"),
        PostureFlag(FLAG_ROUNDED_SHOULDER, "어깨가 안쪽으로 말린 패턴 가능성이 있습니다"),
        PostureFlag(FLAG_ANTERIOR_PELVIC_TILT, "골반 전방 경사 패턴 가능성이 있습니다"),
        PostureFlag(FLAG_FOOT_ASYMMETRY, "발 방향이 양쪽으로 조금 다르게 보입니다"),
        PostureFlag(FLAG_NO_MAJOR_ISSUE, "사진상 큰 자세 불균형은 없어 보입니다"),
        PostureFlag(FLAG_SHOULDER_ASYMMETRY, "어깨 높이가 양쪽으로 살짝 달라 보입니다"),
        PostureFlag(FLAG_PELVIS_ASYMMETRY, "골반 높이도 양쪽이 살짝 달라 보입니다")
    )

    val POSTURE_FLAGS_BY_ID: Map<String, PostureFlag> = POSTURE_FLAGS.associateBy { it.id }

    // ── Checkbox pain regions ──

    val PAIN_REGION_GROUPS = listOf(
        PainRegionGroup(
            "허리", listOf(
                PainInputRegion("low_back_left", "허리", "왼쪽"),
                PainInputRegion("low_back_right", "허리", "오른쪽")
            )
        ),
        PainRegionGroup(
            "어깨", listOf(
                PainInputRegion("shoulder_left_front", "어깨", "왼쪽 앞"),
                PainInputRegion("shoulder_left_back", "어깨", "왼쪽 뒤"),
                PainInputRegion("shoulder_right_front", "어깨", "오른쪽 앞"),
                PainInputRegion("shoulder_right_back", "어깨", "오른쪽 뒤")
            )
        ),
        PainRegionGroup(
            "고관절 및 골반", listOf(
                PainInputRegion("hip_pelvis_left_front", "고관절 및 골반", "왼쪽 앞"),
                PainInputRegion("hip_pelvis_left_back", "고관절 및 골반", "왼쪽 뒤"),
                PainInputRegion("hip_pelvis_right_front", "고관절 및 골반", "오른쪽 앞"),
                PainInputRegion("hip_pelvis_right_back", "고관절 및 골반", "오른쪽 뒤")
            )
        ),
        PainRegionGroup(
            "허벅지", listOf(
                PainInputRegion("thigh_left_front", "허벅지", "왼쪽 앞"),
                PainInputRegion("thigh_left_back", "허벅지", "왼쪽 뒤"),
                PainInputRegion("thigh_right_front", "허벅지", "오른쪽 앞"),
                PainInputRegion("thigh_right_back", "허벅지", "오른쪽 뒤")
            )
        ),
        PainRegionGroup(
            "종아리", listOf(
                PainInputRegion("calf_left_front", "종아리", "왼쪽 앞"),
                PainInputRegion("calf_left_back", "종아리", "왼쪽 뒤"),
                PainInputRegion("calf_right_front", "종아리", "오른쪽 앞"),
                PainInputRegion("calf_right_back", "종아리", "오른쪽 뒤")
            )
        ),
        PainRegionGroup(
            "발목", listOf(
                PainInputRegion("ankle_left_front", "발목", "왼쪽 앞"),
                PainInputRegion("ankle_left_back", "발목", "왼쪽 뒤"),
                PainInputRegion("ankle_right_front", "발목", "오른쪽 앞"),
                PainInputRegion("ankle_right_back", "발목", "오른쪽 뒤")
            )
        )
    )

    val PAIN_REGIONS_BY_ID: Map<String, PainInputRegion> =
        PAIN_REGION_GROUPS.flatMap { it.options }.associateBy { it.id }

    fun painLabel(painId: String): String =
        PAIN_REGIONS_BY_ID[painId]?.fullLabel ?: painId

    // ── SVG body map regions ──

    private val SVG_REGION_NAME_LABELS = mapOf(
        "neck" to "목",
        "shoulder" to "어깨",
        "chest" to "가슴",
        "abdomen" to "복부",
        "upper_back" to "등(상부)",
        "lower_back" to "허리(등 하부)",
        "waist" to "허리/옆구리",
        "glute" to "엉덩이",
        "upper_arm" to "위팔",
        "forearm" to "아래팔",
        "hand_wrist" to "손/손목",
        "hip" to "골반",
        "thigh" to "허벅지",
        "front_thigh" to "허벅지 앞",
        "back_thigh" to "허벅지 뒤",
        "knee" to "무릎",
        "calf" to "종아리",
        "ankle_foot" to "발목/발"
    )

    fun svgRegionLabel(id: String): String {
        var rest = id
        val view = when {
            rest.startsWith("front_") -> { rest = rest.removePrefix("front_"); "정면" }
            rest.startsWith("back_") -> { rest = rest.removePrefix("back_"); "후면" }
            rest.startsWith("left_side_") -> { rest = rest.removePrefix("left_side_"); "좌측면" }
            rest.startsWith("right_side_") -> { rest = rest.removePrefix("right_side_"); "우측면" }
            else -> ""
        }
        val side = when {
            rest.startsWith("left_") -> { rest = rest.removePrefix("left_"); "왼쪽 " }
            rest.startsWith("right_") -> { rest = rest.removePrefix("right_"); "오른쪽 " }
            else -> ""
        }
        val region = SVG_REGION_NAME_LABELS[rest] ?: rest
        return "$side$region ($view)"
    }

    private val SVG_BODY_REGION_IDS = listOf(
        // front
        "front_neck", "front_left_shoulder", "front_right_shoulder", "front_chest",
        "front_abdomen", "front_left_upper_arm", "front_right_upper_arm",
        "front_left_forearm", "front_right_forearm",
        "front_left_hand_wrist", "front_right_hand_wrist",
        "front_left_hip", "front_right_hip",
        "front_left_front_thigh", "front_right_front_thigh",
        "front_left_knee", "front_right_knee",
        "front_left_calf", "front_right_calf",
        "front_left_ankle_foot", "front_right_ankle_foot",
        // back
        "back_neck", "back_left_shoulder", "back_right_shoulder",
        "back_upper_back", "back_lower_back",
        "back_left_waist", "back_right_waist",
        "back_left_glute", "back_right_glute",
        "back_left_upper_arm", "back_right_upper_arm",
        "back_left_forearm", "back_right_forearm",
        "back_left_hand_wrist", "back_right_hand_wrist",
        "back_left_back_thigh", "back_right_back_thigh",
        "back_left_knee", "back_right_knee",
        "back_left_calf", "back_right_calf",
        "back_left_ankle_foot", "back_right_ankle_foot",
        // left side
        "left_side_neck", "left_side_shoulder", "left_side_chest", "left_side_abdomen",
        "left_side_upper_back", "left_side_lower_back", "left_side_waist",
        "left_side_glute", "left_side_upper_arm", "left_side_forearm",
        "left_side_hand_wrist", "left_side_hip", "left_side_thigh",
        "left_side_knee", "left_side_calf", "left_side_ankle_foot",
        // right side
        "right_side_neck", "right_side_shoulder", "right_side_chest", "right_side_abdomen",
        "right_side_upper_back", "right_side_lower_back", "right_side_waist",
        "right_side_glute", "right_side_upper_arm", "right_side_forearm",
        "right_side_hand_wrist", "right_side_hip", "right_side_thigh",
        "right_side_knee", "right_side_calf", "right_side_ankle_foot"
    )

    val SVG_BODY_REGIONS: List<SvgBodyRegion> =
        SVG_BODY_REGION_IDS.map { SvgBodyRegion(it, svgRegionLabel(it)) }

    // ── Exercises ──

    val EXERCISES = listOf(
        ExerciseItem(
            id = "chin_tuck",
            koreanName = "턱 당기기",
            englishName = "Chin Tuck",
            reason = "머리가 앞으로 나온 자세 패턴 완화와 목 정렬에 도움이 될 가능성이 있습니다.",
            caution = "목에 통증이 느껴지면 즉시 중단하세요. 과도하게 힘을 주지 마세요.",
            youtubeQuery = "chin tuck exercise 거북목 운동"
        ),
        ExerciseItem(
            id = "thoracic_extension",
            koreanName = "흉추 신전 운동",
            englishName = "Thoracic Extension",
            reason = "굽은 등과 상체 정렬 개선에 도움이 될 가능성이 있습니다.",
            caution = "허리를 과도하게 젖히지 말고 등 윗부분 위주로 움직이세요.",
            youtubeQuery = "thoracic extension exercise 흉추 가동성"
        ),
        ExerciseItem(
            id = "scapular_retraction",
            koreanName = "견갑골 모으기",
            englishName = "Scapular Retraction",
            reason = "어깨 주변 안정성과 좌우 균형 유지에 도움이 될 가능성이 있습니다.",
            caution = "어깨를 으쓱 올리지 말고 날개뼈를 뒤로 모으는 느낌으로 하세요.",
            youtubeQuery = "scapular retraction exercise 견갑골 운동"
        ),
        ExerciseItem(
            id = "pec_stretch",
            koreanName = "가슴 근육 스트레칭",
            englishName = "Pec Stretch",
            reason = "가슴 근육의 긴장을 풀어 어깨가 말린 패턴 완화에 도움이 될 가능성이 있습니다.",
            caution = "어깨 앞쪽에 찌릿한 통증이 느껴지면 강도를 줄이세요.",
            youtubeQuery = "pec stretch doorway 가슴 스트레칭"
        ),
        ExerciseItem(
            id = "glute_bridge",
            koreanName = "엉덩이 들기",
            englishName = "Glute Bridge",
            reason = "엉덩이와 허리 주변 근육을 강화해 골반 안정에 도움이 될 가능성이 있습니다.",
            caution = "허리를 과도하게 꺾지 말고 엉덩이 힘으로 들어 올리세요.",
            youtubeQuery = "glute bridge exercise 브릿지 운동"
        ),
        ExerciseItem(
            id = "dead_bug",
            koreanName = "데드버그",
            englishName = "Dead Bug",
            reason = "허리에 부담을 적게 주면서 코어 안정성을 기르는 데 도움이 될 가능성이 있습니다.",
            caution = "허리가 바닥에서 뜨지 않는 범위에서만 팔다리를 움직이세요.",
            youtubeQuery = "dead bug exercise 데드버그"
        ),
        ExerciseItem(
            id = "bird_dog",
            koreanName = "버드독",
            englishName = "Bird Dog",
            reason = "척추 안정화와 몸통 좌우 균형 향상에 도움이 될 가능성이 있습니다.",
            caution = "골반이 좌우로 흔들리지 않게 천천히 진행하세요.",
            youtubeQuery = "bird dog exercise 버드독"
        ),
        ExerciseItem(
            id = "side_plank",
            koreanName = "사이드 플랭크",
            englishName = "Side Plank",
            reason = "몸통 측면 근육을 강화해 좌우 균형 유지에 도움이 될 가능성이 있습니다.",
            caution = "어깨나 손목에 통증이 있으면 무릎을 대고 강도를 낮추세요.",
            youtubeQuery = "side plank exercise 사이드 플랭크"
        ),
        ExerciseItem(
            id = "hip_flexor_stretch",
            koreanName = "고관절 굴곡근 스트레칭",
            englishName = "Hip Flexor Stretch",
            reason = "고관절 앞쪽 긴장을 풀어 골반 정렬에 도움이 될 가능성이 있습니다.",
            caution = "허리를 과도하게 젖히지 말고 골반을 살짝 뒤로 말아 유지하세요.",
            youtubeQuery = "hip flexor stretch 고관절 스트레칭"
        ),
        ExerciseItem(
            id = "hamstring_stretch",
            koreanName = "햄스트링 스트레칭",
            englishName = "Hamstring Stretch",
            reason = "허벅지 뒤쪽 긴장을 풀어 골반과 허리 부담 완화에 도움이 될 가능성이 있습니다.",
            caution = "반동을 주지 말고 천천히 늘려 주세요.",
            youtubeQuery = "hamstring stretch 햄스트링 스트레칭"
        ),
        ExerciseItem(
            id = "calf_stretch",
            koreanName = "종아리 스트레칭",
            englishName = "Calf Stretch",
            reason = "종아리와 발목 주변 유연성 향상에 도움이 될 가능성이 있습니다.",
            caution = "발목에 통증이 있으면 강도를 줄이고 천천히 진행하세요.",
            youtubeQuery = "calf stretch 종아리 스트레칭"
        ),
        ExerciseItem(
            id = "neck_side_stretch",
            koreanName = "목 옆 스트레칭",
            englishName = "Neck Side Stretch",
            reason = "목 옆 근육의 긴장을 풀어 머리 기울어짐 패턴 완화에 도움이 될 가능성이 있습니다.",
            caution = "손으로 머리를 세게 당기지 말고 무게만 살짝 얹으세요.",
            youtubeQuery = "neck side stretch 목 스트레칭"
        )
    )

    val EXERCISES_BY_ID: Map<String, ExerciseItem> = EXERCISES.associateBy { it.id }

    // ── Recommendation rules ──

    val DEFAULT_RULE = RecommendationRule(
        id = "default_general_homecare",
        priority = 10,
        feedbackTitle = "전반적인 자세 관리",
        feedbackBody = "특정 부위에 집중된 신호는 뚜렷하지 않지만, 규칙적인 움직임과 기본 코어 운동은 " +
            "자세 유지에 도움이 될 가능성이 있습니다. 장시간 같은 자세를 피하는 것을 권장합니다.",
        exerciseIds = listOf("glute_bridge", "dead_bug", "bird_dog", "thoracic_extension")
    )

    val RULES = listOf(
        RecommendationRule(
            id = "low_back_core_stability",
            priority = 90,
            painIds = listOf("low_back_left", "low_back_right"),
            svgRegionIds = listOf(
                "back_lower_back", "back_left_waist", "back_right_waist",
                "left_side_lower_back", "right_side_lower_back",
                "left_side_waist", "right_side_waist", "front_abdomen"
            ),
            feedbackTitle = "허리 주변 부담 가능성",
            feedbackBody = "선택하신 부위로 보아 허리 주변에 부담이 쌓였을 가능성이 있습니다. " +
                "허리에 부담이 적은 코어 안정화 운동부터 가볍게 시작하는 것을 권장합니다.",
            exerciseIds = listOf("dead_bug", "bird_dog", "glute_bridge", "hip_flexor_stretch")
        ),
        RecommendationRule(
            id = "shoulder_neck_pattern",
            priority = 85,
            painIds = listOf(
                "shoulder_left_front", "shoulder_left_back",
                "shoulder_right_front", "shoulder_right_back"
            ),
            svgRegionIds = listOf(
                "front_neck", "back_neck", "left_side_neck", "right_side_neck",
                "front_left_shoulder", "front_right_shoulder",
                "back_left_shoulder", "back_right_shoulder",
                "left_side_shoulder", "right_side_shoulder",
                "front_chest", "back_upper_back",
                "left_side_upper_back", "right_side_upper_back",
                "left_side_chest", "right_side_chest"
            ),
            postureFlags = listOf(
                FLAG_ROUNDED_SHOULDER, FLAG_FORWARD_HEAD,
                FLAG_HEAD_LEFT_TILT, FLAG_HEAD_RIGHT_TILT
            ),
            feedbackTitle = "어깨·목 주변 긴장 가능성",
            feedbackBody = "어깨와 목 주변 근육이 긴장되어 있을 가능성이 있습니다. " +
                "가슴을 펴고 날개뼈를 모으는 가벼운 운동과 스트레칭을 권장합니다.",
            exerciseIds = listOf(
                "scapular_retraction", "pec_stretch", "chin_tuck",
                "thoracic_extension", "neck_side_stretch"
            )
        ),
        RecommendationRule(
            id = "shoulder_height_asymmetry_care",
            priority = 80,
            postureFlags = listOf(
                FLAG_SHOULDER_ASYMMETRY, FLAG_SHOULDER_LEFT_HIGHER, FLAG_SHOULDER_RIGHT_HIGHER
            ),
            feedbackTitle = "어깨 높이 차이 관찰",
            feedbackBody = "사진상 어깨 높이에 좌우 차이가 있어 보입니다. 한쪽으로 가방을 메거나 " +
                "기울여 앉는 습관이 영향을 줄 가능성이 있습니다. 상체 균형 운동을 권장합니다.",
            exerciseIds = listOf(
                "scapular_retraction", "thoracic_extension", "pec_stretch", "side_plank"
            )
        ),
        RecommendationRule(
            id = "pelvis_hip_pattern",
            priority = 78,
            painIds = listOf(
                "hip_pelvis_left_front", "hip_pelvis_left_back",
                "hip_pelvis_right_front", "hip_pelvis_right_back"
            ),
            svgRegionIds = listOf(
                "front_left_hip", "front_right_hip",
                "back_left_glute", "back_right_glute",
                "left_side_hip", "right_side_hip",
                "left_side_glute", "right_side_glute"
            ),
            postureFlags = listOf(
                FLAG_PELVIS_ASYMMETRY, FLAG_PELVIS_LEFT_HIGHER,
                FLAG_PELVIS_RIGHT_HIGHER, FLAG_ANTERIOR_PELVIC_TILT
            ),
            feedbackTitle = "골반 주변 불균형 가능성",
            feedbackBody = "골반 높이 차이 또는 고관절 주변 부담이 관찰될 가능성이 있습니다. " +
                "골반 주변 근육을 풀고 안정화하는 운동을 권장합니다.",
            exerciseIds = listOf("hip_flexor_stretch", "glute_bridge", "side_plank", "bird_dog")
        ),
        RecommendationRule(
            id = "trunk_alignment_care",
            priority = 70,
            postureFlags = listOf(FLAG_TRUNK_LEFT_TILT, FLAG_TRUNK_RIGHT_TILT),
            feedbackTitle = "몸통 중심선 기울어짐 가능성",
            feedbackBody = "몸통 중심선이 한쪽으로 기울어 보일 가능성이 있습니다. " +
                "몸통 측면과 코어를 함께 강화하는 운동을 권장합니다.",
            exerciseIds = listOf("side_plank", "dead_bug", "bird_dog")
        ),
        RecommendationRule(
            id = "thigh_knee_pattern",
            priority = 65,
            painIds = listOf(
                "thigh_left_front", "thigh_left_back",
                "thigh_right_front", "thigh_right_back"
            ),
            svgRegionIds = listOf(
                "front_left_front_thigh", "front_right_front_thigh",
                "back_left_back_thigh", "back_right_back_thigh",
                "left_side_thigh", "right_side_thigh",
                "front_left_knee", "front_right_knee",
                "back_left_knee", "back_right_knee",
                "left_side_knee", "right_side_knee"
            ),
            feedbackTitle = "허벅지·무릎 주변 부담 가능성",
            feedbackBody = "허벅지나 무릎 주변에 부담이 쌓였을 가능성이 있습니다. " +
                "허벅지 앞뒤 근육을 풀고 엉덩이 근육을 강화하는 운동을 권장합니다.",
            exerciseIds = listOf("hip_flexor_stretch", "hamstring_stretch", "glute_bridge")
        ),
        RecommendationRule(
            id = "calf_ankle_pattern",
            priority = 60,
            painIds = listOf(
                "calf_left_front", "calf_left_back", "calf_right_front", "calf_right_back",
                "ankle_left_front", "ankle_left_back", "ankle_right_front", "ankle_right_back"
            ),
            svgRegionIds = listOf(
                "front_left_calf", "front_right_calf",
                "back_left_calf", "back_right_calf",
                "left_side_calf", "right_side_calf",
                "front_left_ankle_foot", "front_right_ankle_foot",
                "back_left_ankle_foot", "back_right_ankle_foot",
                "left_side_ankle_foot", "right_side_ankle_foot"
            ),
            postureFlags = listOf(FLAG_FOOT_ASYMMETRY),
            feedbackTitle = "종아리·발목 주변 긴장 가능성",
            feedbackBody = "종아리나 발목 주변이 긴장되어 있을 가능성이 있습니다. " +
                "종아리 스트레칭과 하체 뒤쪽 이완 운동을 권장합니다.",
            exerciseIds = listOf("calf_stretch", "hamstring_stretch", "glute_bridge")
        ),
        RecommendationRule(
            id = "arm_posture_support",
            priority = 55,
            svgRegionIds = listOf(
                "front_left_upper_arm", "front_right_upper_arm",
                "back_left_upper_arm", "back_right_upper_arm",
                "front_left_forearm", "front_right_forearm",
                "back_left_forearm", "back_right_forearm",
                "front_left_hand_wrist", "front_right_hand_wrist",
                "back_left_hand_wrist", "back_right_hand_wrist",
                "left_side_upper_arm", "right_side_upper_arm",
                "left_side_forearm", "right_side_forearm",
                "left_side_hand_wrist", "right_side_hand_wrist"
            ),
            feedbackTitle = "팔·손목 주변 불편감",
            feedbackBody = "팔이나 손목 불편감은 어깨와 상체 자세의 영향일 가능성도 있습니다. " +
                "어깨 주변을 함께 관리하는 것을 권장합니다.",
            exerciseIds = listOf("scapular_retraction", "pec_stretch", "thoracic_extension")
        ),
        DEFAULT_RULE
    )

    // ── Consultation template ──

    val CONSULTATION_TEMPLATE = ConsultationTemplate(
        talkingPoints = listOf(
            "어느 부위가 아픈지",
            "언제부터 아팠는지",
            "어떤 동작이나 자세에서 심해지는지",
            "저림, 감각 이상, 힘 빠짐이 있는지",
            "최근 운동량이나 생활 습관 변화가 있었는지",
            "과거에 비슷한 통증이나 부상 이력이 있었는지"
        ),
        redFlags = listOf(
            "다리나 팔의 저림, 감각 이상이 함께 나타나는 경우",
            "힘 빠짐(근력 저하)이 느껴지는 경우",
            "야간 통증이나 휴식 중에도 지속되는 통증",
            "낙상이나 충돌 등 외상 이후 시작된 통증",
            "발열, 체중 감소 등 전신 증상이 동반되는 경우"
        )
    )

    // ─── Posture Flag Inference ───────────────────────────────────────────────

    // Shoulder/hip use absolute height differences. Head/trunk/foot flags are
    // only generated when the backend marks the metric as detected — never
    // invented from missing data. Directional left/right for head and trunk
    // follows the backend's signed angle (image-based).
    fun inferPostureFlags(result: AnalysisResponse?): Set<String> {
        if (result == null || !result.landmarkDetected) return emptySet()
        val flags = mutableSetOf<String>()

        // Shoulder/hip: prefer the backend's signed direction enum;
        // fall back to absolute differences for older backends.
        when (result.shoulderDirection) {
            "left_higher" -> flags += setOf(FLAG_SHOULDER_LEFT_HIGHER, FLAG_SHOULDER_ASYMMETRY)
            "right_higher" -> flags += setOf(FLAG_SHOULDER_RIGHT_HIGHER, FLAG_SHOULDER_ASYMMETRY)
            null -> if (result.shoulderHeightDifference > POSTURE_DIFF_THRESHOLD) {
                flags += FLAG_SHOULDER_ASYMMETRY
            }
        }
        when (result.hipDirection) {
            "left_higher" -> flags += setOf(FLAG_PELVIS_LEFT_HIGHER, FLAG_PELVIS_ASYMMETRY)
            "right_higher" -> flags += setOf(FLAG_PELVIS_RIGHT_HIGHER, FLAG_PELVIS_ASYMMETRY)
            null -> if (result.hipHeightDifference > POSTURE_DIFF_THRESHOLD) {
                flags += FLAG_PELVIS_ASYMMETRY
            }
        }

        // Head/trunk: direction enums; never generated when not detected.
        when (result.headTiltDirection) {
            "left" -> flags += FLAG_HEAD_LEFT_TILT
            "right" -> flags += FLAG_HEAD_RIGHT_TILT
            null -> {
                val headAngle = result.headTiltAngleDegrees
                if (result.headTiltDetected && headAngle != null &&
                    kotlin.math.abs(headAngle) >= HEAD_TILT_DEGREE_THRESHOLD
                ) {
                    flags += if (headAngle > 0) FLAG_HEAD_RIGHT_TILT else FLAG_HEAD_LEFT_TILT
                }
            }
        }
        when (result.trunkTiltDirection) {
            "left" -> flags += FLAG_TRUNK_LEFT_TILT
            "right" -> flags += FLAG_TRUNK_RIGHT_TILT
            null -> {
                val trunkAngle = result.trunkCenterlineAngleDegrees
                if (result.trunkCenterlineDetected && trunkAngle != null &&
                    kotlin.math.abs(trunkAngle) >= TRUNK_TILT_DEGREE_THRESHOLD
                ) {
                    flags += if (trunkAngle > 0) FLAG_TRUNK_RIGHT_TILT else FLAG_TRUNK_LEFT_TILT
                }
            }
        }

        // Foot: asymmetry when a side is more outward, or when a difference
        // is observed but direction is uncertain.
        when (result.footDirectionStatus) {
            "left_more_outward", "right_more_outward", "uncertain" -> flags += FLAG_FOOT_ASYMMETRY
            null -> {
                val footDiff = result.footDirectionDifferenceDegrees
                if (result.footDirectionDetected && footDiff != null &&
                    footDiff >= FOOT_DIFF_DEGREE_THRESHOLD
                ) {
                    flags += FLAG_FOOT_ASYMMETRY
                }
            }
        }

        if (flags.isEmpty()) {
            flags += FLAG_NO_MAJOR_ISSUE
        }
        return flags
    }

    fun hasPostureIssue(postureFlags: Set<String>): Boolean =
        postureFlags.any { it != FLAG_NO_MAJOR_ISSUE }

    // ─── Rule Matching ────────────────────────────────────────────────────────

    fun getRecommendations(
        selectedPainIds: Set<String>,
        selectedSvgRegionIds: Set<String>,
        postureFlags: Set<String>
    ): RecommendationResult {
        val matched = RULES
            .filter { rule ->
                rule.painIds.any { it in selectedPainIds } ||
                    rule.svgRegionIds.any { it in selectedSvgRegionIds } ||
                    rule.postureFlags.any { it in postureFlags }
            }
            .sortedByDescending { it.priority }

        val activeRules = matched.ifEmpty { listOf(DEFAULT_RULE) }
        val feedbackCards = activeRules.take(2)

        var exerciseIds = activeRules
            .flatMap { it.exerciseIds }
            .distinct()
            .take(5)
        if (exerciseIds.size < 3) {
            exerciseIds = (exerciseIds + DEFAULT_RULE.exerciseIds).distinct().take(3)
        }

        return RecommendationResult(
            feedbackCards = feedbackCards,
            exercises = exerciseIds.mapNotNull { EXERCISES_BY_ID[it] }
        )
    }

    // ─── Consultation Summary ─────────────────────────────────────────────────

    fun postureSummaryPhrase(postureFlags: Set<String>): String = when {
        FLAG_SHOULDER_ASYMMETRY in postureFlags && FLAG_PELVIS_ASYMMETRY in postureFlags ->
            "어깨와 골반 높이에 좌우 차이가 있는 것으로 보인다"
        FLAG_SHOULDER_ASYMMETRY in postureFlags ->
            "어깨 높이에 좌우 차이가 있는 것으로 보인다"
        FLAG_PELVIS_ASYMMETRY in postureFlags ->
            "골반 높이에 좌우 차이가 있는 것으로 보인다"
        FLAG_NO_MAJOR_ISSUE in postureFlags ->
            "큰 불균형이 없어 보인다"
        postureFlags.isNotEmpty() ->
            "머리, 몸통 또는 발 정렬에 좌우 차이가 있을 수 있다"
        else ->
            "아직 자세 분석 결과를 확인하지 못했다"
    }

    fun buildConsultationSummary(
        selectedPainIds: Set<String>,
        selectedSvgRegionLabels: List<String>,
        postureFlags: Set<String>
    ): String {
        val painPart = selectedPainIds
            .mapNotNull { PAIN_REGIONS_BY_ID[it]?.fullLabel }
            .joinToString(", ")
        val svgPart = selectedSvgRegionLabels.joinToString(", ")
        val posturePart = postureSummaryPhrase(postureFlags)

        val opening = when {
            painPart.isNotEmpty() && svgPart.isNotEmpty() ->
                "저는 $painPart 부위에 불편감이 있고, 특히 $svgPart 쪽이 신경 쓰입니다."
            painPart.isNotEmpty() ->
                "저는 $painPart 부위에 불편감이 있습니다."
            svgPart.isNotEmpty() ->
                "저는 $svgPart 쪽에 불편감이 있습니다."
            else ->
                "현재 뚜렷한 통증 부위를 표시하지는 않았지만, 자세 관리에 관심이 있습니다."
        }

        return "$opening 사진상 자세 분석에서는 ${posturePart}는 안내를 받았습니다. " +
            "통증이 언제부터 시작되었고 어떤 동작에서 심해지는지 상담받고 싶습니다."
    }
}
