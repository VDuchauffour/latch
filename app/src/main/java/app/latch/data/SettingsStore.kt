package app.latch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences shown in the Latch settings screen. */
data class LatchSettings(
    /** How many app tiles fit across an open drawer on home. */
    val appsPerDrawer: Int = DEFAULT_APPS_PER_DRAWER,
    /** Show app icons in grayscale. */
    val grayIcons: Boolean = true,
    val tileBackground: TileBackground = TileBackground.DEFAULT,
    val accentColor: AccentColor = AccentColor.DEFAULT
) {
    companion object {
        const val DEFAULT_APPS_PER_DRAWER = 5
        val APPS_PER_DRAWER_RANGE = 3..7
    }
}

enum class TileBackground(val label: String, val alpha: Float) {
    None("None", 0f),
    Faint("Faint", 0.04f),
    Subtle("Subtle", 0.08f),
    Medium("Medium", 0.14f),
    LightGray("Light gray", 0.22f);

    companion object {
        val DEFAULT = Subtle
    }
}

/** Highlight color for selected and active elements, as 0xAARRGGBB. Yellow matches the original look. */
enum class AccentColor(val label: String, val argb: Long) {
    Yellow("Yellow", 0xFFFFFC33),
    Lime("Lime", 0xFFB8FF33),
    Mint("Mint", 0xFF33FFB5),
    Cyan("Cyan", 0xFF33E0FF),
    Blue("Blue", 0xFF4D8DFF),
    Violet("Violet", 0xFFB36BFF),
    Pink("Pink", 0xFFFF4FA3),
    Coral("Coral", 0xFFFF5C5C),
    Orange("Orange", 0xFFFF8A33),
    Amber("Amber", 0xFFFFC233);

    companion object {
        val DEFAULT = Yellow
    }
}

/** Persists [LatchSettings] next to the categories, in the same DataStore. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.latchDataStore)

    val settings: Flow<LatchSettings> = dataStore.data.map { prefs ->
        LatchSettings(
            appsPerDrawer = (prefs[APPS_PER_DRAWER] ?: LatchSettings.DEFAULT_APPS_PER_DRAWER)
                .coerceIn(LatchSettings.APPS_PER_DRAWER_RANGE),
            grayIcons = prefs[GRAY_ICONS] ?: true,
            tileBackground = TileBackground.entries.find { it.name == prefs[TILE_BACKGROUND] } ?: TileBackground.DEFAULT,
            accentColor = AccentColor.entries.find { it.name == prefs[ACCENT_COLOR] } ?: AccentColor.DEFAULT
        )
    }

    suspend fun setAppsPerDrawer(count: Int) {
        dataStore.edit { it[APPS_PER_DRAWER] = count.coerceIn(LatchSettings.APPS_PER_DRAWER_RANGE) }
    }

    suspend fun setGrayIcons(enabled: Boolean) {
        dataStore.edit { it[GRAY_ICONS] = enabled }
    }

    suspend fun setTileBackground(background: TileBackground) {
        dataStore.edit { it[TILE_BACKGROUND] = background.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { it[ACCENT_COLOR] = color.name }
    }

    private companion object {
        val APPS_PER_DRAWER = intPreferencesKey("apps_per_drawer")
        val GRAY_ICONS = booleanPreferencesKey("gray_icons")
        val TILE_BACKGROUND = stringPreferencesKey("tile_background")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
    }
}
