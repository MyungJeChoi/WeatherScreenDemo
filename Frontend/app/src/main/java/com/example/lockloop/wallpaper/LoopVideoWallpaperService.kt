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

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            player = ExoPlayer.Builder(this@LoopVideoWallpaperService).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                setVideoSurfaceHolder(surfaceHolder)
            }
            // 최신 비디오 경로를 읽어 미디어 설정
            val file: File = runBlocking {
                LatestVideoPathProvider.get(this@LoopVideoWallpaperService)
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }
                    ?: copyRawDemo()
            }
            val item = MediaItem.fromUri(file.toURI().toString().toUri())
            player?.setMediaItem(item)
            player?.prepare()
            player?.playWhenReady = true
        }

        override fun onDestroy() {
            super.onDestroy()
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
