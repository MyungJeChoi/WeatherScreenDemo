package com.example.lockloop.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lockloop.data.PrefsRepository
import com.example.lockloop.media.transcodeToLockscreen
import com.example.lockloop.network.ApiService
import com.example.lockloop.network.GenerateRequest
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

/**
 * 1) 백엔드에 '생성' 요청
 * 2) 반환된 downloadUrl에서 mp4 다운로드
 * 3) 잠금화면 친화적으로 변환 후 경로 저장
 */
class GenerateVideoWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PrefsRepository(applicationContext)
        val u = prefs.flow.first()

        // 1) Retrofit 생성 — 백엔드는 FastAPI 서버 (http://147.46.27.83:8750/)
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val ok = OkHttpClient.Builder().addInterceptor(logging).build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://147.46.27.83:8750/")
            .addConverterFactory(MoshiConverterFactory.create())
            .client(ok)
            .build()
        val api = retrofit.create(ApiService::class.java)

        // 프롬프트는 간단히 캐릭터+배경을 합쳐서 보냅니다(서버가 날씨 기반으로 보강).
//        val prompt = "${"$"}{u.cast} standing at ${"$"}{u.background}. Cinematic, loopable, minimal camera motion, no text."

        val resp = api.generateVideo(
            GenerateRequest(
                subject = u.cast,
                place = u.background,
                aspect = u.aspect,
                durationSec = u.durationSec
            )
        )

        // 2) 다운로드
        val rawDir = File(applicationContext.filesDir, "raw").apply { mkdirs() }
        val raw = File(rawDir, "lockscreen_raw.mp4")
        val req = Request.Builder().url(resp.downloadUrl).build()
        ok.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("다운로드 실패: ${"$"}{r.code}")
            r.body?.byteStream()?.use { input ->
                raw.outputStream().use { output -> input.copyTo(output) }
            } ?: error("응답 body 없음")
        }

        // 3) 후처리(Media3 Transformer) → files/lock/lockscreen.mp4
        val outDir = File(applicationContext.filesDir, "lock").apply { mkdirs() }
        val out = File(outDir, "lockscreen.mp4")
        raw.copyTo(out, overwrite = true)
        // transcodeToLockscreen(applicationContext, raw, out)

        // 4) 경로 저장 (라이브 월페이퍼 서비스가 이 경로를 읽어 재생)
        prefs.setLatestVideo(out.absolutePath)

        return Result.success()
    }
}
