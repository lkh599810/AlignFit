package com.alignfit.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alignfit.app.network.AnalysisResponse
import com.alignfit.app.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        resultText = ""
        errorText = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AlignFit",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )

        Text(
            text = "Posture Analysis",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Image")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedImageUri != null) {
            Text(
                text = "Image selected: ${selectedImageUri!!.lastPathSegment ?: "unknown"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val uri = selectedImageUri
                if (uri == null) {
                    errorText = "Please select an image first."
                    return@Button
                }
                isLoading = true
                resultText = ""
                errorText = ""
                coroutineScope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()

                        if (bytes == null) {
                            errorText = "Failed to read image."
                            isLoading = false
                            return@launch
                        }

                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("image", "upload.jpg", requestBody)

                        val response = RetrofitClient.apiService.analyzeImage(part)
                        resultText = formatResult(response)
                    } catch (e: Exception) {
                        errorText = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Analyzing..." else "Analyze Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }

        if (errorText.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (resultText.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = resultText,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun formatResult(response: AnalysisResponse): String {
    return buildString {
        appendLine("Landmark Detected: ${response.landmarkDetected}")
        appendLine("Landmark Count: ${response.landmarkCount}")
        appendLine("Shoulder Height Diff: ${"%.4f".format(response.shoulderHeightDifference)}")
        appendLine("Hip Height Diff: ${"%.4f".format(response.hipHeightDifference)}")
        appendLine()
        appendLine("Summary:")
        appendLine(response.simpleSummary)
        appendLine()
        appendLine("Recommendations:")
        response.recommendations.forEachIndexed { i, rec ->
            appendLine("${i + 1}. $rec")
        }
        appendLine()
        appendLine("Note:")
        append(response.cautionMessage)
    }
}
