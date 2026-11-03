package app.latch.domain

import java.text.Normalizer

object AppSearch {

    /** Best first: label prefix, then word prefix, then substring. Input order is kept within a tier. */
    fun match(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = normalize(query).trim()
        if (q.isEmpty()) return emptyList()
        return apps
            .mapNotNull { app -> rank(normalize(app.label), q)?.let { app to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun rank(label: String, q: String): Int? = when {
        label.startsWith(q) -> 0
        WORD_BREAK.findAll(label).any { label.startsWith(q, it.range.last + 1) } -> 1
        label.contains(q) -> 2
        else -> null
    }

    private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")
    private val MARKS = Regex("\\p{Mn}+")

    internal fun normalize(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()
}
