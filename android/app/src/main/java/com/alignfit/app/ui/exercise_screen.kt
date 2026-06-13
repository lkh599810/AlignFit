package com.alignfit.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.ExerciseItem
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.network.AnalysisResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Lightweight client for thumbnail fetching only (no logging interceptor needed).
private val thumbnailHttpClient = OkHttpClient()

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
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "선택한 통증 부위와 자세 분석 결과를 함께 고려해, " +
                    "먼저 부담이 적은 홈케어 운동부터 추천합니다.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
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

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    val watchUrl = exercise.youtubeUrl
    val thumbnailUrl = exercise.youtubeThumbnailUrl
    val searchUrl = "https://www.youtube.com/results?search_query=" +
        Uri.encode(exercise.youtubeQuery)

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
            Spacer(modifier = Modifier.height(8.dp))

            // YouTube-style preview area: thumbnail when a fixed video id exists,
            // otherwise a placeholder preview that opens the search results.
            if (watchUrl != null && thumbnailUrl != null) {
                YoutubeThumbnailPreview(
                    thumbnailUrl = thumbnailUrl,
                    onClick = { openUrl(watchUrl) }
                )
            } else {
                PlaceholderPreview(
                    exerciseName = exercise.koreanName,
                    searchQuery = exercise.youtubeQuery,
                    onClick = { openUrl(searchUrl) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
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
                onClick = { openUrl(watchUrl ?: searchUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (watchUrl != null) "${exercise.koreanName} 영상 보기"
                    else "${exercise.koreanName} 영상 검색하기",
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun YoutubeThumbnailPreview(thumbnailUrl: String, onClick: () -> Unit) {
    val thumbnail = rememberThumbnailBitmap(thumbnailUrl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF202020))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "운동 영상 미리보기",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Play badge overlay (also serves as the loading placeholder)
        Box(
            modifier = Modifier
                .background(Color(0xCCCC0000), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "▶", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun PlaceholderPreview(
    exerciseName: String,
    searchQuery: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "▶ YouTube", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "탭하면 ‘$exerciseName’ 영상을 검색합니다",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "검색어: $searchQuery",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Fetches a thumbnail with the existing OkHttp dependency (no Coil in this
// project; per project rules new dependencies are not added without approval).
@Composable
private fun rememberThumbnailBitmap(url: String): Bitmap? {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                thumbnailHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()?.let {
                        BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }
            }.getOrNull()
        }
    }
    return bitmap
}
