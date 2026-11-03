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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.network.AnalysisResponse

// ─── Analysis Result ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnalysisResultScreen(
    result: AnalysisResponse?,
    displayBitmap: Bitmap?,
    selectedPainIds: Set<String>,
    bodyMapSelections: Set<String>,
    onExercise: () -> Unit,
    onConsultation: () -> Unit,
    onRestart: () -> Unit
) {
    val postureFlags = remember(result) { HomecareDictionary.inferPostureFlags(result) }
    val hasPainSelection = selectedPainIds.isNotEmpty() || bodyMapSelections.isNotEmpty()
    val hasPostureIssue = HomecareDictionary.hasPostureIssue(postureFlags)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(title = "분석 결과", subtitle = "사진을 바탕으로 한 참고용 자세 분석이에요.")
        Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
            displayBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "자세 분석 이미지",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Tint),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(16.dp))
            }

            if (result == null) {
                NoteBox("분석 결과가 없습니다.")
            } else {
                Panel {
                    Tag(
                        text = when {
                            !result.landmarkDetected -> "인식 실패"
                            hasPostureIssue -> "살펴볼 부분 있음"
                            else -> "균형 안정적"
                        },
                        inverted = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (!result.landmarkDetected) {
                            "사진에서 자세를 인식하지 못했어요. 전신이 잘 보이는 밝은 정면 사진으로 다시 시도해 주세요."
                        } else {
                            buildOverallSummary(result, hasPostureIssue)
                        },
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink
                    )
                }

                if (result.landmarkDetected) {
                    Spacer(Modifier.height(28.dp))
                    SectionLabel("측정값")
                    val metrics = listOf(
                        "어깨 좌우 높이" to "%.1f%%".format(result.shoulderHeightDifference * 100),
                        "골반 좌우 높이" to "%.1f%%".format(result.hipHeightDifference * 100),
                        "머리 정렬" to tiltShortValue(result.headTiltDirection, result.headTiltAngleDegrees),
                        "몸통 중심" to tiltShortValue(result.trunkTiltDirection, result.trunkCenterlineAngleDegrees),
                        "발 방향" to footShortValue(result.footDirectionStatus, result.footDirectionDifferenceDegrees)
                    )
                    metrics.forEachIndexed { i, (label, value) ->
                        KeyValueRow(label, value, showDivider = i < metrics.lastIndex)
                    }

                    Spacer(Modifier.height(28.dp))
                    SectionLabel("자세히 보기")
                    val findings = detailedFindings(result)
                    if (findings.isEmpty()) {
                        Text(result.simpleSummary, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
                    } else {
                        findings.forEach { (label, sentence) ->
                            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Spacer(Modifier.height(2.dp))
                                Text(sentence, fontSize = 14.sp, lineHeight = 21.sp, color = InkSoft)
                            }
                        }
                    }
                }
            }

            if (hasPainSelection) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("선택한 부위")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (selectedPainIds.sorted().map { HomecareDictionary.painLabel(it) } +
                        bodyMapSelections.sorted().map { HomecareDictionary.svgRegionLabel(it) })
                        .distinct()
                        .forEach { Tag(it) }
                }
                if (result != null && result.landmarkDetected) {
                    Spacer(Modifier.height(16.dp))
                    Panel {
                        Text("통증 부위와 함께 보면", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildPainConnectionText(result, hasPostureIssue),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = InkSoft
                        )
                    }
                }
            }

            result?.cautionMessage?.let { caution ->
                Spacer(Modifier.height(20.dp))
                NoteBox(caution)
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(text = "추천 운동 보기", onClick = onExercise)
            if (hasPainSelection) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "상담 메모 만들기", onClick = onConsultation)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                Text("다시 분석하기", fontSize = 14.sp, color = InkSoft)
            }
            Spacer(Modifier.height(16.dp))
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

