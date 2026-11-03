package app.latch.data

import app.latch.domain.AppKey
import app.latch.domain.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCodecTest {
    private val sample = listOf(
        Category("1", "Talk", 0, listOf(AppKey("com.example.chat", "com.example.chat.MainActivity", 0))),
        Category("2", "Listen", 1, listOf(AppKey("com.example.music", "com.example.music.MainActivity", 10))),
        Category("3", "Émojis 🎉 \"quoted\"", 2, emptyList()),
    )

    @Test fun `round trip preserves everything`() {
        assertEquals(sample, CategoryCodec.decode(CategoryCodec.encode(sample)))
    }

    @Test fun `encoded document uses the documented keys`() {
        val text = CategoryCodec.encode(sample)
        assertTrue(text.contains("\"version\":1"))
        assertTrue(text.contains("\"pkg\":\"com.example.music\""))
        assertTrue(text.contains("\"cls\":"))
        assertTrue(text.contains("\"user\":10"))
    }

    @Test fun `decodes the plan example`() {
        val text = """
            { "version": 1, "categories": [
              { "id": "uuid", "name": "Talk", "order": 0,
                "apps": [ { "pkg": "com.example.chat", "cls": "MainActivity", "user": 0 } ] } ] }
        """.trimIndent()
        val decoded = CategoryCodec.decode(text)!!
        assertEquals(listOf(AppKey("com.example.chat", "MainActivity", 0)), decoded.single().apps)
    }

    @Test fun `migrates v0 bare array`() {
        val v0 = """[ { "id": "a", "name": "Old", "order": 0, "apps": [] } ]"""
        assertEquals(listOf(Category("a", "Old", 0, emptyList())), CategoryCodec.decode(v0))
    }

    @Test fun `unknown keys and newer versions are read best effort`() {
        val v2 = """{ "version": 2, "extra": true, "categories": [ { "id": "a", "name": "X", "color": "red" } ] }"""
        assertEquals(listOf(Category("a", "X", 0, emptyList())), CategoryCodec.decode(v2))
    }

    @Test fun `duplicate apps are collapsed and orders normalized`() {
        val text = """{ "version": 1, "categories": [
            { "id": "b", "name": "B", "order": 5, "apps": [ {"pkg":"p","cls":"c"}, {"pkg":"p","cls":"c"} ] },
            { "id": "a", "name": "A", "order": 2 } ] }"""
        val decoded = CategoryCodec.decode(text)!!
        assertEquals(listOf("a" to 0, "b" to 1), decoded.map { it.id to it.order })
        assertEquals(1, decoded[1].apps.size)
    }

    @Test fun `corrupt input returns null`() {
        assertNull(CategoryCodec.decode("{ not json"))
        assertNull(CategoryCodec.decode("\"just a string\""))
    }
}
