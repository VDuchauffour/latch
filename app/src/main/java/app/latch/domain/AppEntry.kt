package app.latch.domain

/** A launchable app as shown to the user. */
data class AppEntry(
    val key: AppKey,
    val label: String,
)
