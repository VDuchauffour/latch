package app.latch.domain

/**
 * Pure operations on the category list. Every function returns a new list sorted by [Category.order]
 * with orders renumbered `0..n-1`; unknown ids are ignored (the list is returned normalized).
 */
object CategoryOps {

    fun normalize(categories: List<Category>): List<Category> =
        categories.sortedBy { it.order }.mapIndexed { i, c -> if (c.order == i) c else c.copy(order = i) }

    fun create(categories: List<Category>, id: String, name: String): List<Category> {
        val clean = name.trim()
        if (clean.isEmpty() || categories.any { it.id == id }) return normalize(categories)
        return normalize(normalize(categories) + Category(id, clean, categories.size, emptyList()))
    }

    fun rename(categories: List<Category>, id: String, name: String): List<Category> {
        val clean = name.trim()
        if (clean.isEmpty()) return normalize(categories)
        return normalize(categories.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun delete(categories: List<Category>, id: String): List<Category> = normalize(categories.filterNot { it.id == id })

    /** Adds [app] at the end of the category; no-op if it's already there. */
    fun addApp(categories: List<Category>, id: String, app: AppKey): List<Category> =
        normalize(categories.map { if (it.id == id && app !in it.apps) it.copy(apps = it.apps + app) else it })

    fun removeApp(categories: List<Category>, id: String, app: AppKey): List<Category> =
        normalize(categories.map { if (it.id == id) it.copy(apps = it.apps - app) else it })

    /** Moves [app] from one category to another. Same source and target is a no-op. */
    fun moveApp(categories: List<Category>, fromId: String, toId: String, app: AppKey): List<Category> {
        if (fromId == toId || categories.none { it.id == toId }) return normalize(categories)
        return addApp(removeApp(categories, fromId, app), toId, app)
    }

    /** Moves category [id] to position [toIndex] (clamped). */
    fun reorder(categories: List<Category>, id: String, toIndex: Int): List<Category> {
        val list = normalize(categories).toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return list
        val item = list.removeAt(from)
        list.add(toIndex.coerceIn(0, list.size), item)
        return list.mapIndexed { i, c -> c.copy(order = i) }
    }

    /** Removes every app for which [isGone] returns true from all categories. */
    fun prune(categories: List<Category>, isGone: (AppKey) -> Boolean): List<Category> =
        normalize(categories.map { c -> if (c.apps.any(isGone)) c.copy(apps = c.apps.filterNot(isGone)) else c })

    /** Removes every app not in [installed] from all categories. */
    fun pruneTo(categories: List<Category>, installed: Set<AppKey>): List<Category> = prune(categories) { it !in installed }

    /** Resolves each category's keys to installed [AppEntry]s, preserving the category's own app order. */
    fun resolve(categories: List<Category>, installed: Map<AppKey, AppEntry>): List<Pair<Category, List<AppEntry>>> =
        normalize(categories).map { c -> c to c.apps.mapNotNull { installed[it] } }
}
