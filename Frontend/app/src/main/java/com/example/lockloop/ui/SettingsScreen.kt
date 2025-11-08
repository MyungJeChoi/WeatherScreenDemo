package com.example.lockloop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class SettingsUiState(
    val cast: String,
    val background: String,
    val aspect: String,
    val duration: String,
    val genTime: String,
    val applyTime: String,
    val latestVideoPath: String?
)

data class EditableSettings(
    var cast: String,
    var background: String,
    var aspect: String,
    var duration: String,
    var genTime: String,
    var applyTime: String
)

@Composable
fun SettingsScreen(
    ui: SettingsUiState,
    onSave: (EditableSettings) -> Unit,
    onSaveAndSchedule: () -> Unit,
    onGenerateNow: () -> Unit,
    onApplyNow: () -> Unit,
    messageState: MutableState<String?> = remember { mutableStateOf(null) }
) {
    var cast by remember(ui.cast) { mutableStateOf(ui.cast) }
    var bg by remember(ui.background) { mutableStateOf(ui.background) }
    var aspect by remember(ui.aspect) { mutableStateOf(ui.aspect) }
    var duration by remember(ui.duration) { mutableStateOf(ui.duration) }
    var genTime by remember(ui.genTime) { mutableStateOf(ui.genTime) }
    var applyTime by remember(ui.applyTime) { mutableStateOf(ui.applyTime) }

    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = cast, onValueChange = { cast = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("캐릭터 (subject)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = bg, onValueChange = { bg = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("배경 (place)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = aspect, onValueChange = { aspect = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("화면비 (예: 9:16)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = duration, onValueChange = { duration = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), label = { Text("길이(초)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = genTime, onValueChange = { genTime = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("생성 예약 시간 (HH:mm)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = applyTime, onValueChange = { applyTime = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("배경화면 설정 예약 시간 (HH:mm)") }
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onSave(
                    EditableSettings(
                        cast = cast, background = bg, aspect = aspect, duration = duration,
                        genTime = genTime, applyTime = applyTime
                    )
                )
                messageState.value = "저장했습니다."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("저장") }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSave(
                    EditableSettings(
                        cast = cast, background = bg, aspect = aspect, duration = duration,
                        genTime = genTime, applyTime = applyTime
                    )
                )
                onSaveAndSchedule()
                messageState.value = "매일 생성/적용 스케줄을 등록했습니다."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("저장 & 매일 스케줄 등록") }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onGenerateNow, modifier = Modifier.fillMaxWidth()) {
            Text("지금 바로 생성(테스트)")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onApplyNow, modifier = Modifier.fillMaxWidth()) {
            Text("지금 바로 적용(라이브 월페이퍼)")
        }
    }

    // Compose AlertDialog (팝업) — 버튼을 누르면 위에서 messageState에 메시지를 넣어 띄웁니다.
    val message = messageState.value
    if (message != null) {
        AlertDialog(
            onDismissRequest = { messageState.value = null },
            title = { Text("알림") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { messageState.value = null }) { Text("확인") }
            }
        )
    }
}
