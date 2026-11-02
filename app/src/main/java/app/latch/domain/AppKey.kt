package app.latch.domain

/**
 * Unique identity of a launchable activity.
 *
 * A package can expose several launcher activities, and the same activity can exist once per user
 * profile (personal / work), so all three parts are needed. [userSerial] is the value of
 * `UserManager.getSerialNumberForUser`, which is stable across reboots.
 */
data class AppKey(
    val packageName: String,
    val className: String,
    val userSerial: Long,
)
