package app.latch.domain

/** A user-named, ordered group of apps. An app may belong to several categories. */
data class Category(
    val id: String,
    val name: String,
    val order: Int,
    val apps: List<AppKey>,
)
