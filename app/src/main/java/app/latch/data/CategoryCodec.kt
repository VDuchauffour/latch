package app.latch.data

import app.latch.domain.AppKey
import app.latch.domain.Category
import app.latch.domain.CategoryOps
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Converts categories to/from the persisted JSON document:
 * ```
 * { "version": 1, "categories": [ { "id", "name", "order", "apps": [ { "pkg", "cls", "user" } ] } ] }
 * ```
 * Pure Kotlin, so it is unit-tested on the JVM.
 */
object CategoryCodec {
    const val CURRENT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class Document(val version: Int = CURRENT_VERSION, val categories: List<CategoryDto> = emptyList())

    @Serializable
    private data class CategoryDto(val id: String, val name: String, val order: Int = 0, val apps: List<AppKeyDto> = emptyList())

    @Serializable
    private data class AppKeyDto(
        @SerialName("pkg") val pkg: String,
        @SerialName("cls") val cls: String,
        @SerialName("user") val user: Long = 0
    )

    fun encode(categories: List<Category>): String = json.encodeToString(
        Document.serializer(),
        Document(
            version = CURRENT_VERSION,
            categories = CategoryOps.normalize(categories).map { c ->
                CategoryDto(c.id, c.name, c.order, c.apps.map { AppKeyDto(it.packageName, it.className, it.userSerial) })
            }
        )
    )

    /** Returns null if [text] is not a readable document (callers should then keep the data untouched). */
    fun decode(text: String): List<Category>? = runCatching {
        val migrated = migrate(json.parseToJsonElement(text))
        val doc = json.decodeFromJsonElement(Document.serializer(), migrated)
        CategoryOps.normalize(
            doc.categories.map { c ->
                Category(
                    id = c.id,
                    name = c.name,
                    order = c.order,
                    apps = c.apps.map { AppKey(it.pkg, it.cls, it.user) }.distinct()
                )
            }
        )
    }.getOrNull()

    /**
     * Upgrades older documents to [CURRENT_VERSION].
     * - v0 (pre-release): a bare JSON array of categories, without the version envelope.
     * Unknown newer versions are read best-effort (unknown keys are ignored).
     */
    internal fun migrate(element: JsonElement): JsonElement {
        if (element is JsonArray) {
            return buildJsonObject {
                put("version", CURRENT_VERSION)
                put("categories", element)
            }
        }
        val obj = element as JsonObject
        val version = obj["version"]?.jsonPrimitive?.intOrNull ?: CURRENT_VERSION
        return when {
            version <= CURRENT_VERSION -> obj
            else -> obj // forward-compatible best effort
        }
    }
}
