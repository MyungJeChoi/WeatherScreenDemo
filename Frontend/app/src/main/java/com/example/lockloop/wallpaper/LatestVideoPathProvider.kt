package com.example.lockloop.wallpaper

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.lockloop.data.userPrefsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


private object Keys { val LATEST = stringPreferencesKey("latest_video_path") }

object LatestVideoPathProvider {
    suspend fun get(context: Context): String? =
        context.userPrefsDataStore.data.map { it[Keys.LATEST] }.first()
}
