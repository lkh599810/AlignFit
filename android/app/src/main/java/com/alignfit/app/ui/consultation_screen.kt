package com.alignfit.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.network.AnalysisResponse

// ─── Consultation memo ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
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
        SubPageHeader(
            title = "상담 메모",
            subtitle = "‘어느 부위가, 언제부터, 어떤 동작에서 더 아픈지’를 먼저 이야기하면 좋아요.",
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
        ) {
            Panel {
                Text("이렇게 말해 보세요", fontSize = 13.sp, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "“$consultationSummary”",
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink
                )
            }

            val areas = selectedPainIds.sorted().map { HomecareDictionary.painLabel(it) } + svgLabels
            if (areas.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionLabel("선택한 부위")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    areas.distinct().forEach { Tag(it) }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel("자세 분석 요약")
            Text(
                text = if (result != null && result.landmarkDetected) {
                    result.simpleSummary
                } else {
                    "아직 분석 결과가 없거나 자세를 인식하지 못했어요. 분석 없이도 아래 내용을 활용할 수 있어요."
                },
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Ink
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel("함께 정리하면 좋은 내용")
            HomecareDictionary.CONSULTATION_TEMPLATE.talkingPoints.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(item, fontSize = 14.sp, lineHeight = 20.sp, color = Ink)
                }
            }

            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Ink, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Text("이런 증상이 있다면", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(10.dp))
                HomecareDictionary.CONSULTATION_TEMPLATE.redFlags.forEach { flag ->
                    Text(
                        text = "·  $flag",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Ink,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "빠른 시일 내에 의료 전문가 진료를 권장합니다.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
