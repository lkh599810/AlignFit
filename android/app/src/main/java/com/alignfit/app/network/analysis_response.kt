package com.alignfit.app.network

import com.google.gson.annotations.SerializedName

data class AnalysisResponse(
    @SerializedName("landmark_detected") val landmarkDetected: Boolean,
    @SerializedName("landmark_count") val landmarkCount: Int,
    @SerializedName("shoulder_height_difference") val shoulderHeightDifference: Double,
    @SerializedName("hip_height_difference") val hipHeightDifference: Double,
    @SerializedName("simple_summary") val simpleSummary: String,
    @SerializedName("recommendations") val recommendations: List<String>,
    @SerializedName("caution_message") val cautionMessage: String,
    @SerializedName("annotated_image_base64") val annotatedImageBase64: String?,

    // Signed directional results; null when talking to an older backend.
    // shoulder/hip: "left_higher" | "right_higher" | "balanced" | "not_detected"
    // head/trunk:   "left" | "right" | "balanced" | "not_detected"
    // foot status:  "left_more_outward" | "right_more_outward" | "balanced"
    //               | "uncertain" | "not_detected"
    @SerializedName("shoulder_direction") val shoulderDirection: String? = null,
    @SerializedName("shoulder_direction_summary") val shoulderDirectionSummary: String? = null,
    @SerializedName("hip_direction") val hipDirection: String? = null,
    @SerializedName("hip_direction_summary") val hipDirectionSummary: String? = null,

    // Head tilt metric
    @SerializedName("head_tilt_detected") val headTiltDetected: Boolean = false,
    @SerializedName("head_tilt_angle_degrees") val headTiltAngleDegrees: Double? = null,
    @SerializedName("head_tilt_direction") val headTiltDirection: String? = null,
    @SerializedName("head_tilt_summary") val headTiltSummary: String? = null,

    // Trunk centerline metric
    @SerializedName("trunk_centerline_detected") val trunkCenterlineDetected: Boolean = false,
    @SerializedName("trunk_centerline_angle_degrees") val trunkCenterlineAngleDegrees: Double? = null,
    @SerializedName("trunk_tilt_direction") val trunkTiltDirection: String? = null,
    @SerializedName("trunk_centerline_summary") val trunkCenterlineSummary: String? = null,

    // Foot direction metric
    @SerializedName("foot_direction_detected") val footDirectionDetected: Boolean = false,
    @SerializedName("left_foot_angle_degrees") val leftFootAngleDegrees: Double? = null,
    @SerializedName("right_foot_angle_degrees") val rightFootAngleDegrees: Double? = null,
    @SerializedName("foot_direction_difference_degrees") val footDirectionDifferenceDegrees: Double? = null,
    @SerializedName("foot_direction_status") val footDirectionStatus: String? = null,
    @SerializedName("foot_direction_summary") val footDirectionSummary: String? = null
)
