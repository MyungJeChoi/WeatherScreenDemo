package com.example.lockloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val applyTime: String
)

// Composable: 함수형 UI 선언
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onChange: (SettingsUiState) -> Unit,
    onSaveAndSchedule: () -> Unit,
    onGenerateNow: () -> Unit,
    onApplyNow: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(value = state.cast, onValueChange = { onChange(state.copy(cast = it)) },
            label = { Text("등장인물(프롬프트)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = state.background, onValueChange = { onChange(state.copy(background = it)) },
            label = { Text("배경(프롬프트)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = state.aspect, onValueChange = { onChange(state.copy(aspect = it)) },
            label = { Text("가로세로비 (예: 9:16 / 16:9)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.duration, onValueChange = { onChange(state.copy(duration = it)) },
            label = { Text("길이(초)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = state.genTime, onValueChange = { onChange(state.copy(genTime = it)) },
            label = { Text("생성 시각 (HH:mm) (최소 15분 이후)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = state.applyTime, onValueChange = { onChange(state.copy(applyTime = it)) },
            label = { Text("적용 시각 (HH:mm)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSaveAndSchedule, modifier = Modifier.fillMaxWidth()) {
            Text("저장 & 매일 스케줄 등록")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGenerateNow, modifier = Modifier.fillMaxWidth()) {
            Text("지금 바로 생성(테스트)")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onApplyNow, modifier = Modifier.fillMaxWidth()) {
            Text("지금 바로 적용(라이브 월페이퍼)")
        }
    }
}


