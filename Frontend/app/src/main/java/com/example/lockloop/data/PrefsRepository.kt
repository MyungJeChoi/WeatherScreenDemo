package com.example.lockloop.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PrefsRepository(private val context: Context) {
    private object Keys {
        val CAST = stringPreferencesKey("cast")
        val BG = stringPreferencesKey("bg")
        val ASPECT = stringPreferencesKey("aspect")
        val DURATION = intPreferencesKey("duration")
        val GEN = stringPreferencesKey("gen_time")
        val APPLY = stringPreferencesKey("apply_time")
        val LATEST = stringPreferencesKey("latest_video_path")
    }

    val flow: Flow<UserPrefs> = context.userPrefsDataStore.data.map { p ->
        UserPrefs(
            cast = p[Keys.CAST] ?: "a cute small standing Pomeranian in a blue shirt",
            background = p[Keys.BG] ?: "the Arc de Triomphe in Paris",
            aspect = p[Keys.ASPECT] ?: "9:16",
            durationSec = p[Keys.DURATION] ?: 8,
            genTime = p[Keys.GEN] ?: "11:29",
            applyTime = p[Keys.APPLY] ?: "03:30",
            latestVideoPath = p[Keys.LATEST]
        )
    }

    suspend fun save(u: UserPrefs) {
        context.userPrefsDataStore.edit { e ->
            e[Keys.CAST] = u.cast
            e[Keys.BG] = u.background
            e[Keys.ASPECT] = u.aspect
            e[Keys.DURATION] = u.durationSec
            e[Keys.GEN] = u.genTime
            e[Keys.APPLY] = u.applyTime
            u.latestVideoPath?.let { e[Keys.LATEST] = it }
        }
    }

    suspend fun setLatestVideo(path: String) {
        context.userPrefsDataStore.edit { e ->
            e[Keys.LATEST] = path
        }
    }
}
