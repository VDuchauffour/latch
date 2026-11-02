package app.latch

import android.app.Application
import app.latch.data.AppLauncher
import app.latch.data.AppRepository
import app.latch.data.CategoryStore
import app.latch.data.IconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Creates the app-wide singletons (manual DI). */
class LatchApp : Application() {
    val appScope: CoroutineScope = MainScope()

    lateinit var apps: AppRepository
        private set
    lateinit var categories: CategoryStore
        private set
    lateinit var icons: IconLoader
        private set
    lateinit var launcher: AppLauncher
        private set

    override fun onCreate() {
        super.onCreate()
        apps = AppRepository(this, appScope)
        categories = CategoryStore(this)
        icons = IconLoader(this, apps)
        launcher = AppLauncher(this, apps)
        apps.iconInvalidations.onEach(icons::invalidate).launchIn(appScope)
        apps.start()
    }
}
