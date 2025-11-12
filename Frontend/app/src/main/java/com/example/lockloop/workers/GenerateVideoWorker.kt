package com.example.lockloop.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lockloop.data.PrefsRepository
import com.example.lockloop.network.ApiService
import com.example.lockloop.network.GenerateRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import java.net.SocketTimeoutException
import java.io.IOException

/**
 * 1) 백엔드에 '생성' 요청
 * 2) 반환된 downloadUrl에서 mp4 다운로드
 * 3) 잠금화면 친화적으로 변환 후 경로 저장
 */
class GenerateVideoWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i("GenerateVideoWorker", "doWork 시작됨")

        return try {
            val prefs = PrefsRepository(applicationContext)
            val u = prefs.flow.first()
            Log.i("GenerateVideoWorker", "Prefs 읽음: ${u.cast}, ${u.background}, ${u.aspect}, ${u.durationSec}")

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val ok = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)   // TCP 연결 대기
                .writeTimeout(120, TimeUnit.SECONDS)    // 요청 바디 업로드
                .readTimeout(900, TimeUnit.SECONDS)     // 응답 바디/첫 바이트 대기 (5분)
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val api = Retrofit.Builder()
                .baseUrl("http://10.0.2.2:8484/")
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(ok)
                .build()
                .create(ApiService::class.java)

            Log.i("GenerateVideoWorker", "서버로 요청 시작")

            // 1) 생성 요청 -> jobId/다운로드 URL 받기
            val genResp = api.generateVideo(
                GenerateRequest(
                    subject = u.cast,
                    place = u.background,
                    aspect = u.aspect,
                    durationSec = u.durationSec
                )
            )
            val jobId = genResp.jobId
            Log.i("GenerateVideoWorker", "jobId=$jobId, downloadUrl=${genResp.downloadUrl}")

            // 2) 상태 폴링: ready 될 때까지 대기
            //    (예: 최대 10분, 2초 간격. 필요시 값 조정)
            val pollIntervalMs = 2000L
            val maxWaitMs = 10 * 60 * 1000L
            var waited = 0L
            var ready = false
            while (waited <= maxWaitMs) {
                val st = api.getStatus(jobId)
                Log.i("GenerateVideoWorker", "status=${
                    st.status
                }${st.detail?.let { " detail=$it" } ?: ""}")
                if (st.status.equals("ready", ignoreCase = true)) {
                    ready = true
                    break
                }
                if (st.status.equals("error", ignoreCase = true)) {
                    throw RuntimeException("서버 생성 실패: ${st.detail ?: "unknown"}")
                }
                delay(pollIntervalMs)
                waited += pollIntervalMs
            }
            if (!ready) {
                throw SocketTimeoutException("비디오 생성 대기 타임아웃")
            }

            // 3) 준비 완료 → 다운로드. 반드시 Content-Type 검증!
            val req = Request.Builder().url(genResp.downloadUrl).build()
            ok.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("다운로드 실패: HTTP ${resp.code}")
                }
                val ctype = resp.header("Content-Type") ?: ""
                if (!ctype.startsWith("video/")) {
                    // JSON 등 비디오가 아니면 예외 처리
                    val errBody = resp.body?.string()
                    throw IOException("비디오가 아님(Content-Type=$ctype) body=$errBody")
                }

                val rawDir = File(applicationContext.filesDir, "raw").apply { mkdirs() }
                val raw = File(rawDir, "lockscreen_raw.mp4")
                resp.body?.byteStream()?.use { input ->
                    raw.outputStream().use { output -> input.copyTo(output) }
                } ?: error("응답 본문 없음")

                // 4) 프론트 재처리 제거/유지 정책에 따라 분기
                val outDir = File(applicationContext.filesDir, "lock").apply { mkdirs() }
                val out = File(outDir, "lockscreen.mp4")
                // 재처리 제거안: 그대로 복사
                raw.copyTo(out, overwrite = true)
                // (조건부 재처리 쓰실 거면 여기서 transcodeToLockscreen 호출)

                prefs.setLatestVideo(out.absolutePath)
                Log.i("GenerateVideoWorker", "최신 비디오 경로 저장됨")
            }

            Result.success()
        } catch (e: SocketTimeoutException) {
            Log.e("GenerateVideoWorker", "서버 응답 지연(Timeout). 비디오 생성 중일 수 있음.", e)
            Result.retry() // 또는 Result.failure()
        } catch (e: Exception) {
            Log.e("GenerateVideoWorker", "실패", e)
            Result.failure()
        }
    }
}

