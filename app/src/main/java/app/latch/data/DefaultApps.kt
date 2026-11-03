package app.latch.data

import android.content.Context
import android.telecom.TelecomManager
import app.latch.domain.AppEntry

fun sampleApp(context: Context, apps: List<AppEntry>): AppEntry? {
    val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
    return apps.firstOrNull { it.key.packageName == dialer } ?: apps.firstOrNull()
}
