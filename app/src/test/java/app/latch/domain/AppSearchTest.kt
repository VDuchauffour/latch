package app.latch.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {

    private fun entries(vararg labels: String) = labels.mapIndexed { i, l -> AppEntry(AppKey("pkg$i", "Cls$i", 0), l) }

    private fun search(query: String, vararg labels: String) = AppSearch.match(entries(*labels), query).map { it.label }

    @Test fun `blank query matches nothing`() {
        assertEquals(emptyList<String>(), search("  ", "YouTube", "Maps"))
    }

    @Test fun `ignores case and accents`() {
        assertEquals(listOf("Éditeur"), search("EDI", "Éditeur", "Maps"))
        assertEquals(listOf("Caméra"), search("camera", "Caméra", "Maps"))
    }

    @Test fun `label prefix beats word prefix beats substring`() {
        assertEquals(
            listOf("YouTube", "Google TV: For you", "Bayou"),
            search("you", "Bayou", "Google TV: For you", "YouTube", "Maps")
        )
    }

    @Test fun `keeps input order within a tier`() {
        assertEquals(listOf("YouTube", "YouTube Music"), search("youtube", "YouTube", "YouTube Music"))
    }

    @Test fun `non matching apps are dropped`() {
        assertEquals(emptyList<String>(), search("xyz", "YouTube", "Maps"))
    }
}
