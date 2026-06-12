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

// ─── Pain Symptom ─────────────────────────────────────────────────────────────

data class PainSymptom(val region: String, val location: String)

private val BODY_REGIONS = listOf("허리", "어깨", "고관절 및 골반", "허벅지", "종아리", "발목")
private val DEFAULT_PAIN_LOCATIONS = listOf("왼쪽 앞", "왼쪽 뒤", "오른쪽 앞", "오른쪽 뒤")
private val WAIST_PAIN_LOCATIONS = listOf("왼쪽", "오른쪽")

private fun painLocationsFor(region: String): List<String> =
    if (region == "허리") WAIST_PAIN_LOCATIONS else DEFAULT_PAIN_LOCATIONS

// ─── Analysis Thresholds (matching backend values) ────────────────────────────

private const val SHOULDER_THRESHOLD = 0.005
private const val HIP_THRESHOLD = 0.005

// ─── Exercise Data ────────────────────────────────────────────────────────────

private data class ExerciseItem(
    val name: String,
    val reason: String,
    val caution: String,
    val youtubeQuery: String
)

private val EXERCISE_MAP: Map<String, ExerciseItem> = mapOf(
    "chin_tuck" to ExerciseItem(
        name = "Chin Tuck",
        reason = "목과 상부 경추 정렬 개선에 도움이 될 수 있습니다.",
        caution = "동작 중 통증이 느껴지면 즉시 중단하세요.",
        youtubeQuery = "chin tuck exercise tutorial"
    ),
    "thoracic_extension" to ExerciseItem(
        name = "Thoracic Extension",
        reason = "흉추 가동성 향상에 도움이 될 수 있습니다.",
        caution = "무리한 과신전은 피하세요.",
        youtubeQuery = "thoracic extension stretch tutorial"
    ),
    "scapular_retraction" to ExerciseItem(
        name = "Scapular Retraction",
        reason = "어깨 안정성과 자세 균형 개선에 도움이 될 수 있습니다.",
        caution = "목에 긴장이 가지 않도록 주의하세요.",
        youtubeQuery = "scapular retraction exercise tutorial"
    ),
    "pec_stretch" to ExerciseItem(
        name = "Pec Stretch",
        reason = "흉근 긴장 완화 및 어깨 전방 이동 교정에 도움이 될 수 있습니다.",
        caution = "과도한 스트레칭은 어깨에 무리를 줄 수 있습니다.",
        youtubeQuery = "pec stretch exercise tutorial"
    ),
    "glute_bridge" to ExerciseItem(
        name = "Glute Bridge",
        reason = "둔근 및 요추 안정화 근육 강화에 도움이 될 수 있습니다.",
        caution = "허리가 과도하게 아치되지 않도록 주의하세요.",
        youtubeQuery = "glute bridge exercise tutorial"
    ),
    "dead_bug" to ExerciseItem(
        name = "Dead Bug",
        reason = "코어 안정화 및 요추 보호에 도움이 될 수 있습니다.",
        caution = "허리가 바닥에서 떨어지지 않도록 유지하세요.",
        youtubeQuery = "dead bug core exercise tutorial"
    ),
    "bird_dog" to ExerciseItem(
        name = "Bird Dog",
        reason = "척추 안정화와 균형 능력 향상에 도움이 될 수 있습니다.",
        caution = "골반이 기울지 않도록 주의하세요.",
        youtubeQuery = "bird dog exercise tutorial"
    ),
    "side_plank" to ExerciseItem(
        name = "Side Plank",
        reason = "측면 코어 강화 및 척추 안정성 향상에 도움이 될 수 있습니다.",
        caution = "어깨와 손목에 무리가 가지 않도록 주의하세요.",
        youtubeQuery = "side plank exercise tutorial"
    ),
    "hip_flexor_stretch" to ExerciseItem(
        name = "Hip Flexor Stretch",
        reason = "고관절 굴곡근 이완 및 골반 정렬 개선에 도움이 될 수 있습니다.",
        caution = "허리가 과신전되지 않도록 주의하세요.",
        youtubeQuery = "hip flexor stretch tutorial"
    ),
    "calf_stretch" to ExerciseItem(
        name = "Calf Stretch",
        reason = "하퇴 근육 이완 및 발목 유연성 향상에 도움이 될 수 있습니다.",
        caution = "발목에 통증이 있을 경우 강도를 줄이세요.",
        youtubeQuery = "calf stretch exercise tutorial"
    )
)

private fun selectExercises(
    result: AnalysisResponse?,
    selectedPains: Set<PainSymptom>,
    bodyMapSelections: Set<String>
): List<ExerciseItem> {
    val keys = mutableListOf<String>()

    result?.let { r ->
        if (r.landmarkDetected) {
            if (r.shoulderHeightDifference > SHOULDER_THRESHOLD) {
                keys += listOf("scapular_retraction", "pec_stretch", "chin_tuck", "thoracic_extension")
            }
            if (r.hipHeightDifference > HIP_THRESHOLD) {
                keys += listOf("glute_bridge", "hip_flexor_stretch", "dead_bug", "bird_dog")
            }
        }
    }

    selectedPains.map { it.region }.toSet().forEach { region ->
        when {
            region.contains("허리") -> keys += listOf("dead_bug", "bird_dog", "glute_bridge")
            region.contains("어깨") -> keys += listOf("scapular_retraction", "pec_stretch", "chin_tuck")
            region.contains("고관절") || region.contains("골반") ->
                keys += listOf("hip_flexor_stretch", "glute_bridge")
            region.contains("허벅지") -> keys += listOf("hip_flexor_stretch", "glute_bridge")
            region.contains("종아리") -> keys += listOf("calf_stretch")
            region.contains("발목") -> keys += listOf("calf_stretch")
        }
    }

    bodyMapSelections.forEach { regionId ->
        when {
            regionId.contains("neck") -> keys += listOf("chin_tuck")
            regionId.contains("upper_back") || regionId.contains("shoulder") ||
                regionId.contains("chest") ->
                keys += listOf("scapular_retraction", "pec_stretch", "thoracic_extension")
            regionId.contains("lower_back") || regionId.contains("waist") ||
                regionId.contains("abdomen") ->
                keys += listOf("dead_bug", "bird_dog", "glute_bridge")
            regionId.contains("glute") || regionId.contains("hip") ->
                keys += listOf("hip_flexor_stretch", "glute_bridge")
            regionId.contains("thigh") || regionId.contains("knee") ->
                keys += listOf("hip_flexor_stretch", "glute_bridge", "side_plank")
            regionId.contains("calf") || regionId.contains("ankle_foot") ->
                keys += listOf("calf_stretch")
            regionId.contains("arm") || regionId.contains("hand_wrist") ->
                keys += listOf("scapular_retraction", "pec_stretch")
        }
    }

    if (keys.isEmpty()) {
        keys += listOf("glute_bridge", "dead_bug", "bird_dog", "scapular_retraction")
    }

    return keys.distinct().mapNotNull { EXERCISE_MAP[it] }
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.Landing) }
    var selectedPains by remember { mutableStateOf(setOf<PainSymptom>()) }
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
            selectedPains = selectedPains,
            onPainToggled = { pain ->
                selectedPains =
                    if (pain in selectedPains) selectedPains - pain else selectedPains + pain
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
            selectedPains = selectedPains,
            bodyMapSelections = bodyMapSelections,
            onExercise = { currentScreen = AppScreen.ExerciseRecommendation },
            onConsultation = { currentScreen = AppScreen.ConsultationGuide },
            onBack = { currentScreen = AppScreen.ImageUpload }
        )
        AppScreen.ConsultationGuide -> ConsultationGuideScreen(
            selectedPains = selectedPains,
            bodyMapSelections = bodyMapSelections,
            onBack = { currentScreen = AppScreen.AnalysisResult }
        )
        AppScreen.ExerciseRecommendation -> ExerciseRecommendationScreen(
            result = analysisResult,
            selectedPains = selectedPains,
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
    selectedPains: Set<PainSymptom>,
    onPainToggled: (PainSymptom) -> Unit,
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
            BODY_REGIONS.forEach { region ->
                RegionPainCard(
                    region = region,
                    selectedPains = selectedPains,
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
private fun RegionPainCard(
    region: String,
    selectedPains: Set<PainSymptom>,
    onPainToggled: (PainSymptom) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = region,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            painLocationsFor(region).chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { location ->
                        val symptom = PainSymptom(region, location)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPainToggled(symptom) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = symptom in selectedPains,
                                onCheckedChange = { onPainToggled(symptom) }
                            )
                            Text(location, fontSize = 13.sp)
                        }
                    }
                    if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── Page 3: Image Upload ─────────────────────────────────────────────────────

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
            // Image preview area
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

// ─── Page 4: Analysis Result ──────────────────────────────────────────────────

@Composable
private fun AnalysisResultScreen(
    result: AnalysisResponse?,
    displayBitmap: Bitmap?,
    selectedPains: Set<PainSymptom>,
    bodyMapSelections: Set<String>,
    onExercise: () -> Unit,
    onConsultation: () -> Unit,
    onBack: () -> Unit
) {
    val hasPainSelection = selectedPains.isNotEmpty() || bodyMapSelections.isNotEmpty()
    val hasImbalance = result != null && result.landmarkDetected &&
        (result.shoulderHeightDifference > SHOULDER_THRESHOLD ||
            result.hipHeightDifference > HIP_THRESHOLD)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "자세 분석 결과", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Annotated image or original image
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

            if (result != null) {
                ResultCard(
                    result = result,
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

            if (hasImbalance) {
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
private fun ResultCard(result: AnalysisResponse, hasPainSelection: Boolean) {
    val hasDetectedImbalance = result.landmarkDetected &&
        (result.shoulderHeightDifference > SHOULDER_THRESHOLD ||
            result.hipHeightDifference > HIP_THRESHOLD)

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

            // Metric table
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

            // Result narrative
            if (!hasDetectedImbalance) {
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
                // Use backend-generated Korean summary (already includes cautious wording)
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

// ─── Page 5: Consultation Guide ───────────────────────────────────────────────

@Composable
private fun ConsultationGuideScreen(
    selectedPains: Set<PainSymptom>,
    bodyMapSelections: Set<String>,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "증상 상담 준비", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (selectedPains.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "선택한 통증 부위",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        selectedPains.forEach { pain ->
                            Text(
                                text = "• ${pain.region} - ${pain.location}",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (bodyMapSelections.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "바디맵에서 선택한 부위",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        bodyMapSelections.sorted().forEach { regionId ->
                            Text(
                                text = "• ${regionKoreanLabel(regionId)}",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "의사 상담 시 전달할 내용",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(
                        "어느 부위가 아픈지",
                        "언제부터 아팠는지",
                        "어떤 동작에서 심해지는지",
                        "저림, 감각 이상, 힘 빠짐이 있는지",
                        "최근 운동량이나 생활 습관 변화가 있었는지"
                    ).forEach { item ->
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "향후 업데이트 예정",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "LLM API를 이용해 의사 상담용 요약문을 자동 생성",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─── Page 6: Exercise Recommendation ─────────────────────────────────────────

@Composable
private fun ExerciseRecommendationScreen(
    result: AnalysisResponse?,
    selectedPains: Set<PainSymptom>,
    bodyMapSelections: Set<String>,
    onBack: () -> Unit
) {
    val exercises = remember(result, selectedPains, bodyMapSelections) {
        selectExercises(result, selectedPains, bodyMapSelections)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "추천 운동", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            exercises.forEach { exercise ->
                ExerciseCard(exercise = exercise)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // TODO: 향후 쿠팡 운동용품 연동 예정

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "운동 중 통증이 심해지면 즉시 중단하고 전문가 상담을 권장합니다.",
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
                text = exercise.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
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
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
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
