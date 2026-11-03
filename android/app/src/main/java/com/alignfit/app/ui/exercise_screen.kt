package com.alignfit.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// ─── Exercise tab ─────────────────────────────────────────────────────────────
// Personalised picks on top (from pain selection + photo analysis), then the
// whole exercise library as collapsible rows.

@Composable
internal fun ExerciseRecommendationScreen(
    result: AnalysisResponse?,
    selectedPainIds: Set<String>,
    bodyMapSelections: Set<String>,
    onStartAnalysis: () -> Unit
) {
    val hasInput = result != null || selectedPainIds.isNotEmpty() || bodyMapSelections.isNotEmpty()
    val recommendations = remember(result, selectedPainIds, bodyMapSelections) {
        val postureFlags = HomecareDictionary.inferPostureFlags(result)
        HomecareDictionary.getRecommendations(selectedPainIds, bodyMapSelections, postureFlags)
    }
    val recommendedIds = recommendations.exercises.map { it.id }.toSet()
    var expandedId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(title = "운동")
        Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
            if (!hasInput) {
                Panel(onClick = onStartAnalysis) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Face, contentDescription = null, tint = Ink)
                        Spacer(Modifier.width(12.dp))
                        Text("자세 분석", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ink)
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                recommendations.feedbackCards.forEach { rule ->
                    Panel {
                        Text(rule.feedbackTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(rule.feedbackBody, fontSize = 14.sp, lineHeight = 21.sp, color = InkSoft)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(16.dp))
            }

            SectionLabel("추천")
            recommendations.exercises.forEach { exercise ->
                ExerciseCard(exercise = exercise)
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("전체")
            val others = HomecareDictionary.EXERCISES.filter { it.id !in recommendedIds }
            others.forEachIndexed { i, exercise ->
                val expanded = expandedId == exercise.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedId = if (expanded) null else exercise.id }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(exercise.koreanName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            listOf(exercise.englishName, exercise.dose).filter { it.isNotEmpty() }.joinToString(" · "),
                            fontSize = 12.sp,
                            color = InkFaint
                        )
                    }
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "접기" else "펼치기",
                        tint = InkSoft
                    )
                }
                if (expanded) {
                    ExerciseCard(exercise = exercise, showTitle = false)
                    Spacer(Modifier.height(8.dp))
                }
                if (i < others.lastIndex) HorizontalDivider(thickness = 1.dp, color = Hairline)
            }

            Spacer(Modifier.height(24.dp))
            NoteBox(
                "운동 중 통증이 심해지거나 저림, 감각 이상, 힘 빠짐이 나타나면 즉시 중단하고 전문가 상담을 권장합니다.",
                title = "주의"
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseItem, showTitle: Boolean = true) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    val watchUrl = exercise.youtubeUrl
    val thumbnailUrl = exercise.youtubeThumbnailUrl
    val searchUrl = "https://www.youtube.com/results?search_query=" +
        Uri.encode(exercise.youtubeQuery)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        // Looping demo animation when keyframes exist; otherwise the
        // YouTube thumbnail / search placeholder.
        if (hasExerciseAnimation(context, exercise.id)) {
            ExerciseAnimation(exerciseId = exercise.id)
        } else if (watchUrl != null && thumbnailUrl != null) {
            YoutubeThumbnailPreview(thumbnailUrl = thumbnailUrl, onClick = { openUrl(watchUrl) })
        } else {
            PlaceholderPreview(exerciseName = exercise.koreanName, onClick = { openUrl(searchUrl) })
        }

        Spacer(Modifier.height(16.dp))
        if (showTitle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.koreanName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(exercise.englishName, fontSize = 12.sp, color = InkFaint)
                }
                if (exercise.dose.isNotEmpty()) Tag(exercise.dose, inverted = true)
            }
        } else if (exercise.dose.isNotEmpty()) {
            Tag(exercise.dose, inverted = true)
        }

        if (exercise.steps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            exercise.steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Tint),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(thickness = 1.dp, color = Hairline)
        Spacer(Modifier.height(12.dp))
        Text(exercise.reason, fontSize = 13.sp, lineHeight = 19.sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text("주의", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
        Spacer(Modifier.height(2.dp))
        Text(exercise.caution, fontSize = 13.sp, lineHeight = 19.sp, color = Ink)

        Spacer(Modifier.height(16.dp))
        SecondaryButton(
            text = "YouTube",
            icon = Icons.Filled.PlayArrow,
            onClick = { openUrl(watchUrl ?: searchUrl) }
        )
    }
}

@Composable
private fun YoutubeThumbnailPreview(thumbnailUrl: String, onClick: () -> Unit) {
    val thumbnail = rememberThumbnailBitmap(thumbnailUrl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
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
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Paper),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "재생", tint = Ink)
        }
    }
}

@Composable
private fun PlaceholderPreview(exerciseName: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Tint)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "$exerciseName 영상", tint = Ink)
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
