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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Landing, PainSelection, BodyMapSelection, ImageUpload, AnalysisResult, ConsultationGuide, ExerciseRecommendation
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
            onBack = { currentScreen = AppScreen.AnalysisResult }
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
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AlignFit",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "AI 기반 자세 분석 및 홈케어 가이드",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(72.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("자세 교정 시작하기", fontSize = 16.sp)
        }
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
        ScreenTopBar(title = "통증 부위 선택", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "현재 통증이 느껴지는 부위를 선택해 주세요. 선택 없이도 계속할 수 있습니다.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
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
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("다음", fontSize = 16.sp)
            }
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
        ScreenTopBar(title = "사진 업로드", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val previewBgColor = MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(previewBgColor, RoundedCornerShape(12.dp)),
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
                        text = "사진을 선택해 주세요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("사진 올리기")
            }

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
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isLoading) "분석 중..." else "분석하기", fontSize = 16.sp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("← 뒤로", fontSize = 14.sp)
        }
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
