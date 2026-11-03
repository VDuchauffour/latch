package app.latch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryOpsTest {
    private val chat = AppKey("com.example.chat", "MainActivity", 0)
    private val music = AppKey("com.example.music", "MainActivity", 0)
    private val workChat = chat.copy(userSerial = 10)

    private fun cats() = listOf(
        Category("t", "Talk", 0, listOf(chat)),
        Category("l", "Listen", 1, emptyList()),
    )

    @Test fun `create appends trimmed name and ignores blank`() {
        val created = CategoryOps.create(cats(), "s", "  See ")
        assertEquals(listOf("Talk", "Listen", "See"), created.map { it.name })
        assertEquals(2, created.last().order)
        assertEquals(cats(), CategoryOps.create(cats(), "x", "   "))
        assertEquals(cats(), CategoryOps.create(cats(), "t", "Duplicate id"))
    }

    @Test fun `add is idempotent`() {
        val once = CategoryOps.addApp(cats(), "t", chat)
        assertEquals(listOf(chat), once.first { it.id == "t" }.apps)
        val other = CategoryOps.addApp(once, "t", music)
        assertEquals(listOf(chat, music), other.first { it.id == "t" }.apps)
    }

    @Test fun `same app may be in several categories`() {
        val result = CategoryOps.addApp(cats(), "l", chat)
        assertTrue(result.all { chat in it.apps })
    }

    @Test fun `work profile copy is a distinct app`() {
        val result = CategoryOps.addApp(cats(), "t", workChat)
        assertEquals(listOf(chat, workChat), result.first { it.id == "t" }.apps)
    }

    @Test fun `remove only affects the given category`() {
        val both = CategoryOps.addApp(cats(), "l", chat)
        val result = CategoryOps.removeApp(both, "t", chat)
        assertEquals(emptyList<AppKey>(), result.first { it.id == "t" }.apps)
        assertEquals(listOf(chat), result.first { it.id == "l" }.apps)
    }

    @Test fun `move removes from source and adds to target`() {
        val result = CategoryOps.moveApp(cats(), "t", "l", chat)
        assertEquals(emptyList<AppKey>(), result.first { it.id == "t" }.apps)
        assertEquals(listOf(chat), result.first { it.id == "l" }.apps)
        assertEquals(cats(), CategoryOps.moveApp(cats(), "t", "t", chat))
        assertEquals(cats(), CategoryOps.moveApp(cats(), "t", "missing", chat))
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
        val both = CategoryOps.addApp(CategoryOps.addApp(cats(), "l", chat), "l", music)
        val result = CategoryOps.pruneTo(both, setOf(music))
        assertTrue(result.none { chat in it.apps })
        assertEquals(listOf(music), result.first { it.id == "l" }.apps)
    }

    @Test fun `normalize sorts by order and renumbers`() {
        val messy = listOf(Category("b", "B", 7, emptyList()), Category("a", "A", 3, emptyList()))
        assertEquals(listOf("a" to 0, "b" to 1), CategoryOps.normalize(messy).map { it.id to it.order })
    }

    @Test fun `resolve drops missing apps and keeps order`() {
        val withBoth = CategoryOps.addApp(cats(), "t", music)
        val installed = mapOf(music to AppEntry(music, "Music"))
        val resolved = CategoryOps.resolve(withBoth, installed)
        assertEquals(listOf("Music"), resolved.first().second.map { it.label })
        assertEquals(listOf(chat, music), resolved.first().first.apps)
    }
}
