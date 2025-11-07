package com.example.lockloop.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// 앱 전역에서 '단 한 번만' 선언해서 재사용합니다.
val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")