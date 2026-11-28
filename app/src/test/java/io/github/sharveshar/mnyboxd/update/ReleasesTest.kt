// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private val dl = "https://github.com/Sharvesh-AR/MNYBOXD/releases/download/v2.9.0"

    /** The 2.8.0 release notes, as published. */
    private val notes = """
        **Four tidy widgets, proper previews in the widget list, and a way to keep your data when you uninstall.** This updates MNYBOXD in place, so all your accounts, transactions, budgets and settings stay.

        > All automated tests and lint pass. This version hasn't been installed on a phone yet, so please report anything that looks or behaves wrong.

        ## What's new

        **Four widgets:** *Quick Add*, *Add income*, *Monthly overview* and *Spending trend*. The *Expense + Income*, *Account balance* and *Quick stats* widgets are gone.

        **Each widget appears once in the widget list, with a picture of itself.** Before, every widget showed up twice: once with an empty grey preview and once with just the app icon.

        **Keep your data when you uninstall.** MNYBOXD has no account, so uninstalling normally deletes everything. On Android 10 and newer, the uninstall dialog now offers to keep MNYBOXD's data.

        ## After updating

        * If you had *Expense + Income*, *Account balance* or *Quick stats* on your home screen, Android removes them during the update.

        ## Install

        Download **`MNYBOXD.apk`** and install it over your current version.
    """.trimIndent()

    private fun json(tag: String = "v2.9.0", assets: String = apkAndSums, body: String = notes) = """
        {"tag_name": "$tag", "html_url": "https://github.com/Sharvesh-AR/MNYBOXD/releases/tag/$tag",
         "body": ${kotlinx.serialization.json.JsonPrimitive(body)},
         "assets": [$assets]}
    """.trimIndent()

    private val apkAndSums = """
        {"name": "MNYBOXD.apk", "size": 10268792, "browser_download_url": "$dl/MNYBOXD.apk"},
        {"name": "SHA256SUMS.txt", "size": 79, "browser_download_url": "$dl/SHA256SUMS.txt"}
    """.trimIndent()

    @Test fun `versions compare number by number`() {
        assertTrue(Releases.isNewer("2.9.0", "2.8.0"))
        assertTrue(Releases.isNewer("2.10.0", "2.9.1"))
        assertTrue(Releases.isNewer("v3.0", "2.99.99"))
        assertTrue(Releases.isNewer("2.8.1", "2.8.0-debug"))
        assertFalse(Releases.isNewer("2.8.0", "2.8.0"))
        assertFalse(Releases.isNewer("2.8.0", "2.8.0-debug"))
        assertFalse(Releases.isNewer("2.7.9", "2.8.0"))
        assertFalse(Releases.isNewer("2.8", "2.8.0"))
        assertFalse(Releases.isNewer("latest", "2.8.0"))
        assertFalse(Releases.isNewer("", "2.8.0"))
    }

    @Test fun `a release is read from GitHub's JSON`() {
        val r = Releases.parse(json())!!
        assertEquals("2.9.0", r.version)
        assertEquals("$dl/MNYBOXD.apk", r.apkUrl)
        assertEquals(10_268_792L, r.apkBytes)
        assertEquals("$dl/SHA256SUMS.txt", r.sumsUrl)
        assertNull(r.imageUrl)
        assertEquals("https://github.com/Sharvesh-AR/MNYBOXD/releases/tag/v2.9.0", r.pageUrl)
    }

    @Test fun `the highlights are the first sentence of each What's new paragraph, without Markdown`() {
        assertEquals(
            listOf(
                "Four widgets: Quick Add, Add income, Monthly overview and Spending trend.",
                "Each widget appears once in the widget list, with a picture of itself.",
                "Keep your data when you uninstall.",
            ),
            Releases.highlights(notes),
        )
    }

    @Test fun `without a What's new section, bullets are the highlights`() {
        assertEquals(listOf("Faster start.", "A link stays as its text."), Releases.highlights("Intro.\n\n* Faster start. More words.\n- A [link](https://x.y) stays as its text."))
        assertEquals(emptyList<String>(), Releases.highlights("Just a paragraph."))
    }

    @Test fun `only this repository's own downloads are used`() {
        val evil = """{"name": "MNYBOXD.apk", "size": 1, "browser_download_url": "https://evil.example/MNYBOXD.apk"}"""
        assertNull(Releases.parse(json(assets = evil)))
        val otherRepo = """{"name": "MNYBOXD.apk", "size": 1, "browser_download_url": "https://github.com/someone/MNYBOXD/releases/download/v2.9.0/MNYBOXD.apk"}"""
        assertNull(Releases.parse(json(assets = otherRepo)))
        assertNull(Releases.parse(json(assets = "")))
        assertNull(Releases.parse(json(tag = "nightly")))
        assertNull(Releases.parse("not json"))
        assertFalse(Releases.isTrustedDownload("http://github.com/Sharvesh-AR/MNYBOXD/releases/download/v1/MNYBOXD.apk"))
        assertFalse(Releases.isTrustedDownload("https://github.com/Sharvesh-AR/MNYBOXD/releases/download/../../x"))
        assertTrue(Releases.isTrustedHost("release-assets.githubusercontent.com"))
        assertTrue(Releases.isTrustedHost("api.github.com"))
        assertFalse(Releases.isTrustedHost("github.com.evil.example"))
        assertFalse(Releases.isTrustedHost(null))
    }

    @Test fun `a picture attached to the release, else one in the notes, is the what's-new image`() {
        val withPicture = "$apkAndSums,\n" + """{"name": "whats-new.png", "size": 9, "browser_download_url": "$dl/whats-new.png"}"""
        assertEquals("$dl/whats-new.png", Releases.parse(json(assets = withPicture))!!.imageUrl)
        val inNotes = "## What's new\n\n![MNYBOXD 2.9](https://github.com/user-attachments/assets/abc)\n\n**New header.** Cleaner."
        assertEquals("https://github.com/user-attachments/assets/abc", Releases.parse(json(body = inNotes))!!.imageUrl)
        assertEquals(listOf("New header."), Releases.parse(json(body = inNotes))!!.highlights)
        assertNull(Releases.imageInNotes("![x](https://evil.example/a.png) <img src=\"http://github.com/a.png\">"))
    }

    @Test fun `the APK's checksum is read from SHA256SUMS`() {
        val sum = "90eb145d39697602fb831bbb4756d97d44565ab301377804ca8665d086f53317"
        assertEquals(sum, Releases.expectedSha256("$sum  MNYBOXD.apk\n", "MNYBOXD.apk"))
        assertEquals(sum, Releases.expectedSha256("${sum.uppercase()} *MNYBOXD.apk", "MNYBOXD.apk"))
        assertNull(Releases.expectedSha256("$sum  Other.apk", "MNYBOXD.apk"))
        assertNull(Releases.expectedSha256("nothex  MNYBOXD.apk", "MNYBOXD.apk"))
    }
}
