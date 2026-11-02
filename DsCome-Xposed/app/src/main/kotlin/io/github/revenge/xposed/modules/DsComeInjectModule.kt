package io.github.revenge.xposed.modules

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.revenge.xposed.Module
import io.github.revenge.xposed.Utils.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * DsComeInjectModule
 *
 * On startup, copies DsCome's injection scripts from the module APK's assets
 * into Discord's preloads directory (files/pyoncord/preloads/).
 * They are automatically picked up and executed by HookScriptLoaderModule.
 *
 * Scripts injected:
 *  - 00_fake_deafen.js   : Fake Deafen — appears deaf to server, audio stays active
 *  - 01_token_login.js   : Token Login + Account Switcher in settings/logout screen
 *  - 02_nuker.js         : Full server nuker (channels, roles, ban, kick, spam…)
 *  - 03_ucore_loader.js  : ツcore audio engine loader
 *
 * Also copies ucore/injector.js to files/pyoncord/dscome_assets/ucore_injector.js
 * for 03_ucore_loader.js to load at runtime.
 */
object DsComeInjectModule : Module() {

    private const val TAG = "DsComeInject"

    private val PRELOAD_SCRIPTS = listOf(
        "dscome_preloads/00_fake_deafen.js",
        "dscome_preloads/01_token_login.js",
        "dscome_preloads/02_nuker.js",
        "dscome_preloads/03_ucore_loader.js"
    )

    private lateinit var modulePath: String

    override fun onInit(startupParam: de.robv.android.xposed.IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun onContext(context: Context) {
        try {
            val dataDir = context.dataDir.absolutePath

            // Discord's preloads dir: /data/data/com.discord/files/pyoncord/preloads/
            val preloadsDir = File("$dataDir/files/pyoncord/preloads").also { it.mkdirs() }

            // DsCome assets dir for large files (ucore)
            val assetsDir = File("$dataDir/files/pyoncord/dscome_assets").also { it.mkdirs() }

            ZipFile(modulePath).use { zip ->
                // Copy preload scripts
                for (scriptPath in PRELOAD_SCRIPTS) {
                    val entryName = "assets/$scriptPath"
                    val destName = scriptPath.substringAfterLast('/')
                    copyAssetFromZip(zip, entryName, File(preloadsDir, destName))
                }

                // Copy ucore injector (847KB)
                copyAssetFromZip(
                    zip,
                    "assets/dscome_ucore/ucore_injector.js",
                    File(assetsDir, "ucore_injector.js")
                )
            }

            Log.i("$TAG: scripts installed to $preloadsDir")
        } catch (e: Exception) {
            Log.e("$TAG: failed to install scripts", e)
        }
    }

    override fun onLoad(packageParam: XC_LoadPackage.LoadPackageParam) {
        // nothing — preloads auto-run via HookScriptLoaderModule
    }

    private fun copyAssetFromZip(zip: ZipFile, entryName: String, dest: File) {
        val entry = zip.getEntry(entryName) ?: run {
            Log.w("$TAG: asset not found: $entryName")
            return
        }
        zip.getInputStream(entry).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
    }
}
