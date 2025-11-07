package com.example.lockloop.network

import retrofit2.http.Body
import retrofit2.http.POST

data class GenerateRequest(
    val prompt: String,
    val aspect: String,
    val durationSec: Int
)

data class GenerateResponse(
    val downloadUrl: String // 백엔드가 반환하는 mp4 다운로드 URL
)

interface ApiService {
    @POST("generateVideo")
    suspend fun generateVideo(@Body req: GenerateRequest): GenerateResponse
}
