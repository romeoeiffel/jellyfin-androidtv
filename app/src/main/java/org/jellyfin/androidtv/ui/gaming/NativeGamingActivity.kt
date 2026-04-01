package org.jellyfin.androidtv.ui.gaming

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import java.io.File
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import com.swordfish.libretrodroid.Variable

class NativeGamingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MadflixNative"

        const val EXTRA_ROM = "rom"
        const val EXTRA_CORE_FILE = "core_file"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_SYSTEM = "system"

	const val EXTRA_ROM_CANDIDATES = "rom_candidates"

	const val EXTRA_CORE_PROFILE = "core_profile"

        private const val DEFAULT_ROM = "Sonic_The_Hedgehog_1991_MEGADRIVE.md"
        private const val DEFAULT_CORE_FILE = "libgenesis_plus_gx_libretro_android.so"
        private const val DEFAULT_GAME_NAME = "Sonic The Hedgehog"

        private const val BASE_ROM_URL = "https://madflix.coinfactory.fr/gaming/roms/"
    }

    private lateinit var statusView: TextView
    private lateinit var container: FrameLayout
    private var retroView: GLRetroView? = null

    private fun isDreamcastSystem(system: String?): Boolean {
      return (system ?: "").trim().uppercase() in setOf(
        "DREAMCAST",
        "SEGA_DREAMCAST",
        "SEGADREAMCAST"
      )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_native_gaming)

        statusView = findViewById(R.id.native_status)
        container = findViewById(R.id.native_game_container)

        val romName = intent.getStringExtra(EXTRA_ROM)?.trim().takeUnless { it.isNullOrEmpty() }
    	?: DEFAULT_ROM

		val romCandidates = intent.getStringArrayExtra(EXTRA_ROM_CANDIDATES)
			?.map { it.trim() }
			?.filter { it.isNotEmpty() }
			?.distinct()
			?.takeIf { it.isNotEmpty() }

		val effectiveRomCandidates = buildList {
			romCandidates?.let { addAll(it) }
			if (romName !in this) add(romName)
		}.ifEmpty { listOf(DEFAULT_ROM) }
		
		val system = intent.getStringExtra(EXTRA_SYSTEM)?.trim()

		val gameName = intent.getStringExtra(EXTRA_GAME_NAME)?.trim().takeUnless { it.isNullOrEmpty() }
    		?: DEFAULT_GAME_NAME

		Log.d(TAG, "system=$system")

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
    			override fun handleOnBackPressed() {
        		finish()
    		}
	})

	lifecycleScope.launch {
    	try {
        	statusView.text = "Téléchargement de ${gameName}..."
        	val romFile = withContext(Dispatchers.IO) { downloadRomToCache(effectiveRomCandidates) }

        	if (isDreamcastSystem(system)) {
            	statusView.text = "Lancement de Flycast..."
            	Log.d(TAG, "Dreamcast detected -> embedded Flycast")
            	Log.d(TAG, "romFile=${romFile.absolutePath}")

            	val launched = EmbeddedFlycastLauncher.launch(this@NativeGamingActivity, romFile)
            	if (!launched) {
                	throw IllegalStateException("Impossible de lancer Flycast embarqué")
           	 }

            	finish()
            	return@launch
        }

        val coreFile = intent.getStringExtra(EXTRA_CORE_FILE)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: NativeCoreMapper.coreForSystem(system)

        val coreProfile = intent.getStringExtra(EXTRA_CORE_PROFILE)?.trim()
        val coreVariables = buildCoreVariables(coreFile, coreProfile)

        Log.d(TAG, "coreProfile=$coreProfile")
        Log.d(TAG, "coreVariables=${coreVariables.joinToString { "${it.key}=${it.value}" }}")
        Log.d(TAG, "selectedCore=$coreFile")

        statusView.text = "Initialisation du core..."
        startRetro(
            romFile = romFile,
            coreFile = coreFile,
            gameName = gameName,
            system = system,
            coreVariables = coreVariables
        )

        statusView.text = ""
   	} catch (t: Throwable) {
        		Log.e(TAG, "Fatal error in NativeGamingActivity", t)
        		statusView.text = "Erreur: ${t.javaClass.simpleName}: ${t.message}"
    		}
	}

    }

private fun buildCoreVariables(coreFile: String, coreProfile: String?): Array<Variable> {
    val profile = (coreProfile ?: "").trim().lowercase()
    Log.d(TAG, "BUILD_CORE_VARS coreFile=$coreFile profile=$profile")

    return when (coreFile) {
        "mednafen_saturn_libretro_android.so" -> when (profile) {
            "mednafen_perf" -> arrayOf(
                Variable("beetle_saturn_cdimagecache", "enabled"),
                Variable("beetle_saturn_midsync", "disabled"),
                Variable("beetle_saturn_horizontal_blend", "disabled")
            )

            "mednafen_latency" -> arrayOf(
                Variable("beetle_saturn_cdimagecache", "enabled"),
                Variable("beetle_saturn_midsync", "enabled"),
                Variable("beetle_saturn_horizontal_blend", "disabled")
            )

            else -> arrayOf()
        }

        else -> arrayOf()
    }
}

private fun installAssetIfMissing(assetPath: String, target: File) {
    if (target.exists() && target.length() > 0L) return

    assets.open(assetPath).use { input ->
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    Log.d(TAG, "BIOS installed: ${target.absolutePath}")
}

private fun maybeInstallSaturnBios(system: String?, systemDir: File) {
    val normalized = (system ?: "").trim().uppercase()
    if (normalized !in setOf("SATURN", "SEGA_SATURN", "SEGASATURN")) return

    try {
        installAssetIfMissing("bios/mpr-17933.bin", File(systemDir, "mpr-17933.bin"))
        installAssetIfMissing("bios/mpr-17933.bin", File(systemDir, "saturn_bios.bin"))
    } catch (t: Throwable) {
        Log.e(TAG, "Unable to install Saturn BIOS", t)
    }
}

private fun maybeInstallDreamcastBios(system: String?, systemDir: File) {
    val normalized = (system ?: "").trim().uppercase()
    if (normalized !in setOf("DREAMCAST", "SEGA_DREAMCAST")) return

    val dcDir = File(systemDir, "dc").apply { mkdirs() }

    try {
        installAssetIfMissing("bios/dc_boot.bin", File(dcDir, "dc_boot.bin"))
        installAssetIfMissing("bios/dc_flash.bin", File(dcDir, "dc_flash.bin"))
    } catch (t: Throwable) {
        Log.e(TAG, "Unable to install Dreamcast BIOS", t)
    }
}

private suspend fun downloadRomToCache(romCandidates: List<String>): File = withContext(Dispatchers.IO) {
    val romDir = File(cacheDir, "madflix-roms").apply { mkdirs() }
    var lastError: Throwable? = null

    for (romName in romCandidates.distinct()) {
        val romFile = File(romDir, romName)

        if (romFile.exists() && romFile.length() > 0L) {
            return@withContext romFile
        }

        val romUrl = BASE_ROM_URL + Uri.encode(romName)

        try {
            val connection = (URL(romUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 5000
                readTimeout = 15000
                requestMethod = "GET"
            }

            connection.connect()

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    romFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                connection.disconnect()
                return@withContext romFile
            } else {
                connection.disconnect()
            }
        } catch (t: Throwable) {
            lastError = t
            if (romFile.exists() && romFile.length() == 0L) {
                romFile.delete()
            }
        }
    }

    throw IllegalStateException(
        "ROM introuvable. Candidats testés: ${romCandidates.joinToString()}",
        lastError
    )
}

     private fun startRetro(
        romFile: File,
        coreFile: String,
        gameName: String,
        system: String?,
        coreVariables: Array<Variable>
     ) {
        val savesDir = File(filesDir, "madflix-saves").apply { mkdirs() }
        val systemDir = File(filesDir, "madflix-system").apply { mkdirs() }

	maybeInstallSaturnBios(system, systemDir)
	maybeInstallDreamcastBios(system, systemDir)

        val coreAbsolutePath = File(applicationInfo.nativeLibraryDir, coreFile).absolutePath
        val coreExists = File(coreAbsolutePath).exists()

        Log.d(TAG, "gameName=$gameName")
        Log.d(TAG, "romFile=${romFile.absolutePath}")
        Log.d(TAG, "coreAbsolutePath=$coreAbsolutePath")
        Log.d(TAG, "coreExists=$coreExists")

        if (!coreExists) {
            throw IllegalStateException("Core introuvable: $coreFile")
        }

	Log.d(TAG, "APPLY_CORE_VARS count=${coreVariables.size}")

        val data = GLRetroViewData(this).apply {
            coreFilePath = coreAbsolutePath
            gameFilePath = romFile.absolutePath
            gameFileBytes = null
            systemDirectory = systemDir.absolutePath
            savesDirectory = savesDir.absolutePath
            variables = coreVariables
            saveRAMState = null
            shader = ShaderConfig.Default
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true
        }

        val view = GLRetroView(this, data)
        retroView = view
        lifecycle.addObserver(view)

        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        view.requestFocus()
    }

    private fun isGamepadKeyEvent(event: KeyEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun isGamepadMotionEvent(event: MotionEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val view = retroView

        if (view != null) {
            // Si c'est une vraie touche de manette, on la donne au core
            // ET on empêche Android TV de la traiter comme "Back/Home".
            if (isGamepadKeyEvent(event)) {
                view.sendKeyEvent(event.action, keyCode)
                return true
            }

            // Pour la télécommande / clavier :
            // on laisse uniquement KEYCODE_BACK quitter le jeu.
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return super.onKeyDown(keyCode, event)
            }

            // Toutes les autres touches utiles sont consommées par l'émulateur.
            view.sendKeyEvent(event.action, keyCode)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val view = retroView

        if (view != null) {
            if (isGamepadKeyEvent(event)) {
                view.sendKeyEvent(event.action, keyCode)
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return super.onKeyUp(keyCode, event)
            }

            view.sendKeyEvent(event.action, keyCode)
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        val view = retroView ?: return super.onGenericMotionEvent(event)
        val e = event ?: return super.onGenericMotionEvent(event)

        if (!isGamepadMotionEvent(e)) {
            return super.onGenericMotionEvent(event)
        }

        view.sendMotionEvent(
            GLRetroView.MOTION_SOURCE_DPAD,
            e.getAxisValue(MotionEvent.AXIS_HAT_X),
            e.getAxisValue(MotionEvent.AXIS_HAT_Y),
            0
        )

        view.sendMotionEvent(
            GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
            e.getAxisValue(MotionEvent.AXIS_X),
            e.getAxisValue(MotionEvent.AXIS_Y),
            0
        )

        view.sendMotionEvent(
            GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
            e.getAxisValue(MotionEvent.AXIS_Z),
            e.getAxisValue(MotionEvent.AXIS_RZ),
            0
        )

        return true
    }

    override fun onDestroy() {
        retroView = null
        super.onDestroy()
    }
}
