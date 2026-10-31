package com.alignfit.app.network

import com.google.gson.annotations.SerializedName

// Mirrors backend/app/schemas/predict_schema.py (POST /predict).
data class PredictResponse(
    @SerializedName("pose_detected") val poseDetected: Boolean,
    @SerializedName("frames_used") val framesUsed: Int,
    // null when poseDetected is false
    @SerializedName("movement_quality") val movementQuality: MovementQuality? = null,
    @SerializedName("predicted_exercise") val predictedExercise: PredictedExercise? = null,
    @SerializedName("message") val message: String,
    @SerializedName("recommendations") val recommendations: List<VideoRecommendation> = emptyList(),
    @SerializedName("caution_message") val cautionMessage: String
)

data class MovementQuality(
    @SerializedName("label") val label: String,        // "correct" | "incorrect"
    @SerializedName("status") val status: String,      // "looks_ok" | "needs_attention"
    @SerializedName("confidence") val confidence: Double
)

data class PredictedExercise(
    @SerializedName("exercise_id") val exerciseId: String,
    @SerializedName("exercise_name") val exerciseName: String,
    @SerializedName("exercise_name_ko") val exerciseNameKo: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("target_region") val targetRegion: String,
    @SerializedName("target_region_ko") val targetRegionKo: String,
    @SerializedName("posture_pattern") val posturePattern: String,
    @SerializedName("posture_pattern_ko") val posturePatternKo: String
)

data class VideoRecommendation(
    @SerializedName("exercise_name") val exerciseName: String,
    @SerializedName("related_body_part") val relatedBodyPart: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("cautions") val cautions: String? = null,
    @SerializedName("youtube_query") val youtubeQuery: String? = null
)
