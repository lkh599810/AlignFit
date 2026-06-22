package com.alignfit.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.data.PainRegionGroup
import com.alignfit.app.network.AnalysisResponse
import com.alignfit.app.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

// ─── Navigation ───────────────────────────────────────────────────────────────

enum class AppScreen {
    Landing, PainSelection, BodyMapSelection, ImageUpload, AnalysisResult, ConsultationGuide, ExerciseRecommendation, AiExerciseEvaluation
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.Landing) }
    var selectedPainIds by remember { mutableStateOf(setOf<String>()) }
    var bodyMapSelections by remember { mutableStateOf(setOf<String>()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<AnalysisResponse?>(null) }
    var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    when (currentScreen) {
        AppScreen.Landing -> LandingScreen(
            onStart = { currentScreen = AppScreen.PainSelection }
        )
        AppScreen.PainSelection -> PainSelectionScreen(
            selectedPainIds = selectedPainIds,
            onPainToggled = { painId ->
                selectedPainIds =
                    if (painId in selectedPainIds) selectedPainIds - painId
                    else selectedPainIds + painId
            },
            onNext = { currentScreen = AppScreen.BodyMapSelection },
            onBack = { currentScreen = AppScreen.Landing }
        )
        AppScreen.BodyMapSelection -> BodyMapSelectionScreen(
            selectedRegions = bodyMapSelections,
            onRegionToggled = { regionId ->
                bodyMapSelections =
                    if (regionId in bodyMapSelections) bodyMapSelections - regionId
                    else bodyMapSelections + regionId
            },
            onNext = { currentScreen = AppScreen.ImageUpload },
            onBack = { currentScreen = AppScreen.PainSelection }
        )
        AppScreen.ImageUpload -> ImageUploadScreen(
            selectedImageUri = selectedImageUri,
            selectedImageBitmap = selectedImageBitmap,
            onImageSelected = { uri ->
                selectedImageUri = uri
                annotatedBitmap = null
                analysisResult = null
                coroutineScope.launch {
                    try {
                        val stream = context.contentResolver.openInputStream(uri)
                        selectedImageBitmap = BitmapFactory.decodeStream(stream)
                        stream?.close()
                    } catch (_: Exception) {
                        selectedImageBitmap = null
                    }
                }
            },
            onAnalysisComplete = { response, bmp ->
                analysisResult = response
                annotatedBitmap = bmp
                currentScreen = AppScreen.AnalysisResult
            },
            onBack = { currentScreen = AppScreen.BodyMapSelection }
        )
        AppScreen.AnalysisResult -> AnalysisResultScreen(
            result = analysisResult,
            displayBitmap = annotatedBitmap ?: selectedImageBitmap,
            selectedPainIds = selectedPainIds,
            bodyMapSelections = bodyMapSelections,
            onExercise = { currentScreen = AppScreen.ExerciseRecommendation },
            onConsultation = { currentScreen = AppScreen.ConsultationGuide },
            onRestart = {
                selectedPainIds = setOf()
                bodyMapSelections = setOf()
                selectedImageUri = null
                selectedImageBitmap = null
                analysisResult = null
                annotatedBitmap = null
                currentScreen = AppScreen.Landing
            },
            onBack = { currentScreen = AppScreen.ImageUpload }
        )
        AppScreen.ConsultationGuide -> ConsultationGuideScreen(
            result = analysisResult,
            selectedPainIds = selectedPainIds,
            bodyMapSelections = bodyMapSelections,
            onBack = { currentScreen = AppScreen.AnalysisResult }
        )
        AppScreen.ExerciseRecommendation -> ExerciseRecommendationScreen(
            result = analysisResult,
            selectedPainIds = selectedPainIds,
            bodyMapSelections = bodyMapSelections,
            onRehabEval = { currentScreen = AppScreen.AiExerciseEvaluation },
            onBack = { currentScreen = AppScreen.AnalysisResult }
        )
        AppScreen.AiExerciseEvaluation -> AiExerciseWebViewScreen(
            onBack = { currentScreen = AppScreen.ExerciseRecommendation }
        )
    }
}

// ─── Page 1: Landing ──────────────────────────────────────────────────────────

@Composable
private fun LandingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Soft gradient hero card with monogram badge, name, and subtitle.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "AlignFit",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "사진과 통증 부위를 바탕으로 자세 상태를 확인하고, " +
                        "부담이 적은 홈케어 운동을 추천해요.",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        PrimaryButton(text = "내 자세 확인하기", onClick = onStart)

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "의학적 진단이 아닌 참고용 자세 분석입니다.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Page 2: Pain Selection ───────────────────────────────────────────────────

@Composable
private fun PainSelectionScreen(
    selectedPainIds: Set<String>,
    onPainToggled: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "불편한 부위 선택", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            ScreenSubtitle(
                text = "지금 불편한 부위를 선택해 주세요. 선택하지 않고 넘어가도 괜찮아요.",
                modifier = Modifier.padding(vertical = 10.dp)
            )
            HomecareDictionary.PAIN_REGION_GROUPS.forEach { group ->
                PainRegionGroupCard(
                    group = group,
                    selectedPainIds = selectedPainIds,
                    onPainToggled = onPainToggled
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            PrimaryButton(text = "다음", onClick = onNext)
        }
    }
}

@Composable
private fun PainRegionGroupCard(
    group: PainRegionGroup,
    selectedPainIds: Set<String>,
    onPainToggled: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = group.regionLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            group.options.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { option ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPainToggled(option.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = option.id in selectedPainIds,
                                onCheckedChange = { onPainToggled(option.id) }
                            )
                            Text(option.locationLabel, fontSize = 13.sp)
                        }
                    }
                    if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── Page 4: Image Upload ─────────────────────────────────────────────────────

@Composable
private fun ImageUploadScreen(
    selectedImageUri: Uri?,
    selectedImageBitmap: Bitmap?,
    onImageSelected: (Uri) -> Unit,
    onAnalysisComplete: (AnalysisResponse, Bitmap?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImageSelected(uri)
            errorText = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "자세 사진", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScreenSubtitle(
                text = "전신이 잘 보이는 밝은 정면 사진일수록 분석이 정확해요.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
            val previewBgColor = MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(previewBgColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageBitmap != null) {
                    Image(
                        bitmap = selectedImageBitmap.asImageBitmap(),
                        contentDescription = "선택된 이미지",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = "아직 선택된 사진이 없어요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SecondaryButton(
                text = "자세 사진 선택",
                onClick = { imagePickerLauncher.launch("image/*") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val uri = selectedImageUri ?: return@Button
                    isLoading = true
                    errorText = ""
                    coroutineScope.launch {
                        try {
                            val stream = context.contentResolver.openInputStream(uri)
                            val bytes = stream?.readBytes()
                            stream?.close()
                            if (bytes == null) {
                                errorText = "이미지를 읽을 수 없습니다."
                                isLoading = false
                                return@launch
                            }
                            val mimeType =
                                context.contentResolver.getType(uri) ?: "image/jpeg"
                            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                            val part = MultipartBody.Part.createFormData(
                                "image", "upload.jpg", requestBody
                            )
                            val response = RetrofitClient.apiService.analyzeImage(part)
                            val annotatedBmp = response.annotatedImageBase64?.let { b64 ->
                                val decoded = Base64.decode(b64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                            }
                            isLoading = false
                            onAnalysisComplete(response, annotatedBmp)
                        } catch (e: Exception) {
                            isLoading = false
                            errorText = "분석 중 오류가 발생했습니다. 네트워크 연결 및 서버 상태를 확인해 주세요."
                        }
                    }
                },
                enabled = selectedImageUri != null && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isLoading) "분석 중..." else "자세 분석하기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "자세를 분석하는 중입니다...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─── Shared UI ────────────────────────────────────────────────────────────────

@Composable
internal fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← 이전", fontSize = 14.sp)
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
