package com.example.lockloop.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path

data class GenerateRequest(
    val subject: String,
    val place: String,
    val aspect: String,
    val durationSec: Int
)

data class GenerateResponse(
    val jobId: String,
    val downloadUrl: String // 백엔드가 반환하는 mp4 다운로드 URL
)

data class StatusResponse(
    val status: String,
    val detail: String? = null
)

interface ApiService {
    // FastAPI 서버 app.py의 /generateVideo 와 매핑
    @POST("generateVideo")
    suspend fun generateVideo(@Body req: GenerateRequest): GenerateResponse

    @GET("status/{jobId}")
    suspend fun getStatus(@Path("jobId") jobId: String): StatusResponse
}
