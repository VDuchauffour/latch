package app.latch.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.latch.domain.Category
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

internal val Context.latchDataStore: DataStore<Preferences> by preferencesDataStore(name = "latch")

/** Persists categories as a single JSON string in DataStore (see [CategoryCodec]). */
class CategoryStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.latchDataStore)

    /** Emits the stored categories; nothing is emitted until the document exists (see [seedIfEmpty]). */
    val categories: Flow<List<Category>> = dataStore.data.mapNotNull { it[KEY] }.map { text ->
        CategoryCodec.decode(text) ?: run {
            Log.w(TAG, "Unreadable categories document, showing nothing")
            emptyList()
        }
    }

    /** On first launch, stores an empty category list so [categories] starts emitting. */
    suspend fun seedIfEmpty() {
        dataStore.edit { prefs ->
            if (prefs[KEY] == null) {
                prefs[KEY] = CategoryCodec.encode(emptyList())
            }
        }
    }

    /** Atomically applies [transform]. A corrupt document is left untouched. */
    suspend fun update(transform: (List<Category>) -> List<Category>) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { CategoryCodec.decode(it) ?: return@edit } ?: emptyList()
            val next = transform(current)
            if (next != current) prefs[KEY] = CategoryCodec.encode(next)
        }
    }

    companion object {
        private const val TAG = "CategoryStore"
        val KEY = stringPreferencesKey("categories_json")
        fun newId(): String = UUID.randomUUID().toString()
    }
}
