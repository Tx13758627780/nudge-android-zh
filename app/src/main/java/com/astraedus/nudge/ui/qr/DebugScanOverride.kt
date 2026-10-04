package com.astraedus.nudge.ui.qr

import com.astraedus.nudge.BuildConfig

/**
 * DEBUG BUILDS ONLY: lets bench QA feed [QrScanActivity] a scan result over adb, because the bench
 * Pixel's camera faces a desk and every "Nuke is on" case needs a paired key first.
 *
 * ```
 * adb shell setprop debug.nudge.fakescan 'nudge-nuke:anything'   # next scan returns this
 * adb shell setprop debug.nudge.fakescan ''                      # back to the real camera
 * ```
 *
 * `debug.*` properties are settable by the adb shell user without root. In a release build
 * [BuildConfig.DEBUG] is a compile-time false, so [read] returns null without looking and R8 drops
 * the rest: a release APK cannot be steered this way. The camera decode itself is covered by the
 * JVM frame tests (`BarcodeFrameDecoderTest`); this only replaces the camera, never the result path.
 */
internal object DebugScanOverride {

    const val PROPERTY = "debug.nudge.fakescan"

    fun read(): String? {
        if (!BuildConfig.DEBUG) return null
        return try {
            val process = ProcessBuilder("getprop", PROPERTY).redirectErrorStream(true).start()
            val value = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            normalize(value)
        } catch (e: Exception) {
            null
        }
    }

    /** `getprop` prints an empty line for an unset property; blank means "no override". */
    fun normalize(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
}
