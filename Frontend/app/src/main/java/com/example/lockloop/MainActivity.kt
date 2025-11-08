package com.example.lockloop

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lockloop.data.PrefsRepository
import com.example.lockloop.data.UserPrefs
import com.example.lockloop.ui.SettingsScreen
import com.example.lockloop.ui.SettingsUiState
import com.example.lockloop.util.NotificationHelper
import com.example.lockloop.wallpaper.SetWallpaperActivity
import com.example.lockloop.workers.ApplyWallpaperWorker
import com.example.lockloop.workers.GenerateVideoWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsRepository

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 알림 권한 승인/거부는 데모에선 무시 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        prefs = PrefsRepository(this)
        NotificationHelper.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= 33) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // 팝업(Compose AlertDialog) 메시지 상태
            val messageState = remember { mutableStateOf<String?>(null) }

            // DataStore Flow를 Compose에서 수명주기-안전하게 수집
            val u = prefs.flow.collectAsState(initial = UserPrefs()).value

            Surface(color = MaterialTheme.colorScheme.background) {
                SettingsScreen(
                    ui = SettingsUiState(
                        cast = u.cast,
                        background = u.background,
                        aspect = u.aspect,
                        duration = u.durationSec.toString(),
                        genTime = u.genTime,
                        applyTime = u.applyTime,
                        latestVideoPath = u.latestVideoPath
                    ),
                    onSave = { new ->
                        // 사용자가 입력한 설정 저장
                        lifecycleScope.launch {
                            prefs.save(
                                UserPrefs(
                                    cast = new.cast,
                                    background = new.background,
                                    aspect = new.aspect,
                                    durationSec = new.duration.toIntOrNull() ?: 8,
                                    genTime = new.genTime,
                                    applyTime = new.applyTime,
                                    latestVideoPath = u.latestVideoPath
                                )
                            )
                            messageState.value = "설정을 저장했습니다."
                        }
                    },
                    onSaveAndSchedule = { scheduleDaily(messageState) },
                    onGenerateNow = { runGenerateOnce() },
                    onApplyNow = {
                        startActivity(Intent(this@MainActivity, SetWallpaperActivity::class.java))
                    },
                    messageState = messageState
                )
            }
        }
    }

    /**
     * 매일 '생성 시간'과 '적용 시간'에 맞춰 워커 등록.
     * - 최소 초기 지연 15분(WorkManager 제약 하한)을 보장
     * - 시간 파싱 실패 시 15분으로 대체하고 팝업으로 안내
     */
    private fun scheduleDaily(messageState: MutableState<String?>) {
        lifecycleScope.launch {
            fun delayMinutesFromNowOr15(time: String): Long {
                return runCatching {
                    val t = LocalTime.parse(time) // "HH:mm" 기대
                    val now = LocalTime.now()
                    val base = if (now.isBefore(t)) {
                        Duration.between(now, t)
                    } else {
                        Duration.between(now, t).plusDays(1) // 내일 같은 시각
                    }
                    val minutes = base.toMinutes()
                    val adjusted = minutes.coerceAtLeast(15)
                    if (minutes < 15) {
                        messageState.value = "WorkManager 제약으로 최소 15분 후에 시작합니다."
                    }
                    adjusted
                }.getOrElse {
                    messageState.value = "시간 형식이 올바르지 않아 15분 후로 설정했습니다. (예: 08:30)"
                    15L
                }
            }

            val u = prefs.flow.first()

            val gen = PeriodicWorkRequestBuilder<GenerateVideoWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMinutesFromNowOr15(u.genTime), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED) // 네트워크 필요
                        .build()
                )
                .build()

            val apply = PeriodicWorkRequestBuilder<ApplyWallpaperWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMinutesFromNowOr15(u.applyTime), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                "gen_daily", ExistingPeriodicWorkPolicy.UPDATE, gen
            )
            WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                "apply_daily", ExistingPeriodicWorkPolicy.UPDATE, apply
            )

            messageState.value = "매일 스케줄을 등록했습니다."
        }
    }

    /**
     * 즉시 한 번만 생성 워커 실행 (테스트 버튼용)
     */
    private fun runGenerateOnce() {
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<GenerateVideoWorker>().build()
        )
    }
}
