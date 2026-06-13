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
                OverallSummaryCard(result = result, hasPostureIssue = hasPostureIssue)
                if (result.landmarkDetected) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailedFindingsCard(result = result)
                    if (hasPainSelection) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PainConnectionCard(result = result, hasPostureIssue = hasPostureIssue)
                    }
                }
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
private fun OverallSummaryCard(result: AnalysisResponse, hasPostureIssue: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "전체 요약",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (!result.landmarkDetected) {
                Text(
                    text = "사진에서 자세를 인식하지 못했습니다. 전신이 잘 보이는 밝은 정면 사진으로 다시 시도해 주세요.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = buildOverallSummary(result, hasPostureIssue),
                    fontSize = 13.sp,
                    color = if (hasPostureIssue) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DetailedFindingsCard(result: AnalysisResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "자세 세부 관찰",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

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
            Spacer(modifier = Modifier.height(10.dp))

            val findings = detailedFindings(result)
            if (findings.isEmpty()) {
                Text(
                    text = result.simpleSummary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                findings.forEach { (label, sentence) ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(76.dp)
                        )
                        Text(
                            text = sentence,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PainConnectionCard(result: AnalysisResponse, hasPostureIssue: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "통증 부위와 함께 보면",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildPainConnectionText(result, hasPostureIssue),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// Short metric-table value for head/trunk tilt. Three base states:
// direction null (the connected backend does not send the field) → "분석 정보 없음";
// "not_detected" → "분석 불가"; otherwise the directional short label with the
// absolute angle.
private fun tiltShortValue(direction: String?, angleDegrees: Double?): String {
    val angleText = angleDegrees?.let { " (%.1f°)".format(kotlin.math.abs(it)) } ?: ""
    return when (direction) {
        null -> "분석 정보 없음"
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
        null -> "분석 정보 없음"
        "not_detected" -> "분석 불가"
        "balanced" -> "큰 차이 없음"
        "left_more_outward" -> "왼쪽 더 벌어짐$diffText"
        "right_more_outward" -> "오른쪽 더 벌어짐$diffText"
        "uncertain" -> "차이 있음 (방향 불확실)$diffText"
        else -> status
    }
}

// Per-metric natural-language finding sentences for the detail card, using the
// backend's direction-specific summaries. Empty when the connected backend is
// an older version without the summary fields (the caller then falls back to
// simple_summary).
private fun detailedFindings(result: AnalysisResponse): List<Pair<String, String>> {
    val findings = mutableListOf<Pair<String, String>>()
    result.shoulderDirectionSummary?.takeIf { it.isNotEmpty() }
        ?.let { findings += "어깨" to it }
    result.hipDirectionSummary?.takeIf { it.isNotEmpty() }
        ?.let { findings += "골반" to it }
    result.headTiltSummary?.takeIf { it.isNotEmpty() }
        ?.let { findings += "머리 정렬" to it }
    result.trunkCenterlineSummary?.takeIf { it.isNotEmpty() }
        ?.let { findings += "몸통 중심선" to it }
    result.footDirectionSummary?.takeIf { it.isNotEmpty() }
        ?.let { findings += "발 방향" to it }
    return findings
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

// Overall impression paragraph for the summary card.
// Structure: overall impression -> single most noticeable finding.
private fun buildOverallSummary(result: AnalysisResponse, hasPostureIssue: Boolean): String {
    if (!hasPostureIssue) {
        return "사진상 어깨와 골반 정렬은 큰 불균형 없이 비교적 안정적으로 보입니다. " +
            "지금의 균형이 잘 유지되도록 가벼운 스트레칭과 규칙적인 움직임을 이어가 보세요."
    }

    val shoulderSide = directionToNaturalKorean(result.shoulderDirection)
    val hipSide = directionToNaturalKorean(result.hipDirection)
    val sentences = mutableListOf<String>()

    sentences += when {
        shoulderSide != null && hipSide != null ->
            "사진상 어깨와 골반 정렬에 좌우 차이가 조금 있어 보입니다."
        shoulderSide != null ->
            "사진상 어깨 정렬에 좌우 차이가 조금 있어 보입니다."
        hipSide != null ->
            "사진상 골반 정렬에 좌우 차이가 조금 있어 보입니다."
        else ->
            "사진상 자세에 조금 신경 쓰이는 부분이 보입니다. 아래 세부 내용을 함께 확인해 보세요."
    }

    if (shoulderSide != null &&
        result.shoulderHeightDifference >= result.hipHeightDifference
    ) {
        val severity = severityToNaturalKorean(result.shoulderHeightDifference)
        sentences += "특히 ${shoulderSide} 어깨가 반대쪽보다 ${severity} 높게 위치해 보입니다."
    } else if (hipSide != null) {
        val severity = severityToNaturalKorean(result.hipHeightDifference)
        sentences += "특히 ${hipSide} 골반이 반대쪽보다 ${severity} 높게 위치해 보입니다."
    }

    return sentences.joinToString(" ")
}

// Sentence connecting the selected pain areas with the posture findings.
private fun buildPainConnectionText(result: AnalysisResponse, hasPostureIssue: Boolean): String {
    if (!hasPostureIssue) {
        return "선택한 통증 부위와 함께 보면, 자세보다는 운동량, 수면 자세, " +
            "오래 앉아 있는 습관 등이 영향을 주었을 수 있습니다."
    }
    val side = directionToNaturalKorean(result.hipDirection)
        ?: directionToNaturalKorean(result.shoulderDirection)
    return if (side != null) {
        "선택한 통증 부위와 함께 보면, ${side}으로 치우친 부분에 부담이 더해졌을 가능성이 있습니다. " +
            "한쪽으로 체중을 싣거나 기울여 앉는 습관이 있는지 함께 살펴보면 좋습니다."
    } else {
        "선택한 통증 부위와 함께 보면, 해당 부위에 부담이 더해졌을 가능성이 있습니다."
    }
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
