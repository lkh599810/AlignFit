package com.alignfit.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.network.MAX_VIDEO_SECONDS
import com.alignfit.app.network.PredictResponse
import com.alignfit.app.network.RetrofitClient
import com.alignfit.app.network.VideoInfo
import com.alignfit.app.network.VideoRecommendation
import com.alignfit.app.network.buildVideoPart
import com.alignfit.app.network.queryVideoInfo
import com.alignfit.app.network.toUserMessage
import com.alignfit.app.network.videoLimitError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Movement evaluation tab ──────────────────────────────────────────────────
// Pick an exercise video -> POST /predict (streamed multipart) -> result cards.

@Composable
fun VideoEvaluationScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PredictResponse?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            videoUri = uri
            result = null
            errorText = ""
            coroutineScope.launch {
                val info = withContext(Dispatchers.IO) { queryVideoInfo(context, uri) }
                videoInfo = info
                videoLimitError(info)?.let { errorText = it }
            }
        }
    }
    val pickVideo = {
        if (!isLoading) {
            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(title = "움직임 평가")
        Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
            // Video picker slot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tint)
                    .clickable(onClick = pickVideo)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Paper),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (videoInfo == null) Icons.Filled.Add else Icons.Filled.PlayArrow,
                        contentDescription = "영상 선택",
                        tint = Ink
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val info = videoInfo
                    Text(
                        text = info?.displayName ?: "영상",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (info != null) {
                            val sizeMb = if (info.sizeBytes >= 0) "%.1fMB".format(info.sizeBytes / 1048576.0) else "-"
                            val duration = info.durationSeconds?.let { "${it}초" } ?: "-"
                            "$duration · $sizeMb"
                        } else "최대 ${MAX_VIDEO_SECONDS}초",
                        fontSize = 12.sp,
                        color = InkSoft
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (isLoading) "분석 중…" else "분석하기",
                enabled = videoUri != null && !isLoading &&
                    videoInfo?.let { videoLimitError(it) } == null,
                onClick = {
                    val uri = videoUri ?: return@PrimaryButton
                    val info = videoInfo ?: return@PrimaryButton
                    isLoading = true
                    errorText = ""
                    result = null
                    coroutineScope.launch {
                        try {
                            val part = buildVideoPart(context, uri, info)
                            result = RetrofitClient.apiService.predictVideo(part)
                        } catch (e: Exception) {
                            errorText = e.toUserMessage()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )

            if (isLoading) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp)),
                    color = Ink,
                    trackColor = Hairline
                )
            }

            if (errorText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                NoteBox(text = errorText)
            }

            result?.let { VideoResult(it) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VideoResult(r: PredictResponse) {
    val quality = r.movementQuality
    val exercise = r.predictedExercise
    Spacer(Modifier.height(24.dp))

    if (!r.poseDetected || quality == null || exercise == null) {
        Panel { Text(r.message, fontSize = 14.sp, lineHeight = 21.sp, color = Ink) }
        Spacer(Modifier.height(12.dp))
        NoteBox(r.cautionMessage)
        return
    }

    val attention = quality.status == "needs_attention"
    Panel {
        Tag(text = if (attention) "움직임 주의 필요" else "움직임 양호", inverted = true)
        Spacer(Modifier.height(12.dp))
        Text(r.message, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
    }

    Spacer(Modifier.height(28.dp))
    SectionLabel("감지된 동작")
    Text(exercise.exerciseNameKo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
    Spacer(Modifier.height(6.dp))
    Tag(exercise.targetRegionKo)
    Spacer(Modifier.height(8.dp))
    Text(exercise.posturePatternKo, fontSize = 14.sp, lineHeight = 21.sp, color = InkSoft)

    Spacer(Modifier.height(28.dp))
    SectionLabel("추천 운동")
    if (r.recommendations.isEmpty()) {
        Text("추천 항목이 없습니다.", fontSize = 14.sp, color = InkSoft)
    }
    r.recommendations.forEach { rec ->
        RecommendationItem(rec)
        Spacer(Modifier.height(10.dp))
    }

    Spacer(Modifier.height(18.dp))
    SectionLabel("분석 정보")
    KeyValueRow("움직임 품질 신뢰도", "${(quality.confidence * 100).toInt()}%")
    KeyValueRow("운동 분류 신뢰도", "${(exercise.confidence * 100).toInt()}%")
    KeyValueRow("분석 프레임", "${r.framesUsed}", showDivider = false)

    Spacer(Modifier.height(16.dp))
    NoteBox(r.cautionMessage)
}

@Composable
private fun RecommendationItem(rec: VideoRecommendation) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(rec.exerciseName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
        rec.relatedBodyPart?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, fontSize = 12.sp, color = InkFaint)
        }
        rec.reason?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = Ink)
        }
        rec.cautions?.let {
            Spacer(Modifier.height(6.dp))
            Text("주의 · $it", fontSize = 12.sp, lineHeight = 18.sp, color = InkSoft)
        }
        Spacer(Modifier.height(12.dp))
        val query = rec.youtubeQuery?.takeIf { it.isNotBlank() } ?: "${rec.exerciseName} 운동"
        SecondaryButton(
            text = "YouTube",
            icon = Icons.Filled.PlayArrow,
            onClick = {
                val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        )
    }
}
