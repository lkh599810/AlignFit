package com.alignfit.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.data.HomecareDictionary
import com.alignfit.app.network.AnalysisResponse
import com.alignfit.app.network.PREVIEW_IMAGE_MAX_SIDE
import com.alignfit.app.network.RetrofitClient
import com.alignfit.app.network.buildImagePart
import com.alignfit.app.network.decodeSampledBitmap
import com.alignfit.app.network.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Navigation ───────────────────────────────────────────────────────────────
// Bottom tabs for the four main areas; the posture check is a short step flow
// inside its own tab. Tabs keep their state, so switching never loses progress.

enum class AppTab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Home("홈", Icons.Filled.Home, Icons.Outlined.Home),
    Check("자세 분석", Icons.Filled.Face, Icons.Outlined.Face),
    Exercise("운동", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    Evaluate("움직임 평가", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow)
}

enum class CheckStep { Pain, BodyMap, Photo, Result, Consultation }

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(AppTab.Home) }
    var step by remember { mutableStateOf(CheckStep.Pain) }
    var selectedPainIds by remember { mutableStateOf(setOf<String>()) }
    var bodyMapSelections by remember { mutableStateOf(setOf<String>()) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<AnalysisResponse?>(null) }
    var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun resetAnalysis() {
        selectedPainIds = setOf()
        bodyMapSelections = setOf()
        selectedImageUri = null
        selectedImageBitmap = null
        analysisResult = null
        annotatedBitmap = null
        step = CheckStep.Pain
    }

    fun startAnalysis() {
        if (analysisResult != null) resetAnalysis()
        tab = AppTab.Check
    }

    // System back: previous step inside the check flow, otherwise back to Home.
    BackHandler(enabled = tab != AppTab.Home) {
        if (tab == AppTab.Check) when (step) {
            CheckStep.Consultation -> step = CheckStep.Result
            CheckStep.Photo -> step = CheckStep.BodyMap
            CheckStep.BodyMap -> step = CheckStep.Pain
            else -> tab = AppTab.Home
        } else tab = AppTab.Home
    }

    Scaffold(
        containerColor = Paper,
        bottomBar = { AppBottomBar(current = tab, onSelect = { tab = it }) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                AppTab.Home -> HomeScreen(
                    lastResult = analysisResult,
                    onStartAnalysis = ::startAnalysis,
                    onOpenResult = { tab = AppTab.Check; step = CheckStep.Result },
                    onOpenTab = { tab = it }
                )
                AppTab.Check -> when (step) {
                    CheckStep.Pain -> PainSelectionScreen(
                        selectedPainIds = selectedPainIds,
                        onPainToggled = { id ->
                            selectedPainIds =
                                if (id in selectedPainIds) selectedPainIds - id else selectedPainIds + id
                        },
                        onNext = { step = CheckStep.BodyMap },
                        onBack = { tab = AppTab.Home }
                    )
                    CheckStep.BodyMap -> BodyMapSelectionScreen(
                        selectedRegions = bodyMapSelections,
                        onRegionToggled = { id ->
                            bodyMapSelections =
                                if (id in bodyMapSelections) bodyMapSelections - id else bodyMapSelections + id
                        },
                        onNext = { step = CheckStep.Photo },
                        onBack = { step = CheckStep.Pain }
                    )
                    CheckStep.Photo -> ImageUploadScreen(
                        selectedImageUri = selectedImageUri,
                        selectedImageBitmap = selectedImageBitmap,
                        onImageSelected = { uri ->
                            selectedImageUri = uri
                            annotatedBitmap = null
                            analysisResult = null
                            coroutineScope.launch {
                                // Downsampled preview: a full-resolution camera photo can need
                                // ~50MB+ as a Bitmap.
                                selectedImageBitmap = withContext(Dispatchers.IO) {
                                    try {
                                        decodeSampledBitmap(context, uri, PREVIEW_IMAGE_MAX_SIDE)
                                    } catch (_: Exception) {
                                        null
                                    } catch (_: OutOfMemoryError) {
                                        null
                                    }
                                }
                            }
                        },
                        onAnalysisComplete = { response, bmp ->
                            analysisResult = response
                            annotatedBitmap = bmp
                            step = CheckStep.Result
                        },
                        onBack = { step = CheckStep.BodyMap }
                    )
                    CheckStep.Result -> AnalysisResultScreen(
                        result = analysisResult,
                        displayBitmap = annotatedBitmap ?: selectedImageBitmap,
                        selectedPainIds = selectedPainIds,
                        bodyMapSelections = bodyMapSelections,
                        onExercise = { tab = AppTab.Exercise },
                        onConsultation = { step = CheckStep.Consultation },
                        onRestart = ::resetAnalysis
                    )
                    CheckStep.Consultation -> ConsultationGuideScreen(
                        result = analysisResult,
                        selectedPainIds = selectedPainIds,
                        bodyMapSelections = bodyMapSelections,
                        onBack = { step = CheckStep.Result }
                    )
                }
                AppTab.Exercise -> ExerciseRecommendationScreen(
                    result = analysisResult,
                    selectedPainIds = selectedPainIds,
                    bodyMapSelections = bodyMapSelections,
                    onStartAnalysis = ::startAnalysis
                )
                AppTab.Evaluate -> VideoEvaluationScreen()
            }
        }
    }
}

@Composable
private fun AppBottomBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = Hairline)
        NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
            AppTab.entries.forEach { item ->
                val selected = item == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(item) },
                    icon = {
                        Icon(
                            if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    },
                    label = {
                        Text(
                            item.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Ink,
                        selectedTextColor = Ink,
                        indicatorColor = Tint,
                        unselectedIconColor = InkFaint,
                        unselectedTextColor = InkFaint
                    )
                )
            }
        }
    }
}

// ─── Home ─────────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(
    lastResult: AnalysisResponse?,
    onStartAnalysis: () -> Unit,
    onOpenResult: () -> Unit,
    onOpenTab: (AppTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
    ) {
        Spacer(Modifier.height(28.dp))
        Text("AlignFit", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(24.dp))

        // Primary action: black hero card.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Ink)
                .clickable(onClick = onStartAnalysis)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Face, contentDescription = null, tint = Paper, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                "자세 분석",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Paper,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Paper),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ink)
            }
        }

        if (lastResult != null) {
            Spacer(Modifier.height(12.dp))
            Panel(onClick = onOpenResult) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val flags = remember(lastResult) { HomecareDictionary.inferPostureFlags(lastResult) }
                    Text(
                        when {
                            !lastResult.landmarkDetected -> "인식 실패"
                            HomecareDictionary.hasPostureIssue(flags) -> "살펴볼 부분 있음"
                            else -> "균형 안정적"
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "결과 보기", tint = Ink)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeTile(
                icon = Icons.Outlined.FavoriteBorder,
                title = "운동",
                onClick = { onOpenTab(AppTab.Exercise) },
                modifier = Modifier.weight(1f)
            )
            HomeTile(
                icon = Icons.Outlined.PlayArrow,
                title = "움직임 평가",
                onClick = { onOpenTab(AppTab.Evaluate) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Panel(modifier = modifier, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(28.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

// ─── Step 1: Pain Selection ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PainSelectionScreen(
    selectedPainIds: Set<String>,
    onPainToggled: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(
            title = "어디가 불편하세요?",
            onBack = onBack,
            step = 1
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
        ) {
            HomecareDictionary.PAIN_REGION_GROUPS.forEach { group ->
                Spacer(Modifier.height(12.dp))
                Text(group.regionLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.options.forEach { option ->
                        SelectPill(
                            text = option.locationLabel,
                            selected = option.id in selectedPainIds,
                            onClick = { onPainToggled(option.id) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = Hairline)
            }
            Spacer(Modifier.height(16.dp))
        }
        BottomActions {
            PrimaryButton(
                text = if (selectedPainIds.isEmpty()) "건너뛰기" else "다음",
                onClick = onNext,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Step 3: Photo ────────────────────────────────────────────────────────────

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
    val pickImage = { if (!isLoading) imagePickerLauncher.launch("image/*") }

    fun analyze() {
        val uri = selectedImageUri ?: return
        isLoading = true
        errorText = ""
        coroutineScope.launch {
            try {
                // Downscaled + EXIF-rotated JPEG (<= 1600px); the server
                // returns an annotated image of the same size.
                val part = withContext(Dispatchers.IO) { buildImagePart(context, uri) }
                if (part == null) {
                    errorText = "이미지를 읽을 수 없습니다. 다른 사진을 선택해 주세요."
                    isLoading = false
                    return@launch
                }
                val response = RetrofitClient.apiService.analyzeImage(part)
                val annotatedBmp = withContext(Dispatchers.Default) {
                    response.annotatedImageBase64?.let { b64 ->
                        val decoded = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                    }
                }
                isLoading = false
                onAnalysisComplete(response, annotatedBmp)
            } catch (e: Exception) {
                isLoading = false
                errorText = e.toUserMessage()
            } catch (e: OutOfMemoryError) {
                isLoading = false
                errorText = e.toUserMessage()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(
            title = "전신 사진",
            onBack = onBack,
            step = 3
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Tint)
                    .clickable(onClick = pickImage),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageBitmap != null) {
                    Image(
                        bitmap = selectedImageBitmap.asImageBitmap(),
                        contentDescription = "선택된 이미지",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Paper),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "다시 선택", tint = Ink)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Paper),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "사진 선택", tint = Ink, modifier = Modifier.size(28.dp))
                    }
                }
            }

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
                Spacer(Modifier.height(16.dp))
                NoteBox(text = errorText)
            }

            Spacer(Modifier.height(16.dp))
        }
        BottomActions {
            PrimaryButton(
                text = if (isLoading) "분석 중…" else "분석하기",
                onClick = ::analyze,
                enabled = selectedImageUri != null && !isLoading,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
