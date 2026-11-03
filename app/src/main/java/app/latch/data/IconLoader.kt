package app.latch.data

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.latch.domain.AppKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Loads badged app icons off the main thread and keeps them in an LRU cache. */
class IconLoader(context: Context, private val repository: AppRepository) {
    private val density = context.resources.displayMetrics.densityDpi
    private val sizePx = (48 * context.resources.displayMetrics.density).toInt()

    private val cache = object : LruCache<AppKey, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: AppKey, value: ImageBitmap) = value.width * value.height * 4
    }

    private val _version = MutableStateFlow(0)

    /** Bumped whenever cached icons are invalidated, so the UI can reload them. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun cached(key: AppKey): ImageBitmap? = cache.get(key)

    suspend fun load(key: AppKey): ImageBitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val info = repository.info(key) ?: return@withContext null
            val bitmap = runCatching {
                // getBadgedIcon adds the work-profile badge when relevant.
                info.getBadgedIcon(density).toBitmap(sizePx, sizePx).asImageBitmap()
            }.getOrNull() ?: return@withContext null
            bitmap.prepareToDraw()
            cache.put(key, bitmap)
            bitmap
        }
    }

    fun invalidate(change: PackageChange) {
        cache.snapshot().keys
            .filter { it.packageName == change.packageName && it.userSerial == change.userSerial }
            .forEach { cache.remove(it) }
        _version.value++
    }

    private companion object {
        fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
