package com.example.lockloop.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.runBlocking
import java.io.File

class LoopVideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var player: ExoPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startPlayer(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            player?.playWhenReady = visible
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            releasePlayer()
            super.onDestroy()
        }

        private fun startPlayer(holder: SurfaceHolder) {
            val ctx = this@LoopVideoWallpaperService

            val videoFile: File = runBlocking {
                // 1) 최신 경로 가져오기
                val latest = LatestVideoPathProvider.get(ctx)
                val f = latest?.let { File(it) }
                when {
                    f != null && f.exists() -> f
                    else -> copyRawDemo() // 2) 없으면 동봉 demo.mp4로 폴백
                }
            }

            // 3) ExoPlayer를 Surface에 '명시적으로' 연결
            player = ExoPlayer.Builder(ctx).build().apply {
                setVideoSurfaceHolder(holder)               // ★ 핵심: 검은 화면 방지
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                setMediaItem(MediaItem.fromUri(videoFile.toUri()))
                prepare()
                playWhenReady = true
            }
        }

        private fun releasePlayer() {
            player?.release()
            player = null
        }

        private fun copyRawDemo(): File {
            val out = File(filesDir, "raw/demo.mp4").apply { parentFile?.mkdirs() }
            val resId = resources.getIdentifier("demo", "raw", packageName)
            resources.openRawResource(resId).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            return out
        }
    }
}
