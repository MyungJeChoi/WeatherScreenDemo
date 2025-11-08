@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.lockloop.media

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Media3 Transformer로 입력 mp4를 잠금화면 친화적(H.264/무음) mp4로 변환.
 */
suspend fun transcodeToLockscreen(
    context: Context,
    input: File,
    output: File
): File = suspendCancellableCoroutine { cont ->

    require(input.exists()) { "입력 파일이 존재하지 않습니다: ${input.absolutePath}" }
    output.parentFile?.mkdirs()
    if (output.exists()) runCatching { output.delete() }

    val edited = EditedMediaItem.Builder(MediaItem.fromUri(input.toUri()))
        .setRemoveAudio(true)            // 오디오 제거
        .setFlattenForSlowMotion(true)   // 슬로모션 클립 평탄화
        .build()

    // ✅ v1.4.1: Composition은 Builder 로 생성해야 함
    val composition = Composition.Builder(
        listOf(EditedMediaItemSequence(listOf(edited)))
    ).build()

    val transformer = Transformer.Builder(context)
//        .setRemoveAudio(true)                 // 안전하게 한 번 더 보장
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        // .setContainerMimeType(MimeTypes.VIDEO_MP4) // 필요 시 지정
        .build()

    val listener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            cont.resume(output)
        }

        // ✅ v1.4.1: exportResult 가 non-null
        override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
        ) {
            cont.resumeWithException(exportException)
        }
    }

    transformer.addListener(listener)

    runCatching {
        transformer.start(composition, output.absolutePath)
    }.onFailure { e ->
        transformer.removeListener(listener)
        cont.resumeWithException(e)
        return@suspendCancellableCoroutine
    }

    cont.invokeOnCancellation {
        runCatching {
            transformer.cancel()
            transformer.removeListener(listener)
        }
    }
}
