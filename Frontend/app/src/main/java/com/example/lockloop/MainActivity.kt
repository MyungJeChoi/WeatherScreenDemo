package com.example.lockloop

import android.Manifest
import android.os.Build
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.lockloop.data.PrefsRepository
import com.example.lockloop.data.UserPrefs
import com.example.lockloop.ui.SettingsScreen
import com.example.lockloop.ui.SettingsUiState
import com.example.lockloop.workers.ApplyWallpaperWorker
import com.example.lockloop.workers.GenerateVideoWorker
import com.example.lockloop.wallpaper.SetWallpaperActivity
import kotlinx.coroutines.flow.first
import androidx.work.*
import android.content.Intent
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {
    /* PrefsRepository : video 설정 저장해두는 class */
    private lateinit var prefs: PrefsRepository

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 승인/거부 뒤 실행할 콜백 (예시) */
        /* granted -> if (granted) { ... } else { ... } */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        prefs = PrefsRepository(this)

        if (Build.VERSION.SDK_INT >= 33) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            val p = prefs.flow.first()
            setContent {
                // 1) UI 상태를 Compose 쪽에서 보관
                var ui by remember {
                    mutableStateOf(
                        SettingsUiState(
                            cast = p.cast,
                            background = p.background,
                            aspect = p.aspect,
                            duration = p.durationSec.toString(),
                            genTime = p.genTime,
                            applyTime = p.applyTime
                        )
                    )
                }

                // 2) Composable 전용 코루틴 스코프(중요: lifecycleScope 대신)
                val scope = rememberCoroutineScope()

                // 3) 저장 디바운스용 잡
                var saveJob: Job? by remember { mutableStateOf<Job?>(null) }

                SettingsScreen(
                    state = ui,
                    onChange = { new ->
                        // 4) 입력 즉시 UI 반영
                        ui = new

                        // 5) 저장은 Composable scope에서 디바운스 실행
                        saveJob?.cancel()
                        saveJob = scope.launch {
                            delay(400)
                            val latest = prefs.flow.first().latestVideoPath
                            prefs.save(
                                UserPrefs(
                                    cast = new.cast,
                                    background = new.background,
                                    aspect = new.aspect,
                                    durationSec = new.duration.toIntOrNull() ?: 8,
                                    genTime = new.genTime,
                                    applyTime = new.applyTime,
                                    latestVideoPath = latest
                                )
                            )
                        }
                    },
                    onSaveAndSchedule = { scheduleDaily() },
                    onGenerateNow = { runGenerateOnce() },
                    onApplyNow = {
                        startActivity(Intent(this@MainActivity, SetWallpaperActivity::class.java))
                    }
                )
            }
        }
    }

    private fun scheduleDaily() {
        lifecycleScope.launch {
            // 다음 실행까지 대기시간 계산
            fun safeDelayMinutesOr15(time: String): Long {
                return runCatching {
                    val target = LocalTime.parse(time)
                    val now = LocalTime.now()
                    val duration = if (now.isBefore(target))
                        Duration.between(now, target)
                    else
                        Duration.between(now, target).plusDays(1)

                    val minutes = duration.toMinutes()
                    val adjusted = minutes.coerceAtLeast(15)

                    if (adjusted == 15L && minutes < 15L) {
                        // 반드시 UI 스레드에서 수행
                        runOnUiThread {
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle("알림")
                                .setMessage("최소 시간 15분으로 설정했습니다.")
                                .setPositiveButton("확인", null)
                                .show()
                        }
                    }
                    adjusted
                }.getOrElse {
                    runOnUiThread {
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle("시간 형식 오류")
                            .setMessage("시간 형식이 올바르지 않아 15분으로 설정했습니다.")
                            .setPositiveButton("확인", null)
                            .show()
                    }
                    15L
                }
            }
            val u = prefs.flow.first()
            val gen = PeriodicWorkRequestBuilder<GenerateVideoWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(safeDelayMinutesOr15(u.genTime), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED) // 와이파이 권장
//                        .setRequiresCharging(true)
                        .build()
                )
                .build()

            val apply = PeriodicWorkRequestBuilder<ApplyWallpaperWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(safeDelayMinutesOr15(u.applyTime), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                "gen_daily", ExistingPeriodicWorkPolicy.UPDATE, gen
            )
            WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                "apply_daily", ExistingPeriodicWorkPolicy.UPDATE, apply
            )
        }
    }

    private fun runGenerateOnce() {
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<GenerateVideoWorker>().build()
        )
    }
}
