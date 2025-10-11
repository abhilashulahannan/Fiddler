package com.example.fiddler.subapps.Fidland.music

/**
 * Repository for managing MusicApp instances.
 * Stores, updates, and retrieves all music apps in the system.
 */
object MusicAppsRepository {

    // Internal backing list of apps
    private val musicApps = mutableListOf(
        MusicApp("Spotify", MusicAppController.SPOTIFY_PACKAGE),
        MusicApp("YouTube Music", MusicAppController.YTMUSIC_PACKAGE)
    )

    /**
     * Get a snapshot of all registered apps.
     * Returns a copy to prevent external modification.
     */
    fun getAllApps(): List<MusicApp> = musicApps.toList()

    /**
     * Find a music app by its package name.
     */
    fun getAppByPackage(packageName: String): MusicApp? =
        musicApps.find { it.appPackage == packageName }

    /**
     * Update an existing app in the repository.
     * If the app does not exist, nothing happens.
     */
    fun updateApp(updatedApp: MusicApp) {
        val index = musicApps.indexOfFirst { it.appPackage == updatedApp.appPackage }
        if (index != -1) musicApps[index] = updatedApp
    }

    /**
     * Add a new app to the repository.
     * Ignores duplicates (by package name).
     */
    fun addApp(app: MusicApp) {
        if (musicApps.none { it.appPackage == app.appPackage }) {
            musicApps.add(app)
        }
    }

    /**
     * Remove an app from the repository by package name.
     */
    fun removeApp(packageName: String) {
        musicApps.removeAll { it.appPackage == packageName }
    }

    /**
     * Replace all apps (e.g., for a full refresh)
     */
    fun replaceAll(newApps: List<MusicApp>) {
        musicApps.clear()
        musicApps.addAll(newApps)
    }
}
