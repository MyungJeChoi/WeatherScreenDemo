package com.example.lockloop.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class SetWallpaperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 라이브 월페이퍼 선택 화면을 직접 엽니다(사용자 확인 필요).
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@SetWallpaperActivity, LoopVideoWallpaperService::class.java)
            )
        }
        startActivity(intent)
        finish()
    }
}
