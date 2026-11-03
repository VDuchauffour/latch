package app.latch.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import app.latch.domain.AppEntry
import app.latch.domain.AppKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A package (in one user profile) whose launchable activities may have changed. */
data class PackageChange(val packageName: String, val userSerial: Long)

/**
 * Lists launchable activities for every user profile and keeps the list fresh with
 * [LauncherApps.Callback]. [apps] is `null` until the first load has finished.
 */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    /** Emitted after a reload triggered by a package event, so callers can prune stale keys. */
    private val _packageChanges = MutableSharedFlow<PackageChange>(extraBufferCapacity = 64)
    val packageChanges: SharedFlow<PackageChange> = _packageChanges.asSharedFlow()

    /** Emitted when an app's icon may have changed (update, re-enable…). */
    private val _iconInvalidations = MutableSharedFlow<PackageChange>(extraBufferCapacity = 64)
    val iconInvalidations: SharedFlow<PackageChange> = _iconInvalidations.asSharedFlow()

    @Volatile private var infos: Map<AppKey, LauncherActivityInfo> = emptyMap()
    private val reloadMutex = Mutex()
    private var pendingReload: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(listOf(packageName), user)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(listOf(packageName), user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(listOf(packageName), user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            changed(packageNames.toList(), user)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            changed(packageNames.toList(), user, prune = false)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            changed(packageNames.toList(), user, prune = false)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            changed(packageNames.toList(), user, prune = false)
    }

    /** Work profile turned on/off or added/removed: reload everything, never prune. */
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch(Dispatchers.Default) { reload() }
        }
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(profileReceiver, filter)
        }
        scope.launch(Dispatchers.Default) { reload() }
    }

    private fun changed(packages: List<String>, user: UserHandle, prune: Boolean = true) {
        val serial = userManager.getSerialNumberForUser(user)
        scope.launch(Dispatchers.Default) {
            reload()
            packages.forEach { pkg ->
                val change = PackageChange(pkg, serial)
                _iconInvalidations.emit(change)
                if (prune) _packageChanges.emit(change)
            }
        }
    }

    suspend fun reload() = reloadMutex.withLock {
        val newInfos = HashMap<AppKey, LauncherActivityInfo>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            for (info in list) {
                val cn = info.componentName
                if (cn.packageName == context.packageName) continue // don't list ourselves
                newInfos[AppKey(cn.packageName, cn.className, serial)] = info
            }
        }
        infos = newInfos
        _apps.value = newInfos.map { (key, info) -> AppEntry(key, info.label?.toString().orEmpty().ifBlank { key.packageName }) }
    }

    fun info(key: AppKey): LauncherActivityInfo? = infos[key]

    fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    fun componentOf(key: AppKey) = ComponentName(key.packageName, key.className)

    /**
     * True only when we're sure [key] is gone (its profile is running and unlocked, and the activity
     * is no longer enabled). Used for the start-up prune, so apps of a paused work profile or on
     * unmounted storage are never dropped.
     */
    fun isDefinitelyGone(key: AppKey): Boolean {
        val user = userFor(key.userSerial) ?: return false // profile removed: keep, it may come back
        return runCatching {
            if (userManager.isQuietModeEnabled(user) || !userManager.isUserUnlocked(user)) return false
            !launcherApps.isActivityEnabled(componentOf(key), user)
        }.getOrDefault(false)
    }
}
