package dev.waypad.android.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.waypad.android.core.model.StreamProfile
import dev.waypad.android.core.model.StreamSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "waypad_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val STREAM_PROFILE = stringPreferencesKey("stream_profile")
        val STREAM_MAX_FPS = intPreferencesKey("stream_max_fps")
        val STREAM_JPEG_QUALITY = intPreferencesKey("stream_jpeg_quality")
        val STREAM_MAX_DIMENSION = intPreferencesKey("stream_max_dimension")
        val SHOW_STATS = booleanPreferencesKey("show_stats")
        val HAPTICS = booleanPreferencesKey("haptics")
        val GAME_MODE = booleanPreferencesKey("game_mode")
        val FIRST_RUN = booleanPreferencesKey("first_run")
    }

    val streamSettings: Flow<StreamSettings> = context.dataStore.data.map { prefs ->
        val isFirstRun = prefs[Keys.FIRST_RUN] != false
        val profileName = prefs[Keys.STREAM_PROFILE]
        val profile = StreamProfile.entries.find { it.name == profileName }

        if (isFirstRun || profile == null) {
            // First run or missing data: default to Game mode (60 fps) for best controller experience
            StreamProfile.Game.toStreamSettings(showStats = prefs[Keys.SHOW_STATS] ?: true)
        } else {
            StreamSettings(
                profile = profile,
                maxFps = prefs[Keys.STREAM_MAX_FPS] ?: profile.defaultFps,
                jpegQuality = prefs[Keys.STREAM_JPEG_QUALITY] ?: profile.defaultQuality,
                maxDimension = prefs[Keys.STREAM_MAX_DIMENSION] ?: profile.defaultMaxDimension,
                showStats = prefs[Keys.SHOW_STATS] ?: true,
            )
        }
    }

    val haptics: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS] ?: true }
    val gameMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.GAME_MODE] ?: false }

    suspend fun saveStreamSettings(settings: StreamSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STREAM_PROFILE] = settings.profile.name
            prefs[Keys.STREAM_MAX_FPS] = settings.maxFps
            prefs[Keys.STREAM_JPEG_QUALITY] = settings.jpegQuality
            prefs[Keys.STREAM_MAX_DIMENSION] = settings.maxDimension
            prefs[Keys.SHOW_STATS] = settings.showStats
            prefs[Keys.FIRST_RUN] = false
        }
    }

    suspend fun saveHaptics(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTICS] = enabled
        }
    }

    suspend fun saveGameMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GAME_MODE] = enabled
            prefs[Keys.FIRST_RUN] = false
        }
    }
}
