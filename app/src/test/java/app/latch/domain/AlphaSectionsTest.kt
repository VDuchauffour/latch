package app.latch.domain

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaSectionsTest {

    private fun collator(locale: Locale = Locale.FRENCH) =
        Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

    private fun entries(vararg labels: String) =
        labels.mapIndexed { i, l -> AppEntry(AppKey("pkg$i", "Cls$i", 0), l) }

    private fun build(vararg labels: String, locale: Locale = Locale.FRENCH) =
        AlphaSections.build(entries(*labels), collator(locale))

    private fun List<Section>.flat() = map { s -> s.header to s.apps.map { it.label } }

    @Test fun `accented labels sort and group with their base letter`() {
        val sections = build("Airbnb", "AlloCiné", "Éditeur", "Alan", "Email", "AFinity")
        assertEquals(
            listOf(
                "A" to listOf("AFinity", "Airbnb", "Alan", "AlloCiné"),
                "E" to listOf("Éditeur", "Email"),
            ),
            sections.flat(),
        )
    }

    @Test fun `digits and symbols go to the leading other bucket`() {
        val sections = build("Bolt", "2FAS Auth", "@Home", "Alan", "9GAG")
        assertEquals(AlphaSections.OTHER, sections.first().header)
        assertEquals(setOf("2FAS Auth", "@Home", "9GAG"), sections.first().apps.map { it.label }.toSet())
        assertEquals(listOf("#", "A", "B"), sections.map { it.header })
    }

    @Test fun `emoji start goes to other bucket`() {
        val sections = build("😀 Smile", "Zoom")
        assertEquals(listOf("#" to listOf("😀 Smile"), "Z" to listOf("Zoom")), sections.flat())
    }

    @Test fun `mixed case is ignored for sorting and headers`() {
        val sections = build("bandcamp", "Binance", "b.tv", "BOLT")
        assertEquals(listOf("B"), sections.map { it.header })
        assertEquals(listOf("bandcamp", "Binance", "BOLT", "b.tv").sortedWith(collator()), sections[0].apps.map { it.label })
    }

    @Test fun `leading whitespace is ignored and empty labels are other`() {
        assertEquals("A", AlphaSections.headerFor("  alan"))
        assertEquals(AlphaSections.OTHER, AlphaSections.headerFor(""))
        assertEquals(AlphaSections.OTHER, AlphaSections.headerFor("   "))
    }

    @Test fun `header folds accents and keeps one code point`() {
        assertEquals("E", AlphaSections.headerFor("éa"))
        assertEquals("C", AlphaSections.headerFor("ça"))
        assertEquals("S", AlphaSections.headerFor("ßtraße"))
        assertEquals("Ж", AlphaSections.headerFor("жук"))
    }

    @Test fun `swedish locale keeps every app and has no duplicate headers`() {
        val sections = build("Öl", "Ost", "Zebra", "Apa", locale = Locale("sv", "SE"))
        val headers = sections.map { it.header }
        assertEquals(headers.distinct(), headers)
        assertEquals(4, sections.sumOf { it.apps.size })
        assertTrue(sections.first { it.header == "O" }.apps.map { it.label }.containsAll(listOf("Öl", "Ost")))
    }

    @Test fun `ties are broken deterministically`() {
        val a = build("alan", "Alan", "ALAN")
        val b = AlphaSections.build(entries("alan", "Alan", "ALAN").reversed(), collator())
        assertEquals(a, b)
    }

    @Test fun `empty input gives no sections`() {
        assertEquals(emptyList<Section>(), build())
    }
}
