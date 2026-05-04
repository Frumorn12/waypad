package dev.waypad.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waypad.android.core.model.StreamProfile
import dev.waypad.android.core.storage.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear DataStore before each test
        context.filesDir.resolve("datastore/waypad_settings.preferences_pb").delete()
        repo = SettingsRepository(context)
    }

    @Test
    fun firstRunDefaultsToGameMode60Fps() = runBlocking {
        val settings = repo.streamSettings.first()
        assertEquals(StreamProfile.Game, settings.profile)
        assertEquals(60, settings.maxFps)
        assertEquals(1280, settings.maxDimension)
    }

    @Test
    fun saveAndRestoreStreamSettings() = runBlocking {
        val custom = StreamProfile.LowLatency.toStreamSettings(showStats = false)
        repo.saveStreamSettings(custom)
        val loaded = repo.streamSettings.first()
        assertEquals(StreamProfile.LowLatency, loaded.profile)
        assertEquals(60, loaded.maxFps)
        assertEquals(false, loaded.showStats)
    }

    @Test
    fun saveAndRestoreHaptics() = runBlocking {
        repo.saveHaptics(false)
        val loaded = repo.haptics.first()
        assertEquals(false, loaded)
    }

    @Test
    fun saveAndRestoreGameMode() = runBlocking {
        repo.saveGameMode(true)
        val loaded = repo.gameMode.first()
        assertEquals(true, loaded)
    }
}
