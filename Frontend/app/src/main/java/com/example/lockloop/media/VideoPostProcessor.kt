@file:OptIn(UnstableApi::class) // ← 파일 전체에서 UnstableApi 사용을 허용

package com.example.lockloop.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object VideoPostProcessor {

    /**
     * 입력 mp4를 무음 + H.264로 트랜스코딩.
     * Media3 Transformer는 비동기로 동작하므로 코루틴으로 완료/에러를 기다립니다.
     */
    suspend fun muteAndTranscode(context: Context, input: File, output: File) {
        // 1) 입력 아이템 구성 + 오디오 제거
        val inputItem = MediaItem.fromUri(input.toURI().toString())
        val edited = EditedMediaItem.Builder(inputItem)
            .setRemoveAudio(true) // 공식 가이드 방식
            .build()

        // 2) 트랜스포머 구성 (출력 코덱: H.264)
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build()

        // 3) 비동기 시작 + 완료/에러 콜백을 코루틴으로 래핑
        return suspendCancellableCoroutine { cont ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    transformer.removeListener(this)
                    cont.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException // ← 시그니처 정확히 맞춰야 함
                ) {
                    transformer.removeListener(this)
                    cont.resumeWithException(exception)
                }
            }

            transformer.addListener(listener)
            transformer.start(edited, output.absolutePath) // 공식 예시와 동일한 호출

            // 코루틴 취소 시 트랜스포머도 취소
            cont.invokeOnCancellation {
                runCatching {
                    transformer.cancel()
                    transformer.removeListener(listener)
                }
            }
        }
    }
}
