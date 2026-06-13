package com.alignfit.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.network.AnalysisResponse

// ─── Page 5: Posture Analysis Result ─────────────────────────────────────────

@Composable
internal fun AnalysisResultScreen(
    result: AnalysisResponse?,
    displayBitmap: Bitmap?,
    selectedPainIds: Set<String>,
    bodyMapSelections: Set<String>,
    onExercise: () -> Unit,
    onConsultation: () -> Unit,
    onBack: () -> Unit
) {
    val postureFlags = remember(result) { HomecareDictionary.inferPostureFlags(result) }
    val hasPainSelection = selectedPainIds.isNotEmpty() || bodyMapSelections.isNotEmpty()
    val hasPostureIssue = HomecareDictionary.hasPostureIssue(postureFlags)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "자세 분석 결과", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            displayBitmap?.let { bmp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "자세 분석 이미지",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (hasPainSelection) {
                SelectionSummaryCard(
                    selectedPainIds = selectedPainIds,
                    bodyMapSelections = bodyMapSelections
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (result != null) {
                ResultCard(
                    result = result,
                    postureFlags = postureFlags,
                    hasPainSelection = hasPainSelection
                )
            } else {
                Text("분석 결과가 없습니다.", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hasPainSelection) {
                OutlinedButton(
                    onClick = onConsultation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("증상 알아보기")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (hasPostureIssue) {
                Button(
                    onClick = onExercise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("추천 운동 보러가기", fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            result?.cautionMessage?.let { caution ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = caution,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectionSummaryCard(
    selectedPainIds: Set<String>,
    bodyMapSelections: Set<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "선택한 불편 부위",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (selectedPainIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "통증 체크: " + selectedPainIds.sorted()
                        .joinToString(", ") { HomecareDictionary.painLabel(it) },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (bodyMapSelections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "바디맵 선택: " + bodyMapSelections.sorted()
                        .joinToString(", ") { HomecareDictionary.svgRegionLabel(it) },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: AnalysisResponse,
    postureFlags: Set<String>,
    hasPainSelection: Boolean
) {
    val hasPostureIssue = HomecareDictionary.hasPostureIssue(postureFlags)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "자세 분석 결과",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!result.landmarkDetected) {
                Text(
                    text = "신체 랜드마크를 감지하지 못했습니다. 전신이 잘 보이는 밝은 정면 사진을 사용해 주세요.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                return@Column
            }

            MetricRow(
                label = "어깨 좌우 높이",
                value = "%.1f%%".format(result.shoulderHeightDifference * 100)
            )
            MetricRow(
                label = "골반 좌우 높이",
                value = "%.1f%%".format(result.hipHeightDifference * 100)
            )
            MetricRow(
                label = "머리 정렬",
                value = tiltShortValue(result.headTiltDirection, result.headTiltAngleDegrees)
            )
            MetricRow(
                label = "몸통 중심 정렬",
                value = tiltShortValue(result.trunkTiltDirection, result.trunkCenterlineAngleDegrees)
            )
            MetricRow(
                label = "발 방향 좌우 차이",
                value = footShortValue(result.footDirectionStatus, result.footDirectionDifferenceDegrees)
            )

            Spacer(modifier = Modifier.height(8.dp))
            val dividerColor = MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Observed posture flags (only flags inferable from the current backend)
            postureFlags.mapNotNull { HomecareDictionary.POSTURE_FLAGS_BY_ID[it] }
                .forEach { flag ->
                    Text(
                        text = "• ${flag.label}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasPostureIssue) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = buildNaturalPostureSummary(result, hasPostureIssue, hasPainSelection),
                fontSize = 13.sp,
                color = if (hasPostureIssue) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Short metric-table value for head/trunk tilt. Three base states:
// direction null (older backend) → "추가 분석 예정"; "not_detected" → "분석 불가";
// otherwise the directional short label with the absolute angle.
private fun tiltShortValue(direction: String?, angleDegrees: Double?): String {
    val angleText = angleDegrees?.let { " (%.1f°)".format(kotlin.math.abs(it)) } ?: ""
    return when (direction) {
        null -> "추가 분석 예정"
        "not_detected" -> "분석 불가"
        "balanced" -> "큰 차이 없음"
        "left" -> "왼쪽 기울어짐$angleText"
        "right" -> "오른쪽 기울어짐$angleText"
        else -> direction
    }
}

private fun footShortValue(status: String?, differenceDegrees: Double?): String {
    val diffText = differenceDegrees?.let { " (%.1f°)".format(it) } ?: ""
    return when (status) {
        null -> "추가 분석 예정"
        "not_detected" -> "분석 불가"
        "balanced" -> "큰 차이 없음"
        "left_more_outward" -> "왼쪽 더 벌어짐$diffText"
        "right_more_outward" -> "오른쪽 더 벌어짐$diffText"
        "uncertain" -> "차이 관찰 (불확실)$diffText"
        else -> status
    }
}

// Joins the backend's directional sentences (including not-detected
// explanations) into the explanation paragraph. Returns null when the
// backend is an older version without direction fields.
private fun buildDirectionalExplanation(result: AnalysisResponse): String? {
    if (result.shoulderDirection == null && result.headTiltDirection == null &&
        result.trunkTiltDirection == null && result.footDirectionStatus == null
    ) {
        return null
    }
    val sentences = mutableListOf<String>()
    if (result.shoulderDirection != null && result.shoulderDirection != "balanced") {
        result.shoulderDirectionSummary?.takeIf { it.isNotEmpty() }?.let { sentences += it }
    }
    if (result.hipDirection != null && result.hipDirection != "balanced") {
        result.hipDirectionSummary?.takeIf { it.isNotEmpty() }?.let { sentences += it }
    }
    if (result.headTiltDirection != null && result.headTiltDirection != "balanced") {
        result.headTiltSummary?.takeIf { it.isNotEmpty() }?.let { sentences += it }
    }
    if (result.trunkTiltDirection != null && result.trunkTiltDirection != "balanced") {
        result.trunkCenterlineSummary?.takeIf { it.isNotEmpty() }?.let { sentences += it }
    }
    if (result.footDirectionStatus != null && result.footDirectionStatus != "balanced") {
        result.footDirectionSummary?.takeIf { it.isNotEmpty() }?.let { sentences += it }
    }
    return sentences.joinToString(" ").ifEmpty { null }
}

// Maps a normalized shoulder/hip height difference to a soft Korean adverb
// describing how noticeable the difference looks.
private fun severityToNaturalKorean(diff: Double): String = when {
    diff < 0.015 -> "살짝"
    diff < 0.03 -> "약간"
    else -> "꽤"
}

// Maps a left/right direction enum value to a natural Korean side word.
// Returns null for "balanced"/"not_detected"/unrecognized values.
private fun directionToNaturalKorean(direction: String?): String? = when (direction) {
    "left_higher", "left", "left_more_outward" -> "왼쪽"
    "right_higher", "right", "right_more_outward" -> "오른쪽"
    else -> null
}

// Builds the natural-language posture result paragraph shown in the result
// card. Structure: overall impression -> specific posture findings ->
// connection with selected pain areas -> caution sentence.
private fun buildNaturalPostureSummary(
    result: AnalysisResponse,
    hasPostureIssue: Boolean,
    hasPainSelection: Boolean
): String {
    val sentences = mutableListOf<String>()

    if (!hasPostureIssue) {
        sentences += "사진상 어깨와 골반 정렬은 큰 불균형 없이 비교적 안정적으로 보입니다."
    } else {
        val maxDiff = maxOf(result.shoulderHeightDifference, result.hipHeightDifference)
        val severity = severityToNaturalKorean(maxDiff)
        val overallSide = directionToNaturalKorean(result.shoulderDirection)
            ?: directionToNaturalKorean(result.hipDirection)
        sentences += if (overallSide != null) {
            "사진상 전반적으로 ${overallSide}으로 균형이 ${severity} 치우쳐 보입니다."
        } else {
            "사진상 전반적으로 ${severity} 신경 쓰이는 부분이 보입니다."
        }
        sentences += buildDirectionalExplanation(result) ?: result.simpleSummary
    }

    if (hasPainSelection) {
        sentences += if (hasPostureIssue) {
            val painSide = directionToNaturalKorean(result.hipDirection)
                ?: directionToNaturalKorean(result.shoulderDirection)
            if (painSide != null) {
                "선택한 통증 부위와 함께 보면, ${painSide}으로 치우친 부분에 부담이 더해졌을 가능성이 있습니다."
            } else {
                "선택한 통증 부위와 함께 보면, 해당 부위에 부담이 더해졌을 가능성이 있습니다."
            }
        } else {
            "다만 선택한 통증과 함께 보면, 자세 외에도 운동량, 수면 자세, 오래 앉아 있는 습관 등이 영향을 줄 수 있습니다."
        }
    } else if (!hasPostureIssue) {
        sentences += "다만 통증이 있다면 자세 외에도 운동량, 수면 자세, 오래 앉아 있는 습관 등이 영향을 줄 수 있습니다."
    }

    sentences += if (hasPostureIssue) {
        "이 결과는 사진 한 장을 바탕으로 한 참고용 분석이며, 통증이 지속되면 전문가 상담을 권장합니다."
    } else {
        "통증이 계속되거나 심해지면 전문가와 상담해보는 것이 좋습니다."
    }

    return sentences.joinToString(" ")
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
