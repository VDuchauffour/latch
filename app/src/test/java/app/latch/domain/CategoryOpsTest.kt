package app.latch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryOpsTest {
    private val signal = AppKey("org.thoughtcrime.securesms", "RoutingActivity", 0)
    private val spotify = AppKey("com.spotify.music", "MainActivity", 0)
    private val workSignal = signal.copy(userSerial = 10)

    private fun cats() = listOf(
        Category("t", "Talk", 0, listOf(signal)),
        Category("l", "Listen", 1, emptyList()),
    )

    @Test fun `seed creates default categories in order`() {
        var n = 0
        val seeded = CategoryOps.seed { "id${n++}" }
        assertEquals(CategoryOps.DEFAULT_NAMES, seeded.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), seeded.map { it.order })
        assertTrue(seeded.all { it.apps.isEmpty() })
    }

    @Test fun `create appends trimmed name and ignores blank`() {
        val created = CategoryOps.create(cats(), "s", "  See ")
        assertEquals(listOf("Talk", "Listen", "See"), created.map { it.name })
        assertEquals(2, created.last().order)
        assertEquals(cats(), CategoryOps.create(cats(), "x", "   "))
        assertEquals(cats(), CategoryOps.create(cats(), "t", "Duplicate id"))
    }

    @Test fun `add is idempotent`() {
        val once = CategoryOps.addApp(cats(), "t", signal)
        assertEquals(listOf(signal), once.first { it.id == "t" }.apps)
        val other = CategoryOps.addApp(once, "t", spotify)
        assertEquals(listOf(signal, spotify), other.first { it.id == "t" }.apps)
    }

    @Test fun `same app may be in several categories`() {
        val result = CategoryOps.addApp(cats(), "l", signal)
        assertTrue(result.all { signal in it.apps })
    }

    @Test fun `work profile copy is a distinct app`() {
        val result = CategoryOps.addApp(cats(), "t", workSignal)
        assertEquals(listOf(signal, workSignal), result.first { it.id == "t" }.apps)
    }

    @Test fun `remove only affects the given category`() {
        val both = CategoryOps.addApp(cats(), "l", signal)
        val result = CategoryOps.removeApp(both, "t", signal)
        assertEquals(emptyList<AppKey>(), result.first { it.id == "t" }.apps)
        assertEquals(listOf(signal), result.first { it.id == "l" }.apps)
    }

    @Test fun `move removes from source and adds to target`() {
        val result = CategoryOps.moveApp(cats(), "t", "l", signal)
        assertEquals(emptyList<AppKey>(), result.first { it.id == "t" }.apps)
        assertEquals(listOf(signal), result.first { it.id == "l" }.apps)
        assertEquals(cats(), CategoryOps.moveApp(cats(), "t", "t", signal))
        assertEquals(cats(), CategoryOps.moveApp(cats(), "t", "missing", signal))
    }

    @Test fun `rename trims and ignores blank`() {
        assertEquals("Chat", CategoryOps.rename(cats(), "t", " Chat ").first().name)
        assertEquals("Talk", CategoryOps.rename(cats(), "t", "").first().name)
    }

    @Test fun `delete renumbers orders`() {
        val result = CategoryOps.delete(cats(), "t")
        assertEquals(listOf(Category("l", "Listen", 0, emptyList())), result)
    }

    @Test fun `reorder moves and clamps`() {
        val three = CategoryOps.create(cats(), "s", "See")
        assertEquals(listOf("See", "Talk", "Listen"), CategoryOps.reorder(three, "s", 0).map { it.name })
        assertEquals(listOf("Listen", "See", "Talk"), CategoryOps.reorder(three, "t", 99).map { it.name })
        assertEquals(listOf(0, 1, 2), CategoryOps.reorder(three, "t", 99).map { it.order })
    }

    @Test fun `prune removes uninstalled apps everywhere`() {
        val both = CategoryOps.addApp(CategoryOps.addApp(cats(), "l", signal), "l", spotify)
        val result = CategoryOps.pruneTo(both, setOf(spotify))
        assertTrue(result.none { signal in it.apps })
        assertEquals(listOf(spotify), result.first { it.id == "l" }.apps)
    }

    @Test fun `normalize sorts by order and renumbers`() {
        val messy = listOf(Category("b", "B", 7, emptyList()), Category("a", "A", 3, emptyList()))
        assertEquals(listOf("a" to 0, "b" to 1), CategoryOps.normalize(messy).map { it.id to it.order })
    }

    @Test fun `resolve drops missing apps and keeps order`() {
        val withBoth = CategoryOps.addApp(cats(), "t", spotify)
        val installed = mapOf(spotify to AppEntry(spotify, "Spotify"))
        val resolved = CategoryOps.resolve(withBoth, installed)
        assertEquals(listOf("Spotify"), resolved.first().second.map { it.label })
        assertEquals(listOf(signal, spotify), resolved.first().first.apps)
    }
}
