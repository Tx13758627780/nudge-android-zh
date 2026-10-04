package com.astraedus.nudge.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Source-level copy gate on the "Display Over Other Apps" / "Overlay Permission" rows in
 * onboarding and Settings.
 *
 * ## The defect this pins
 * SYSTEM_ALERT_WINDOW draws the block screen, but it does a second, much less visible job: a
 * granted overlay permission helps `NudgeMonitorService` restart itself when
 * `ProtectionWatchdogWorker` finds it dead, because Android 12+ refuses to start a foreground
 * service from the background at all without it — see
 * `docs/architecture/service-lifecycle-and-watchdog.md`, "Real start paths for the foreground
 * service". Both onboarding and Settings described only the first job ("shows block screens") and
 * let the user skip the permission with no idea that doing so also makes it more likely the app
 * cannot heal itself overnight. That is the same shape of defect issue #23 already burned a Play
 * review on: a failure mode invisible to the user until blocking has already quietly stopped.
 *
 * Deliberately worded as "helps" / "more likely", never "guarantees" / "always" or "never": Android
 * 16 (API 36) narrowed the overlay exemption background restarts rely on, so even a granted
 * permission is not a hard guarantee on every OS version. Opening Nudge yourself is the one thing
 * that reliably restarts it regardless of Android version, which both screens' copy says.
 *
 * ## Why a source-grep test, not a Compose UI test
 * Both screens are large, hand-styled Composables with no test tags on this copy, and Settings'
 * description selects resources using the live permission state. Resolving those resources while
 * reading the source checks both the wiring and the translated copy without rendering the UI. This
 * follows the same shape as `ProtectionAlertCopyTest` (service copy) and `MonitorServiceContractTest`
 * (service shape): assert on the properties of the real source text.
 *
 * ## Counterfactual (run manually, not by CI)
 * Deleting the "helps Nudge restart its own protection" sentence from its string resource, or
 * deleting the `if (overlayEnabled) { ... } else { ... }` cost branches from `SettingsScreen.kt` in
 * favor of the old static "Required to show block screens" string, makes this test fail — confirmed
 * by hand, twice (once for the original wording, again after this copy was softened to drop the
 * "guarantees a restart" overclaim): both sentences were removed, the corresponding
 * `mentions ... restart cost` / `is sharper when ... not granted` test failed with the expected
 * assertion message on both screens, and the text was restored. A future editor who trims this copy
 * back to "just describes the overlay" will hit that same failure, which is the point: the cost
 * sentence is load-bearing, not decoration.
 */
class OverlayPermissionCopyTest {

    private fun sourceRoot(): File =
        listOf(File("src/main"), File("app/src/main"))
            .firstOrNull { it.isDirectory }
            ?: error("main source set not found from working dir ${File("").absolutePath}")

    private fun read(relativePath: String): String {
        val file = File(sourceRoot(), relativePath)
        assertTrue("$relativePath must exist", file.exists())
        return file.readText()
    }

    private val onboardingSource by lazy {
        read("java/com/astraedus/nudge/ui/screens/onboarding/OnboardingScreen.kt")
    }

    private val settingsSource by lazy {
        read("java/com/astraedus/nudge/ui/screens/settings/SettingsScreen.kt")
    }

    /** Read actual Android string resources using the JVM XML parser, without Android dependencies. */
    private fun resourceStrings(qualifier: String): Map<String, String> {
        val file = File(sourceRoot(), "res/$qualifier/strings_settings.xml")
        assertTrue("$file must exist", file.exists())
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file).getElementsByTagName("string")
        return (0 until strings.length).associate { index ->
            val node = strings.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private val englishStrings by lazy { resourceStrings("values") }
    private val chineseStrings by lazy { resourceStrings("values-zh-rCN") }

    /**
     * Isolate the row using its title resource, then resolve only the strings it actually uses.
     * Keeping the conditional in the snippet also verifies that the cost follows live state.
     */
    private fun overlaySnippet(source: String, titleResource: String): String {
        val titleReference = "R.string.$titleResource"
        val titleIndex = source.indexOf(titleReference)
        assertTrue("expected the overlay permission row to reference $titleReference", titleIndex >= 0)
        val searchFrom = titleIndex + titleReference.length
        val nextCallIndex = Regex("""PermissionCard\(|PermissionItem\(""")
            .find(source, searchFrom)?.range?.first
            ?: (searchFrom + 800).coerceAtMost(source.length)
        return Regex("""R\.string\.(\w+)""")
            .replace(source.substring(titleIndex, nextCallIndex)) { reference ->
                englishStrings[reference.groupValues[1]]
                    ?: error("missing string resource ${reference.groupValues[1]}")
            }
    }

    private val onboardingOverlaySnippet by lazy {
        overlaySnippet(onboardingSource, "onboarding_overlay_title")
    }

    private val settingsOverlaySnippet by lazy {
        overlaySnippet(settingsSource, "settings_overlay_permission")
    }

    @Test
    fun `onboarding overlay copy mentions both the block screen and the restart cost`() {
        val lower = onboardingOverlaySnippet.lowercase()

        assertTrue(
            "Onboarding's Display Over Other Apps card must still say it draws the block " +
                "screen/delay/breathing overlay — that is the permission's other job and dropping " +
                "it would leave the user with no idea what they are granting at all.",
            lower.contains("overlay") && lower.contains("block")
        )
        assertTrue(
            "Onboarding's Display Over Other Apps card must say skipping it can stop Nudge from " +
                "restarting its own protection in the background after the phone kills it (issue " +
                "#62) — without this sentence the user has no way to know that declining this " +
                "permission silently breaks the watchdog's ability to heal a dead service.",
            lower.contains("restart") && lower.contains("background")
        )
    }

    @Test
    fun `settings overlay copy is sharper when the permission is not granted`() {
        val lower = settingsOverlaySnippet.lowercase()

        assertTrue(
            "Settings' Overlay Permission row must mention the block screen somewhere across its " +
                "granted/not-granted branches.",
            lower.contains("block")
        )
        assertTrue(
            "Settings' Overlay Permission row must mention the background restart cost somewhere " +
                "across its granted/not-granted branches — deleting this is the exact regression " +
                "issue #62 exists to prevent, matching the sibling accessibilityCrashed row's " +
                "pattern of naming the real consequence of the current state.",
            lower.contains("restart") && lower.contains("background")
        )
        assertTrue(
            "Settings' Overlay Permission description must branch on the live overlayEnabled " +
                "state, the same pattern the sibling Accessibility row already uses for " +
                "accessibilityCrashed — a single static string cannot say 'off' only when it is " +
                "actually off.",
            settingsOverlaySnippet.contains("if (overlayEnabled)") ||
                settingsOverlaySnippet.contains("if(overlayEnabled)")
        )
    }

    @Test
    fun `Chinese overlay copy keeps the permission consequences and recovery instructions`() {
        val expectedTerms = mapOf(
            "onboarding_overlay_description" to listOf("等待倒计时", "呼吸练习", "后台停止", "恢复防护", "重新打开"),
            "settings_overlay_granted_description" to listOf("拦截界面", "后台停止", "恢复防护"),
            "settings_overlay_denied_description" to listOf("无法显示拦截界面", "更容易被系统停止", "重新打开应用可恢复防护")
        )
        expectedTerms.forEach { (key, terms) ->
            val text = chineseStrings[key] ?: error("missing Chinese string resource $key")
            terms.forEach { term ->
                assertTrue("$key must retain the consequence or recovery instruction: $term", text.contains(term))
            }
        }
    }

    @Test
    fun `overlay copy never leaks implementation jargon to the user`() {
        val jargon = listOf("foreground service", "system_alert_window", "exemption")

        listOf(
            "onboarding" to onboardingOverlaySnippet,
            "settings" to settingsOverlaySnippet
        ).forEach { (screen, snippet) ->
            val lower = snippet.lowercase()
            jargon.forEach { term ->
                assertFalse(
                    "$screen's overlay copy must not say \"$term\" — this row is user-facing " +
                        "copy, not a code comment, and Android/AOSP terms belong in the source " +
                        "comment next to it, not the string the user reads.",
                    lower.contains(term)
                )
            }
        }
    }
}
