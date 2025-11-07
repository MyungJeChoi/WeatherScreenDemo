package com.example.lockloop.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.weatherdemo.R
import com.example.lockloop.util.NotificationHelper
import com.example.lockloop.wallpaper.SetWallpaperActivity

class ApplyWallpaperWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // 사용자 확인이 필요한 화면을 여는 알림 표시
        NotificationHelper.ensureChannel(applicationContext)
        val pi = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, SetWallpaperActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("새 잠금화면 준비 완료")
            .setContentText("탭하여 라이브 월페이퍼 적용")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationHelper.manager(applicationContext).notify(1001, n)
        return Result.success()
    }
}
