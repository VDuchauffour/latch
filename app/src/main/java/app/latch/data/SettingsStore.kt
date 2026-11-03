package app.latch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences shown in the Latch settings screen. */
data class LatchSettings(
    /** How many app tiles fit across an open drawer on home. */
    val appsPerDrawer: Int = DEFAULT_APPS_PER_DRAWER,
    /** Show app icons in grayscale. */
    val grayIcons: Boolean = true
) {
    companion object {
        const val DEFAULT_APPS_PER_DRAWER = 5
        val APPS_PER_DRAWER_RANGE = 3..7
    }
}

/** Persists [LatchSettings] next to the categories, in the same DataStore. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.latchDataStore)

    val settings: Flow<LatchSettings> = dataStore.data.map { prefs ->
        LatchSettings(
            appsPerDrawer = (prefs[APPS_PER_DRAWER] ?: LatchSettings.DEFAULT_APPS_PER_DRAWER)
                .coerceIn(LatchSettings.APPS_PER_DRAWER_RANGE),
            grayIcons = prefs[GRAY_ICONS] ?: true
        )
    }

    suspend fun setAppsPerDrawer(count: Int) {
        dataStore.edit { it[APPS_PER_DRAWER] = count.coerceIn(LatchSettings.APPS_PER_DRAWER_RANGE) }
    }

    suspend fun setGrayIcons(enabled: Boolean) {
        dataStore.edit { it[GRAY_ICONS] = enabled }
    }

    private companion object {
        val APPS_PER_DRAWER = intPreferencesKey("apps_per_drawer")
        val GRAY_ICONS = booleanPreferencesKey("gray_icons")
    }
}
