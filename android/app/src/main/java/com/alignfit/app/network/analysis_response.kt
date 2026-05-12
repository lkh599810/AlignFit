package com.alignfit.app.network

import com.google.gson.annotations.SerializedName

data class AnalysisResponse(
    @SerializedName("landmark_detected") val landmarkDetected: Boolean,
    @SerializedName("landmark_count") val landmarkCount: Int,
    @SerializedName("shoulder_height_difference") val shoulderHeightDifference: Double,
    @SerializedName("hip_height_difference") val hipHeightDifference: Double,
    @SerializedName("simple_summary") val simpleSummary: String,
    @SerializedName("recommendations") val recommendations: List<String>,
    @SerializedName("caution_message") val cautionMessage: String
)
