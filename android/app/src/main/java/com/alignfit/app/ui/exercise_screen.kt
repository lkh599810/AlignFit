package com.alignfit.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.ExerciseItem
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.network.AnalysisResponse

// ─── Page 7: Exercise Recommendation ─────────────────────────────────────────

@Composable
internal fun ExerciseRecommendationScreen(
    result: AnalysisResponse?,
    selectedPainIds: Set<String>,
    bodyMapSelections: Set<String>,
    onBack: () -> Unit
) {
    val recommendations = remember(result, selectedPainIds, bodyMapSelections) {
        val postureFlags = HomecareDictionary.inferPostureFlags(result)
        HomecareDictionary.getRecommendations(selectedPainIds, bodyMapSelections, postureFlags)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "추천 운동", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Matched feedback cards (top rules by priority)
            recommendations.feedbackCards.forEach { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = rule.feedbackTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rule.feedbackBody,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = "추천 운동",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            recommendations.exercises.forEach { exercise ->
                ExerciseCard(exercise = exercise)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // TODO: Add optional product links for foam roller, massage ball, resistance band later.

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "운동 중 통증이 심해지거나 저림, 감각 이상, 힘 빠짐이 나타나면 " +
                        "즉시 중단하고 전문가 상담을 권장합니다.",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseItem) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = exercise.koreanName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = exercise.englishName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "추천 이유: ${exercise.reason}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "주의사항: ${exercise.caution}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val query = Uri.encode(exercise.youtubeQuery)
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=$query")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("YouTube에서 검색하기", fontSize = 13.sp)
            }
        }
    }
}
