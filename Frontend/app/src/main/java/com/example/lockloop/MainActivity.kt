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
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import com.example.lockloop.data.PrefsRepository
import com.example.lockloop.data.UserPrefs
import com.example.lockloop.ui.SettingsScreen
import com.example.lockloop.ui.SettingsUiState
import com.example.lockloop.util.NotificationHelper
import com.example.lockloop.wallpaper.SetWallpaperActivity
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

            val isGenerating = remember { mutableStateOf(false) }

            Surface(color = MaterialTheme.colorScheme.background) {
                SettingsScreen(
                    ui = SettingsUiState(
                        cast = u.cast,
                        background = u.background,
                        aspect = u.aspect,
                        duration = u.durationSec.toString(),
                        genTime = u.genTime,
                        latestVideoPath = u.latestVideoPath
                    ),
                    onSaveAndSchedule = { new ->
                        lifecycleScope.launch {
                            // 1) 입력값 검증/정규화
                            val gen = normalizeTimeOrNull(new.genTime)
                            if (gen == null) {
                                messageState.value = "시간 형식이 올바르지 않습니다. 예: 04:56 또는 0456"
                                return@launch
                            }

                            val dur = new.duration.toIntOrNull()
                            if (dur == null || dur !in 1..10) {
                                messageState.value = "길이는 1~8 사이의 숫자여야 합니다."
                                return@launch
                            }

                            // 2) prefs에 바로 저장 (현재 값 기반으로 업데이트)
                            val cur = prefs.flow.first()
                            val updated = cur.copy(
                                cast        = new.cast,
                                background  = new.background,
                                aspect      = new.aspect,
                                durationSec = dur,
                                genTime     = gen
                            )
                            prefs.save(updated)

                            // 3) 방금 입력한 gen으로 스케줄 등록
                            scheduleDaily(gen, messageState)
                        }
                    },


                    onGenerateNow = { 
                        isGenerating.value = true
                        runGenerateOnce(isGenerating)
                    },
                    onApplyNow = {
                        startActivity(Intent(this@MainActivity, SetWallpaperActivity::class.java))
                    },
                    messageState = messageState,
                    isGenerating = isGenerating
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
     * - 최소 초기 지연 5분(WorkManager 제약 하한)을 보장
     * - 시간 파싱 실패 시 5분으로 대체하고 팝업으로 안내
     */
    private fun scheduleDaily(
        genTime: String,
        messageState: MutableState<String?>
    ) {
        lifecycleScope.launch {
            val now = java.time.LocalDateTime.now()
            val fmt = java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")

            fun parseOrNull(s: String): LocalTime? =
                runCatching { LocalTime.parse(s) }.getOrNull()

            data class NextRunInfo(
                val requestedText: String,
                val scheduledAt: java.time.LocalDateTime,
                val delayMinutes: Long,
            )

            fun computeNextRun(requested: String): NextRunInfo? {
                val t = parseOrNull(requested) ?: return null

                var target = now.withHour(t.hour)
                    .withMinute(t.minute)
                    .withSecond(0)
                    .withNano(0)

                if (!target.isAfter(now)) {
                    target = target.plusDays(1)
                }

                var delay = Duration.between(now, target).toMinutes()
                if (delay < 5) {
                    delay = 5
                    target = now.plusMinutes(delay)
                }

                return NextRunInfo(
                    requestedText = requested,
                    scheduledAt = target,
                    delayMinutes = delay
                )
            }

            val genInfo = computeNextRun(genTime)

            if (genInfo == null) {
                messageState.value = "시간 형식이 올바르지 않습니다. (예: 04:56)"
                return@launch
            }

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

            val wm = WorkManager.getInstance(this@MainActivity)
            wm.cancelUniqueWork("gen_daily")
            wm.enqueueUniquePeriodicWork(
                "gen_daily",
                ExistingPeriodicWorkPolicy.UPDATE,
                gen
            )

            val genMsg = buildString {
                append("생성+적용 요청 ").append(genInfo.requestedText)
                append(" → 다음 실행 ").append(genInfo.scheduledAt.format(fmt))
                append(" (지연 ").append(genInfo.delayMinutes).append("분, 최소 5분 규칙 적용)")
            }

            messageState.value = "스케줄을 등록했습니다.\n\n$genMsg"
        }
    }
    /**
     * 즉시 한 번만 생성 워커 실행 (테스트 버튼용)
     */
    private fun runGenerateOnce(isGenerating: MutableState<Boolean>) {
        val wm = WorkManager.getInstance(this)
        val req = OneTimeWorkRequestBuilder<GenerateVideoWorker>()
            // ✅ 네트워크 없으면 바로 실패하니 제약 걸어두기
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // ✅ 즉시 실행 시도 (쿼터 없으면 일반 실행으로 폴백)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        // enqueue
        wm.enqueue(req)

        // 상태 관찰 → 로딩 끄기/성공·실패 메시지
        wm.getWorkInfoByIdLiveData(req.id).observe(this) { info ->
            when (info.state) {
                WorkInfo.State.ENQUEUED  -> { /* 필요시 메시지 */ }
                WorkInfo.State.RUNNING   -> { isGenerating.value = true }
                WorkInfo.State.SUCCEEDED -> {
                    isGenerating.value = false
                    startActivity(Intent(this@MainActivity, SetWallpaperActivity::class.java))
                    // 서버/다운로드/후처리까지 끝났음
                    // messageState는 MainActivity에서 접근해야 하므로 필요시 이벤트로 전달
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    isGenerating.value = false
                    // 실패 메시지 처리는 아래 3번에서 Worker 측에서 넘겨주는 outputData로 처리 가능
                }
                else -> {}
            }
        }
    }

}
