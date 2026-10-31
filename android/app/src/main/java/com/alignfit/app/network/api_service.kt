package com.alignfit.app.network

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("analyze/image")
    suspend fun analyzeImage(
        @Part image: MultipartBody.Part
    ): AnalysisResponse

    // Video movement-quality evaluation (MobiPhysio MLPs). Form field: "video".
    @Multipart
    @POST("predict")
    suspend fun predictVideo(
        @Part video: MultipartBody.Part
    ): PredictResponse
}
