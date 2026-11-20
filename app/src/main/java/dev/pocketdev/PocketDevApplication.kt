package dev.pocketdev

import android.app.Application

/**
 * Application entry point. Owns the small dependency container used by the UI.
 *
 * The container is intentionally simple; it will be replaced by a proper
 * dependency-injection setup once the runtime and AI layers exist.
 */
class PocketDevApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}