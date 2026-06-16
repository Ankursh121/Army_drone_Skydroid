package com.ladakh.drone.gcs.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "gcs_settings")

@Singleton
class SettingsManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private val RTSP_URL_KEY = stringPreferencesKey("rtsp_url")
        private const val DEFAULT_RTSP_URL = "rtsp://username:password@192.168.1.100:554/live"
    }

    val rtspUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[RTSP_URL_KEY] ?: DEFAULT_RTSP_URL
    }

    suspend fun saveRtspUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[RTSP_URL_KEY] = url
        }
    }
}
