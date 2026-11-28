// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI

/** The newest MNYBOXD release on GitHub, as the update pop-up shows it. */
data class Release(
    /** "2.9.0" (the tag without its "v"). */
    val version: String,
    /** The release's page on GitHub. */
    val pageUrl: String,
    /** `MNYBOXD.apk`, the app itself. */
    val apkUrl: String,
    /** The APK's size in bytes, or 0 when GitHub didn't say. */
    val apkBytes: Long,
    /** `SHA256SUMS.txt`, checked against the downloaded APK when present. */
    val sumsUrl: String?,
    /** A "what's new" picture: an image attached to the release, else the first image in its notes. */
    val imageUrl: String?,
    /** A few one-line highlights from the release notes' "What's new" section. */
    val highlights: List<String>,
)

/**
 * Reads MNYBOXD's GitHub releases. Pure functions only, so they're tested without a network;
 * [AppUpdater] does the fetching. Release notes follow the format used since 2.0: a
 * "## What's new" section whose paragraphs open with a bold sentence.
 */
object Releases {
    const val REPO = "Sharvesh-AR/MNYBOXD"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val APK_NAME = "MNYBOXD.apk"
    const val SUMS_NAME = "SHA256SUMS.txt"

    /** Release files are only ever fetched from this repository's own download links. */
    private const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    private const val MAX_HIGHLIGHTS = 4
    private const val MAX_HIGHLIGHT_LENGTH = 160

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * True when [candidate] is a later version than [current]. Versions are compared number by
     * number ("2.10.0" is newer than "2.9.1"); a "v" prefix and anything after "-" ("-debug",
     * "-beta.1") are ignored.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = numbers(candidate) ?: return false
        val b = numbers(current) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 6 }) return null
        return parts.map { it.toInt() }
    }

    /** The release described by GitHub's "latest release" JSON, or null if it has no usable APK. */
    fun parse(body: String): Release? = runCatching {
        val release = json.parseToJsonElement(body) as JsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val version = tag.removePrefix("v").removePrefix("V")
        if (numbers(version) == null) return null
        val assets = release["assets"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
        fun asset(name: String) = assets.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == name }
        fun url(asset: JsonObject?) = asset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull?.takeIf(::isTrustedDownload)
        val apk = asset(APK_NAME)
        val apkUrl = url(apk) ?: return null
        val notes = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val picture = assets.firstOrNull { a ->
            val name = a["name"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")
        }
        Release(
            version = version,
            pageUrl = release["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$REPO/releases/tag/$tag",
            apkUrl = apkUrl,
            apkBytes = apk?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
            sumsUrl = url(asset(SUMS_NAME)),
            imageUrl = url(picture) ?: imageInNotes(notes),
            highlights = highlights(notes),
        )
    }.getOrNull()

    /** A link inside the repository's own release downloads (https only, no tricks in the path). */
    fun isTrustedDownload(url: String): Boolean =
        url.startsWith(DOWNLOAD_PREFIX) && runCatching { URI(url).host == "github.com" }.getOrDefault(false) && ".." !in url

    /** Hosts a release file may come from: GitHub itself and its download servers. */
    fun isTrustedHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "github.com" || h == "api.github.com" || h.endsWith(".githubusercontent.com")
    }

    /** The first picture in the notes (Markdown or HTML), if it's hosted on GitHub. */
    fun imageInNotes(notes: String): String? {
        val candidates = Regex("""!\[[^\]]*]\(\s*(\S+?)\s*(?:"[^"]*")?\)""").findAll(notes).map { it.groupValues[1] } +
            Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(notes).map { it.groupValues[1] }
        return candidates.firstOrNull { url ->
            url.startsWith("https://") && runCatching { isTrustedHost(URI(url).host) }.getOrDefault(false)
        }
    }

    /**
     * Up to four one-line highlights: the first sentence of each paragraph or bullet under
     * "What's new", without Markdown. Falls back to bullets anywhere in the notes.
     */
    fun highlights(notes: String): List<String> {
        val lines = notes.replace("\r\n", "\n").lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("#") && heading(it).replace('’', '\'').equals("what's new", ignoreCase = true) }
        val section = if (start >= 0) {
            lines.drop(start + 1).takeWhile { !it.trimStart().startsWith("#") }
        } else {
            lines.filter { it.trimStart().let { l -> l.startsWith("* ") || l.startsWith("- ") } }
        }
        // Paragraphs: blank lines separate them; each bullet is its own item.
        val items = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) items += current.toString()
            current.clear()
        }
        for (raw in section) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("* ") || line.startsWith("- ") -> { flush(); current.append(line.drop(2)); flush() }
                line.startsWith(">") || line.startsWith("![") || line.startsWith("<") || line.startsWith("```") -> flush()
                else -> { if (current.isNotEmpty()) current.append(' '); current.append(line) }
            }
        }
        flush()
        return items.asSequence()
            .map { firstSentence(plain(it)) }
            .filter { it.length >= 3 }
            .map { if (it.length > MAX_HIGHLIGHT_LENGTH) it.take(MAX_HIGHLIGHT_LENGTH - 1).trimEnd() + "…" else it }
            .take(MAX_HIGHLIGHTS)
            .toList()
    }

    private fun heading(line: String) = line.trim().trimStart('#').trim()

    /** Markdown to plain text: links keep their text; bold, italics and code marks go. */
    internal fun plain(text: String): String = text
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("**", "").replace("__", "")
        .replace(Regex("""(?<![\w*])[*_](?=\S)(.+?)(?<=\S)[*_](?![\w*])"""), "$1")
        .replace("`", "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun firstSentence(text: String): String {
        val end = Regex("""[.!?](\s|$)""").find(text)?.range?.first ?: return text
        return text.substring(0, end + 1)
    }

    /** The SHA-256 listed for [fileName] in a `sha256sum` file, lower-case, or null. */
    fun expectedSha256(sums: String, fileName: String): String? = sums.lines().firstNotNullOfOrNull { line ->
        val parts = line.trim().split(Regex("""\s+"""), limit = 2)
        if (parts.size == 2 && parts[1].removePrefix("*") == fileName && parts[0].matches(Regex("[0-9a-fA-F]{64}"))) parts[0].lowercase() else null
    }
}
