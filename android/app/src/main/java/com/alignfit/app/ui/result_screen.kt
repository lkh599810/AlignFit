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
                label = "어깨 높이 차이",
                value = "%.1f%%".format(result.shoulderHeightDifference * 100)
            )
            MetricRow(
                label = "골반 높이 차이",
                value = "%.1f%%".format(result.hipHeightDifference * 100)
            )
            MetricRow(label = "머리 기울기", value = "추가 분석 예정")
            MetricRow(label = "몸통 중심선", value = "추가 분석 예정")
            MetricRow(label = "발 방향 차이", value = "추가 분석 예정")

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

            if (!hasPostureIssue) {
                Text(
                    text = "사진상 자세에서는 큰 불균형이 없어 보입니다.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "다만 통증이 지속된다면 자세 이외의 원인도 가능하므로 전문가 상담을 권장합니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = result.simpleSummary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hasPainSelection) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "선택한 통증 부위와 자세 분석 결과를 함께 보면, " +
                                "특정 부위의 부담이 증가했을 가능성이 있습니다.",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
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
