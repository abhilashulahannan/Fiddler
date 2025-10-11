package com.example.fiddler.subapps.Fidland.music

object MusicAppsRepository {

    private val musicApps = mutableListOf(
        MusicApp("Spotify", MusicAppController.SPOTIFY_PACKAGE),
        MusicApp("YouTube Music", MusicAppController.YTMUSIC_PACKAGE)
    )

    fun getAllApps(): List<MusicApp> = musicApps.toList()

    fun getAppByPackage(packageName: String): MusicApp? =
        musicApps.find { it.appPackage == packageName }

    fun updateApp(updatedApp: MusicApp) {
        val index = musicApps.indexOfFirst { it.appPackage == updatedApp.appPackage }
        if (index != -1) musicApps[index] = updatedApp
    }

    fun addApp(app: MusicApp) {
        if (musicApps.none { it.appPackage == app.appPackage }) {
            musicApps.add(app)
        }
    }
}
