package org.jellyfin.androidtv.ui.gaming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.flycast.emulator.NativeGLActivity
import java.io.File

object EmbeddedFlycastLauncher {
    private const val TAG = "MadflixFlycast"

    fun launch(context: Context, romFile: File): Boolean {
        if (!romFile.exists()) {
            Log.e(TAG, "ROM file does not exist: ${romFile.absolutePath}")
            return false
        }

        return try {
            val rawPath = romFile.absolutePath

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClass(context, NativeGLActivity::class.java)
                data = Uri.parse(rawPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            Log.d(TAG, "Launching embedded Flycast")
            Log.d(TAG, "romFile=$rawPath")
            Log.d(TAG, "uri=${intent.data}")

            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to launch embedded Flycast", t)
            false
        }
    }
}
