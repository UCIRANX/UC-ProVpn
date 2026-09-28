package com.ucprovpn.app

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the real AndroidManifest.xml and guards MainActivity's intent filters. There
 * is a single fixed launcher icon now (no more per-user alias switching), so
 * MAIN/LAUNCHER lives directly on MainActivity alongside every import/deep-link
 * filter it already carried.
 *
 * The manifest is located relative to the module directory; the assertions are
 * skipped rather than failed if the test runner is rooted somewhere unexpected.
 */
class ManifestLauncherAliasTest {

    private val manifest: File? = sequenceOf(
        File("src/main/AndroidManifest.xml"),
        File("app/src/main/AndroidManifest.xml"),
        File("android/app/src/main/AndroidManifest.xml")
    ).firstOrNull { it.isFile }

    private fun elements(tag: String): List<Element> {
        val file = requireNotNull(manifest)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** Every android:name under this element's <action> tags, across all its filters. */
    private fun Element.actionNames(): List<String> = named("action")

    /** Every android:name under this element's <category> tags. */
    private fun Element.categoryNames(): List<String> = named("category")

    private fun Element.named(tag: String): List<String> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
    }

    private fun mainActivity(): Element =
        elements("activity").single { it.getAttribute("android:name") == MAIN_ACTIVITY }

    @Test
    fun mainActivityIsTheOnlyLauncherEntryPoint() {
        assumeTrue("manifest not found from ${File(".").absolutePath}", manifest != null)

        assertTrue(
            "MAIN/LAUNCHER must live on MainActivity now that there is one fixed icon",
            CATEGORY_LAUNCHER in mainActivity().categoryNames()
        )
        assertTrue(
            "there is a single fixed icon now, so no activity-alias should remain",
            elements("activity-alias").isEmpty()
        )
    }

    /** The filters that make imports work must all still be on MainActivity. */
    @Test
    fun mainActivityKeepsEveryOtherIntentFilter() {
        assumeTrue(manifest != null)
        val main = mainActivity()
        val actions = main.actionNames().toSet()
        assertTrue("ACTION_VIEW must stay on MainActivity", ACTION_VIEW in actions)
        assertTrue("ACTION_SEND must stay on MainActivity", ACTION_SEND in actions)

        val nodes = main.getElementsByTagName("data")
        val schemes = (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttribute("android:scheme") }
        listOf("lumen", "vless", "wireguard", "content", "file").forEach { scheme ->
            assertTrue("scheme $scheme must stay on MainActivity", scheme in schemes)
        }
    }

    private companion object {
        const val MAIN_ACTIVITY = "com.ucprovpn.app.MainActivity"

        // android.content.Intent is not usable from a plain JVM unit test, and the
        // manifest stores these strings verbatim anyway.
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
    }
}
