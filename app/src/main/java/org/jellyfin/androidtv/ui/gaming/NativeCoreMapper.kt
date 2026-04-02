package org.jellyfin.androidtv.ui.gaming

object NativeCoreMapper {

    fun coreForSystem(system: String?): String {
        return when ((system ?: "").trim().uppercase()) {
            "MEGADRIVE", "GENESIS", "SEGA_MD", "SEGAMD" ->
                "picodrive_libretro_android.so"

            "SNES", "SUPERNINTENDO", "SUPER_NINTENDO" ->
                "snes9x_libretro_android.so"

            "NES", "NINTENDO", "FAMICOM" ->
                "nestopia_libretro_android.so"

            "MASTERSYSTEM", "MASTER_SYSTEM", "SMS" ->
                "smsplus_libretro_android.so"

            "PLAYSTATION", "PLAYSTATION1", "PS1", "PSX" ->
                "pcsx_rearmed_libretro_android.so"

			"SATURN", "SEGA_SATURN", "SEGASATURN" ->
                "yabasanshiro_libretro_android.so"

            "NINTENDO64", "NINTENDO_64", "N64" ->
                "mupen64plus_next_gles3_libretro_android.so"

	    	"NINTENDODS", "NINTENDO_DS", "DS" ->
                "melonds_libretro_android.so"

            else ->
                "picodrive_libretro_android.so"
        }
    }
}
