package com.example.lockloop.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.runBlocking
import java.io.File
import com.example.lockloop.workers.GenerateVideoWorker.Companion.ACTION_REFRESH_WALLPAPER


class LoopVideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var player: ExoPlayer? = null
        private val refreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(
                ctx: android.content.Context?,
                intent: android.content.Intent?
            ) {
                if (intent?.action == ACTION_REFRESH_WALLPAPER) {
                    val p = intent.getStringExtra("path") ?: return
                    val f = File(p)
                    if (f.exists()) {
                        val uri = f.toUri()
                        player?.setMediaItem(MediaItem.fromUri(uri))
                        player?.prepare()
                        player?.playWhenReady = true
                    }
                }
            }
        }

        @UnstableApi
        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            player = ExoPlayer.Builder(this@LoopVideoWallpaperService).build().apply {
                setVideoSurfaceHolder(surfaceHolder)
                setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                repeatMode = Player.REPEAT_MODE_ONE
            }

            // 최신 비디오 경로를 읽어 미디어 설정
            val file: File = runBlocking {
                LatestVideoPathProvider.get(this@LoopVideoWallpaperService)
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }
                    ?: copyRawDemo()
            }
            val item = MediaItem.fromUri(file.toUri())
            player?.setMediaItem(item)
            player?.prepare()
            player?.playWhenReady = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this@LoopVideoWallpaperService.registerReceiver(
                    refreshReceiver,
                    IntentFilter(ACTION_REFRESH_WALLPAPER),
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                this@LoopVideoWallpaperService.registerReceiver(
                    refreshReceiver,
                    IntentFilter(ACTION_REFRESH_WALLPAPER)
                )
    }
        }

        override fun onDestroy() {
            this@LoopVideoWallpaperService.unregisterReceiver(refreshReceiver)
            player?.release()
            player = null
            super.onDestroy()
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
