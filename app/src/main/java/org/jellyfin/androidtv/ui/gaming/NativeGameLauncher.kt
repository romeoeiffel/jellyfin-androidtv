package org.jellyfin.androidtv.ui.gaming

import android.content.Context
import android.content.Intent

object NativeGameLauncher {

    @JvmStatic
    @JvmOverloads
    fun buildIntent(
        context: Context,
        rom: String,
        system: String,
        gameName: String,
        coreFileOverride: String? = null,
        romCandidatesOverride: Array<String>? = null
    ): Intent {
        return Intent(context, NativeGamingActivity::class.java).apply {
            putExtra(NativeGamingActivity.EXTRA_ROM, rom)
            putExtra(NativeGamingActivity.EXTRA_SYSTEM, system)
            putExtra(NativeGamingActivity.EXTRA_GAME_NAME, gameName)

            if (!coreFileOverride.isNullOrBlank()) {
                putExtra(NativeGamingActivity.EXTRA_CORE_FILE, coreFileOverride)
            }

            if (!romCandidatesOverride.isNullOrEmpty()) {
                putExtra(NativeGamingActivity.EXTRA_ROM_CANDIDATES, romCandidatesOverride)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun launch(
        context: Context,
        rom: String,
        system: String,
        gameName: String,
        coreFileOverride: String? = null,
        romCandidatesOverride: Array<String>? = null
    ) {
        context.startActivity(
            buildIntent(
                context = context,
                rom = rom,
                system = system,
                gameName = gameName,
                coreFileOverride = coreFileOverride,
                romCandidatesOverride = romCandidatesOverride
            )
        )
    }
}