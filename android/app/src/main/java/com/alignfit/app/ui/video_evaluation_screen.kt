package com.alignfit.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// ─── AI exercise evaluation (native) ──────────────────────────────────────────
// Pick an exercise video -> POST /predict (streamed multipart) -> native result
// cards. Replaces the former WebView wrapper around the backend's /demo page.

@Composable
fun VideoEvaluationScreen(onBack: () -> Unit) {
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

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "재활운동 평가", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScreenSubtitle(
                text = "전신이 잘 보이도록 촬영한 운동 영상(${com.alignfit.app.network.MAX_VIDEO_SECONDS}초 이하)을 " +
                    "올리면 움직임 상태를 분석해 참고용 피드백을 드려요.",
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val info = videoInfo
                    Text(
                        text = info?.displayName ?: "아직 선택된 영상이 없어요",
                        fontSize = 14.sp,
                        fontWeight = if (info != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (info != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val sizeMb = if (info.sizeBytes >= 0) "%.1fMB".format(info.sizeBytes / 1048576.0) else "크기 알 수 없음"
                        val duration = info.durationSeconds?.let { "${it}초" } ?: "길이 알 수 없음"
                        Text(
                            text = "$duration · $sizeMb",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SecondaryButton(
                text = "운동 영상 선택",
                enabled = !isLoading,
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            PrimaryButton(
                text = if (isLoading) "분석 중..." else "영상 분석하기",
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
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "영상을 업로드하고 분석하는 중입니다.\n영상 길이에 따라 1~2분 걸릴 수 있어요.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            result?.let { VideoResult(it) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VideoResult(r: PredictResponse) {
    val quality = r.movementQuality
    val exercise = r.predictedExercise
    Spacer(modifier = Modifier.height(16.dp))

    if (!r.poseDetected || quality == null || exercise == null) {
        ResultCard {
            Text(r.message, fontSize = 14.sp, lineHeight = 21.sp)
        }
        Disclaimer(r.cautionMessage)
        return
    }

    val attention = quality.status == "needs_attention"
    ResultCard {
        val (bg, fg) = if (attention) Color(0xFFFBF0E2) to Color(0xFFC4761B)
        else MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Text(
            text = if (attention) "움직임 주의 필요" else "움직임 양호",
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier
                .background(bg, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(r.message, fontSize = 14.sp, lineHeight = 21.sp)
    }

    ResultCard {
        SectionTitle("감지된 동작")
        Text(exercise.exerciseNameKo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = exercise.targetRegionKo,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(exercise.posturePatternKo, fontSize = 13.sp)
    }

    ResultCard {
        SectionTitle("추천 홈케어 운동")
        if (r.recommendations.isEmpty()) {
            Text("추천 항목이 없습니다.", fontSize = 13.sp)
        }
        r.recommendations.forEachIndexed { i, rec ->
            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            RecommendationItem(rec)
        }
    }

    ResultCard {
        SectionTitle("분석 정보")
        InfoRow("움직임 품질 신뢰도", "${(quality.confidence * 100).toInt()}%")
        InfoRow("운동 분류 신뢰도", "${(exercise.confidence * 100).toInt()}%")
        InfoRow("분석 프레임", "${r.framesUsed}")
    }

    Disclaimer(r.cautionMessage)
}

@Composable
private fun RecommendationItem(rec: VideoRecommendation) {
    val context = LocalContext.current
    Text(rec.exerciseName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    rec.relatedBodyPart?.let {
        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
    rec.reason?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(it, fontSize = 13.sp, lineHeight = 19.sp)
    }
    rec.cautions?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "주의: $it",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    val query = rec.youtubeQuery?.takeIf { it.isNotBlank() } ?: "${rec.exerciseName} 운동"
    OutlinedButton(
        onClick = {
            val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Text("YouTube에서 동작 보기", fontSize = 13.sp)
    }
}

@Composable
private fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Disclaimer(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    )
}
