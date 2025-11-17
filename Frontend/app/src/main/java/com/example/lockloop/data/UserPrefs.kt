package com.example.lockloop.data

data class UserPrefs(
    val cast: String = "a cute small standing Pomeranian in a blue shirt",
    val background: String = "the Arc de Triomphe in Paris",
    val aspect: String = "9:16",
    val durationSec: Int = 8,
    val genTime: String = "11:29",   // HH:mm
    val latestVideoPath: String? = null // 후처리된 mp4 경로
)