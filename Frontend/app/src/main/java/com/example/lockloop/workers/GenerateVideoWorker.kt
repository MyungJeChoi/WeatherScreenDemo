package com.example.lockloop.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lockloop.data.PrefsRepository
import com.example.lockloop.media.VideoPostProcessor
import com.example.lockloop.network.ApiService
import com.example.lockloop.network.GenerateRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

class GenerateVideoWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PrefsRepository(applicationContext)
        val u = prefs.flow.first()

        // 1) 프롬프트 조합
        val prompt = "Cast: ${u.cast}, Background: ${u.background}, Aspect: ${u.aspect}, Duration: ${u.durationSec}s"

        // 2) 백엔드 호출 (Base URL은 본인 서버 주소로 교체)
        val baseUrl = "https://YOUR-BACKEND/" // TODO: 교체
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        val api = retrofit.create(ApiService::class.java)

        val resp = try {
            api.generateVideo(GenerateRequest(prompt, u.aspect, u.durationSec))
        } catch (e: Exception) {
            e.printStackTrace()
            // 데모 편의를 위해 백엔드가 없으면 videos/demo.mp4를 복사(테스트용)
            val fallbackIn = copyRawDemo()
            val out = File(applicationContext.filesDir, "videos/processed.mp4").apply { parentFile?.mkdirs() }
            VideoPostProcessor.muteAndTranscode(applicationContext, fallbackIn, out)
            prefs.setLatestVideo(out.absolutePath)
            return Result.success()
        }

        // 3) mp4 다운로드
        val inFile = withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val req = Request.Builder().url(resp.downloadUrl).build()
            val res = client.newCall(req).execute()
            val dir = File(applicationContext.filesDir, "videos").apply { mkdirs() }
            val f = File(dir, "gen.mp4")
            res.body?.byteStream().use { input ->
                f.outputStream().use { output -> input?.copyTo(output) }
            }
            f
        }

        // 4) 후처리 (무음+코덱)
        val outFile = File(applicationContext.filesDir, "videos/processed.mp4")
        VideoPostProcessor.muteAndTranscode(applicationContext, inFile, outFile)

        // 5) 최신 경로 저장
        prefs.setLatestVideo(outFile.absolutePath)

        return Result.success()
    }

    private fun copyRawDemo(): File {
        val out = File(applicationContext.filesDir, "raw/demo.mp4").apply { parentFile?.mkdirs() }
        applicationContext.resources.openRawResource(
            applicationContext.resources.getIdentifier("demo", "raw", applicationContext.packageName)
        ).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}
