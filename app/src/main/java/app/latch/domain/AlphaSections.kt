package app.latch.domain

import java.text.Normalizer

/** One drawer section: a header letter (or [AlphaSections.OTHER]) and its apps, already sorted. */
data class Section(
    val header: String,
    val apps: List<AppEntry>,
)

object AlphaSections {
    /** Header for labels that don't start with a letter (digits, symbols, emoji). */
    const val OTHER = "#"

    private val combiningMarks = Regex("\\p{Mn}+")

    /**
     * Sorts [apps] with [labelComparator] (typically a `java.text.Collator` with PRIMARY strength, so
     * "AlloCiné" sorts next to "Allo") and groups them into sections.
     *
     * - Section headers are the first letter of the label, upper-cased with accents removed (`É` → `E`).
     * - Labels starting with anything other than a letter go into [OTHER], which always comes first.
     * - Ties (labels equal for the comparator) are broken deterministically by raw label then key.
     */
    fun build(apps: List<AppEntry>, labelComparator: Comparator<in String>): List<Section> {
        val sorted = apps.sortedWith(
            Comparator<AppEntry> { a, b -> labelComparator.compare(a.label.trim(), b.label.trim()) }
                .thenBy { it.label }
                .thenBy { it.key.packageName }
                .thenBy { it.key.className }
                .thenBy { it.key.userSerial },
        )
        // groupBy keeps first-appearance order and merges non-contiguous runs (e.g. locales where a
        // folded letter like Swedish "Ö" collates after "Z").
        val groups = sorted.groupBy { headerFor(it.label) }
        val other = groups[OTHER]
        return buildList {
            if (other != null) add(Section(OTHER, other))
            groups.forEach { (header, list) -> if (header != OTHER) add(Section(header, list)) }
        }
    }

    /** The section header for a single label. */
    fun headerFor(label: String): String {
        val trimmed = label.trimStart()
        if (trimmed.isEmpty()) return OTHER
        val firstCodePoint = trimmed.codePointAt(0)
        if (!Character.isLetter(firstCodePoint)) return OTHER
        val first = String(Character.toChars(firstCodePoint))
        val folded = Normalizer.normalize(first, Normalizer.Form.NFD).replace(combiningMarks, "")
        val upper = folded.ifEmpty { first }.uppercase()
        // Keep a single code point ("ß".uppercase() is "SS").
        return String(Character.toChars(upper.codePointAt(0)))
    }
}
