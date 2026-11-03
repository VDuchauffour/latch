package app.latch.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import app.latch.domain.AppKey

/** Starts apps and their system "App info" screen through [LauncherApps] (works for work profiles). */
class AppLauncher(private val context: Context, private val repository: AppRepository) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun launch(key: AppKey, sourceBounds: Rect? = null, options: Bundle? = null) {
        val user = repository.userFor(key.userSerial) ?: return fail(key)
        runCatching {
            launcherApps.startMainActivity(repository.componentOf(key), user, sourceBounds, options)
        }.onFailure { fail(key, it) }
    }

    fun openAppInfo(key: AppKey, sourceBounds: Rect? = null) {
        val user = repository.userFor(key.userSerial) ?: return fail(key)
        runCatching {
            launcherApps.startAppDetailsActivity(repository.componentOf(key), user, sourceBounds, null)
        }.onFailure { fail(key, it) }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications() {
        runCatching {
            val service = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }.onFailure { Log.w("AppLauncher", "Cannot expand notifications", it) }
    }

    private fun fail(key: AppKey, t: Throwable? = null) {
        Log.w("AppLauncher", "Cannot start $key", t)
        Toast.makeText(context, "Can't open this app", Toast.LENGTH_SHORT).show()
    }
}
