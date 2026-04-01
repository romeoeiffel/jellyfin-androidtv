package org.jellyfin.androidtv.ui.gaming

import org.jellyfin.sdk.model.api.BaseItemDto
import java.io.File

data class NativeGameSpec(
    val rom: String,
    val system: String,
    val romCandidates: List<String> = listOf(rom),
)

object NativeGameDetector {

    @JvmStatic
    fun fromItem(item: BaseItemDto?): NativeGameSpec? {
        if (item == null) return null
        return fromPath(item.path)
    }

    @JvmStatic
    fun fromPath(path: String?): NativeGameSpec? {
        if (path.isNullOrBlank()) return null

        val fileName = File(path).name
        val upper = fileName.uppercase()

        if (!upper.endsWith(".MP4")) return null

        val stem = fileName.substringBeforeLast('.')

        return when {
            upper.endsWith("_MEGADRIVE.MP4") ->
                NativeGameSpec(
                    rom = "$stem.md",
                    system = "MEGADRIVE",
                    romCandidates = listOf("$stem.md", "$stem.bin", "$stem.gen")
                )

            upper.endsWith("_SNES.MP4") ->
                NativeGameSpec(
                    rom = "$stem.sfc",
                    system = "SNES",
                    romCandidates = listOf("$stem.sfc", "$stem.smc")
                )

            upper.endsWith("_NES.MP4") ->
                NativeGameSpec(
                    rom = "$stem.nes",
                    system = "NES",
                    romCandidates = listOf("$stem.nes")
                )

            upper.endsWith("_MASTERSYSTEM.MP4") ->
                NativeGameSpec(
                    rom = "$stem.sms",
                    system = "MASTERSYSTEM",
                    romCandidates = listOf("$stem.sms")
                )

            upper.endsWith("_PLAYSTATION.MP4") ->
                NativeGameSpec(
                    rom = "$stem.chd",
                    system = "PS1",
                    romCandidates = listOf("$stem.chd", "$stem.bin", "$stem.img", "$stem.pbp")
                )

            upper.endsWith("_PSX.MP4") ->
                NativeGameSpec(
                    rom = "$stem.chd",
                    system = "PSX",
                    romCandidates = listOf("$stem.chd", "$stem.bin", "$stem.img", "$stem.pbp")
                )
            upper.endsWith("_SEGASATURN.MP4") ->
                NativeGameSpec(
                    rom = "$stem.chd",
                    system = "SEGASATURN",
                    romCandidates = listOf("$stem.chd", "$stem.cue")
                )

            upper.endsWith("_N64.MP4") ->
                NativeGameSpec(
                    rom = "$stem.z64",
                    system = "N64",
                    romCandidates = listOf("$stem.z64", "$stem.n64", "$stem.v64")
                )

            upper.endsWith("_NINTENDO64.MP4") ->
                NativeGameSpec(
                    rom = "$stem.z64",
                    system = "N64",
                    romCandidates = listOf("$stem.z64", "$stem.n64", "$stem.v64")
                )

            upper.endsWith("_DS.MP4") ->
                NativeGameSpec(
                    rom = "$stem.nds",
                    system = "DS",
                    romCandidates = listOf("$stem.nds")
                )

	    upper.endsWith("_DREAMCAST.MP4") ->
                NativeGameSpec(
                    rom = "$stem.chd",
                    system = "DREAMCAST",
                    romCandidates = listOf("$stem.chd")
                )

            upper.endsWith("_NINTENDODS.MP4") ->
                NativeGameSpec(
                    rom = "$stem.nds",
                    system = "NINTENDODS",
                    romCandidates = listOf("$stem.nds")
                )

            else -> null
        }
    }
}