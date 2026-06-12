package com.alignfit.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.network.AnalysisResponse

// ─── Page 6: Consultation Guide ───────────────────────────────────────────────

@Composable
internal fun ConsultationGuideScreen(
    result: AnalysisResponse?,
    selectedPainIds: Set<String>,
    bodyMapSelections: Set<String>,
    onBack: () -> Unit
) {
    val postureFlags = remember(result) { HomecareDictionary.inferPostureFlags(result) }
    val svgLabels = remember(bodyMapSelections) {
        bodyMapSelections.sorted().map { HomecareDictionary.svgRegionLabel(it) }
    }
    val consultationSummary = remember(selectedPainIds, bodyMapSelections, result) {
        HomecareDictionary.buildConsultationSummary(selectedPainIds, svgLabels, postureFlags)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "증상 상담 준비", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Selected pain items
            if (selectedPainIds.isNotEmpty()) {
                GuideCard(title = "선택한 통증 부위") {
                    selectedPainIds.sorted().forEach { painId ->
                        Text(
                            text = "• ${HomecareDictionary.painLabel(painId)}",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Selected body map regions
            if (bodyMapSelections.isNotEmpty()) {
                GuideCard(title = "바디맵에서 선택한 부위") {
                    svgLabels.forEach { label ->
                        Text(
                            text = "• $label",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Posture summary from analysis
            GuideCard(title = "사진 자세 분석 요약") {
                Text(
                    text = if (result != null && result.landmarkDetected) {
                        result.simpleSummary
                    } else {
                        "아직 분석 결과가 없거나 랜드마크를 감지하지 못했습니다. " +
                            "자세 분석 없이도 아래 상담 준비 내용을 활용할 수 있습니다."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Generated consultation summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "의사/물리치료사에게 이렇게 말해볼 수 있습니다",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "“$consultationSummary”",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Talking points
            GuideCard(title = "상담 시 함께 정리하면 좋은 내용") {
                HomecareDictionary.CONSULTATION_TEMPLATE.talkingPoints.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "✓  ",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(item, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Red flags
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "주의가 필요한 증상",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HomecareDictionary.CONSULTATION_TEMPLATE.redFlags.forEach { flag ->
                        Text(
                            text = "• $flag",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "이런 증상이 있다면 빠른 시일 내에 의료 전문가 진료를 권장합니다.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "향후 업데이트 예정: LLM API를 이용해 의사 상담용 요약문을 더 자연스럽게 자동 생성",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuideCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
