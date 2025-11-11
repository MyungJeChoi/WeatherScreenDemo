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
                        lifecycleScope.launch {
                            // 1) 시간 검증/표준화
                            val gen   = normalizeTimeOrNull(new.genTime)
                            val apply = normalizeTimeOrNull(new.applyTime)
                            if (gen == null || apply == null) {
                                messageState.value = "시간 형식이 올바르지 않습니다. 예: 04:56 또는 0456"
                                return@launch
                            }

                            // 2) 길이 검증
                            if (!isDurationValid(new.duration)) {
                                messageState.value = "길이는 1~600 사이의 숫자여야 합니다."
                                return@launch
                            }

                            // 3) 저장 (표준화된 HH:mm 사용)
                            prefs.save(
                                UserPrefs(
                                    cast = new.cast,
                                    background = new.background,
                                    aspect = new.aspect,
                                    durationSec = new.duration.toInt(),
                                    genTime = gen,
                                    applyTime = apply,
                                    latestVideoPath = u.latestVideoPath
                                )
                            )
                            messageState.value = "설정을 저장했습니다."
                        }
                    },

                    onSaveAndSchedule = {
                        lifecycleScope.launch {
                            // 1) 방금 저장된 최신값을 가져온다 (SettingsScreen이 onSave(new) 호출 후라고 가정)
                            val cur = prefs.flow.first()

                            // 2) 검증/정규화
                            val gen   = normalizeTimeOrNull(cur.genTime)
                            val apply = normalizeTimeOrNull(cur.applyTime)
                            val dur   = cur.durationSec

                            if (gen == null || apply == null) {
                                messageState.value = "시간 형식이 올바르지 않습니다. 예: 04:56 또는 0456"
                                return@launch
                            }
                            if (dur !in 1..600) {
                                messageState.value = "길이는 1~600 사이의 숫자여야 합니다."
                                return@launch
                            }

                            // 3) 시간이 '0456' 같은 형식이라면 '04:56'으로 보정 저장
                            if (gen != cur.genTime || apply != cur.applyTime) {
                                prefs.save(cur.copy(genTime = gen, applyTime = apply))
                            }

                            // 4) 스케줄 등록
                            scheduleDaily(messageState)
                            messageState.value = "설정을 저장하고 스케줄을 등록했습니다."
                        }
                    },


                    onGenerateNow = { runGenerateOnce() },
                    onApplyNow = {
                        startActivity(Intent(this@MainActivity, SetWallpaperActivity::class.java))
                    },
                    messageState = messageState
                )
            }
        }
    }

    // "0456" → "04:56", "4:56" → "04:56" 로 표준화. 잘못된 형식이면 null
    private fun normalizeTimeOrNull(raw: String): String? {
        val t = raw.trim()
        val candidate = when {
            Regex("""^\d{4}$""").matches(t) -> t.substring(0, 2) + ":" + t.substring(2)
            Regex("""^\d{1,2}:\d{2}$""").matches(t) -> t
            else -> return null
        }
        return runCatching { LocalTime.parse(candidate) }
            .map { "%02d:%02d".format(it.hour, it.minute)  }
            .getOrNull()
    }

    private fun isDurationValid(s: String): Boolean =
        s.toIntOrNull()?.let { it in 1..10 } == true   // 필요시 범위 조정


    /**
     * 매일 '생성 시간'과 '적용 시간'에 맞춰 워커 등록.
     * - 최소 초기 지연 15분(WorkManager 제약 하한)을 보장
     * - 시간 파싱 실패 시 15분으로 대체하고 팝업으로 안내
     */
    private fun scheduleDaily(messageState: MutableState<String?>) {
        lifecycleScope.launch {
            // 현재 시각
            val now = java.time.LocalDateTime.now()
            val fmt = java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")

            // HH:mm 문자열을 LocalTime으로 (이미 onSave에서 표준화 저장했다고 가정)
            fun parseOrNull(s: String): LocalTime? =
                runCatching { LocalTime.parse(s) }.getOrNull()

            // 다음 실행 정보 계산 (최소 15분 규칙 적용)
            data class NextRunInfo(
                val requestedText: String,               // 사용자가 저장한 문자열(HH:mm)
                val scheduledAt: java.time.LocalDateTime, // 실제 첫 실행 시각
                val delayMinutes: Long,                  // initialDelay
                val adjustedByMinRule: Boolean           // 15분 룰 적용 여부
            )

            fun computeNextRun(requested: String): NextRunInfo? {
                val t = parseOrNull(requested) ?: return null
                // 오늘 그 시간
                var target = now.withHour(t.hour).withMinute(t.minute).withSecond(0).withNano(0)
                if (!target.isAfter(now)) target = target.plusDays(1) // 이미 지났으면 내일
                var delay = Duration.between(now, target).toMinutes()
                var adjusted = false
                if (delay < 15) {
                    delay = 15
                    adjusted = true
                }
                val scheduled = now.plusMinutes(delay)
                return NextRunInfo(
                    requestedText = requested,
                    scheduledAt = scheduled,
                    delayMinutes = delay,
                    adjustedByMinRule = adjusted
                )
            }

            val u = prefs.flow.first()

            val genInfo = computeNextRun(u.genTime)
            val applyInfo = computeNextRun(u.applyTime)

            if (genInfo == null || applyInfo == null) {
                messageState.value = "시간 형식이 올바르지 않습니다. (예: 04:56)"
                return@launch
            }

            // Work 요청 만들기
            val gen = PeriodicWorkRequestBuilder<GenerateVideoWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(genInfo.delayMinutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            val apply = PeriodicWorkRequestBuilder<ApplyWallpaperWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(applyInfo.delayMinutes, TimeUnit.MINUTES)
                .build()

            val wm = WorkManager.getInstance(this@MainActivity)
            wm.enqueueUniquePeriodicWork(
                "gen_daily", ExistingPeriodicWorkPolicy.UPDATE, gen
            )
            wm.enqueueUniquePeriodicWork(
                "apply_daily", ExistingPeriodicWorkPolicy.UPDATE, apply
            )

            // 사용자에게 실제 예약 결과를 명확히 보여주기
            val genMsg = buildString {
                append("생성: 요청 ").append(genInfo.requestedText)
                append(" → 다음 실행 ").append(genInfo.scheduledAt.format(fmt))
                append(" (지연 ").append(genInfo.delayMinutes).append("분")
                if (genInfo.adjustedByMinRule) append(", 최소 15분 규칙 적용")
                append(")")
            }
            val applyMsg = buildString {
                append("적용: 요청 ").append(applyInfo.requestedText)
                append(" → 다음 실행 ").append(applyInfo.scheduledAt.format(fmt))
                append(" (지연 ").append(applyInfo.delayMinutes).append("분")
                if (applyInfo.adjustedByMinRule) append(", 최소 15분 규칙 적용")
                append(")")
            }

            messageState.value = "스케줄을 등록했습니다.\n\n$genMsg\n$applyMsg"
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
