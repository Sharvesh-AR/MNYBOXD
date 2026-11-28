// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import io.github.sharveshar.mnyboxd.BuildConfig
import io.github.sharveshar.mnyboxd.data.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Where an update stands. [release] and [image] carry over from the check to the install. */
data class UpdateState(
    val step: Step = Step.Idle,
    val release: Release? = null,
    val image: Bitmap? = null,
) {
    sealed interface Step {
        data object Idle : Step
        data object Checking : Step
        /** Nothing newer than the installed version. */
        data object UpToDate : Step
        data object Available : Step
        data class Downloading(val bytes: Long, val total: Long) : Step {
            val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
        }
        data class Ready(val file: File) : Step
        data class Failed(val message: String) : Step
    }
}

/**
 * MNYBOXD's in-app updater. It goes online only when the user asks (Settings → Check for
 * updates, or the update arrow on Home), never in the background: it reads the latest release of
 * MNYBOXD on GitHub, downloads `MNYBOXD.apk` into the app's cache and hands it to Android's own
 * installer. Nothing about the user is sent; the requests carry no identifiers.
 *
 * Before the installer sees a download, it must match the release's `SHA256SUMS.txt` and be
 * MNYBOXD itself: the same package, a newer version, signed with the same key as the installed
 * app. (Android refuses a differently signed update anyway; checking first gives a clear message.)
 */
class AppUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val preferences: PreferencesRepository,
    /** The installed version (tests pass an older one to exercise a real update). */
    private val installedVersion: String = BuildConfig.VERSION_NAME,
    private val installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
) {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null
    private val dir get() = File(context.cacheDir, CACHE_DIR)

    /**
     * Asks GitHub for the latest release. Does nothing while a check or download is running, or
     * once an update has been found (the pop-up just shows it again; no second request).
     */
    fun check() {
        when (_state.value.step) {
            UpdateState.Step.Checking, UpdateState.Step.Available, is UpdateState.Step.Downloading, is UpdateState.Step.Ready -> return
            else -> Unit
        }
        _state.value = UpdateState(UpdateState.Step.Checking)
        job = scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            coroutineContext.ensureActive()
            val release = result.getOrNull()
            when {
                result.isFailure -> _state.value = UpdateState(UpdateState.Step.Failed(CHECK_FAILED))
                release == null || !Releases.isNewer(release.version, installedVersion) -> {
                    preferences.setUpdateAvailable(null)
                    _state.value = UpdateState(UpdateState.Step.UpToDate)
                }
                else -> {
                    preferences.setUpdateAvailable(release.version)
                    _state.value = UpdateState(UpdateState.Step.Available, release)
                    // The picture is a nice extra: the pop-up shows without it and adds it when it arrives.
                    val image = release.imageUrl?.let { url -> runCatching { withContext(Dispatchers.IO) { fetchImage(url) } }.getOrNull() }
                    if (image != null) _state.update { if (it.release == release) it.copy(image = image) else it }
                }
            }
        }
    }

    /** Downloads and verifies the APK of the release found by [check]. */
    fun download() {
        val current = _state.value
        val release = current.release ?: return
        if (current.step is UpdateState.Step.Downloading || current.step is UpdateState.Step.Ready) return
        _state.update { it.copy(step = UpdateState.Step.Downloading(0, release.apkBytes)) }
        job = scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchApk(release) } }
            coroutineContext.ensureActive()
            _state.update {
                it.copy(
                    step = result.fold(
                        onSuccess = { file -> UpdateState.Step.Ready(file) },
                        onFailure = { e -> UpdateState.Step.Failed((e as? UpdateProblem)?.message ?: DOWNLOAD_FAILED) },
                    ),
                )
            }
        }
    }

    /** Stops a check or download (a cancelled download deletes its partial file). A found release stays on offer. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.update { s -> if (s.release != null) s.copy(step = UpdateState.Step.Available) else UpdateState() }
    }

    /** Back to the start once the pop-up has closed, keeping a running download or a ready file. */
    fun dismissed() {
        _state.update { s ->
            when (s.step) {
                is UpdateState.Step.Downloading, is UpdateState.Step.Ready, UpdateState.Step.Available -> s
                else -> UpdateState()
            }
        }
    }

    /** True when Android still has to let MNYBOXD install apps (Android 8+: "Install unknown apps"). */
    fun needsInstallPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    /** The screen where the user allows MNYBOXD to install its updates. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Android's installer for the downloaded update, or null if nothing is ready. */
    fun installIntent(): Intent? {
        val file = (_state.value.step as? UpdateState.Step.Ready)?.file?.takeIf { it.isFile } ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Removes downloads left from an earlier run (the app restarts after updating). */
    fun clearDownloads() {
        dir.listFiles()?.forEach { it.delete() }
    }

    // --- Network -------------------------------------------------------------------------------

    private fun fetchLatest(): Release? {
        val body = get(Releases.LATEST_URL, accept = "application/vnd.github+json") { it.readCapped(MAX_JSON_BYTES).decodeToString() }
        return Releases.parse(body)
    }

    private fun fetchImage(url: String): Bitmap? {
        val bytes = get(url) { it.readCapped(MAX_IMAGE_BYTES) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_WIDTH) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private suspend fun fetchApk(release: Release): File {
        val expected = release.sumsUrl?.let { url ->
            val sums = runCatching { get(url) { it.readCapped(MAX_JSON_BYTES).decodeToString() } }.getOrNull()
            sums?.let { Releases.expectedSha256(it, Releases.APK_NAME) }
        }
        dir.mkdirs()
        clearDownloads()
        val file = File(dir, "MNYBOXD-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val connection = open(release.apkUrl)
            try {
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkBytes
                if (total > MAX_APK_BYTES) throw UpdateProblem(DOWNLOAD_FAILED)
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastShown = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            if (done > MAX_APK_BYTES) throw UpdateProblem(DOWNLOAD_FAILED)
                            if (done - lastShown >= 128 * 1024 || done == total) {
                                lastShown = done
                                _state.update { it.copy(step = UpdateState.Step.Downloading(done, total)) }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected != null && actual != expected) throw UpdateProblem("The download was damaged. Try again.")
            verify(file)
            return file
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    /** The file must be MNYBOXD, newer than this version, signed with the installed app's key. */
    private fun verify(file: File) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, file.path) ?: throw UpdateProblem(NOT_MNYBOXD)
        if (archive.packageName != context.packageName) throw UpdateProblem(NOT_MNYBOXD)
        if (PackageInfoCompat.getLongVersionCode(archive) <= installedVersionCode) throw UpdateProblem("This update is already installed.")
        val installed = installedInfo(pm)
        val theirs = signatures(archive)
        val ours = installed?.let(::signatures)
        if (!theirs.isNullOrEmpty() && !ours.isNullOrEmpty() && theirs != ours) throw UpdateProblem(NOT_MNYBOXD)
    }

    /** Android's own reading of the APK (null if it isn't a valid, signed APK). */
    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? = runCatching {
        pm.getPackageArchiveInfo(path, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo? = runCatching {
        pm.getPackageInfo(context.packageName, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String>? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners else info.signatures
        return raw?.map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }?.toSet()
    }

    /** Opens [url] over HTTPS, following redirects only to GitHub's own hosts. */
    private fun open(url: String, accept: String? = null): HttpURLConnection {
        var target = URL(url)
        repeat(MAX_REDIRECTS) {
            if (target.protocol != "https" || !Releases.isTrustedHost(target.host)) throw IOException("Untrusted host")
            val c = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "MNYBOXD-updater")
                if (accept != null) setRequestProperty("Accept", accept)
            }
            when (val code = c.responseCode) {
                in 200..299 -> return c
                301, 302, 303, 307, 308 -> {
                    val next = c.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                    c.disconnect()
                    target = URL(target, next)
                }
                else -> {
                    c.disconnect()
                    throw IOException("HTTP $code")
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private fun <T> get(url: String, accept: String? = null, read: (InputStream) -> T): T {
        val c = open(url, accept)
        try {
            return c.inputStream.use(read)
        } finally {
            c.disconnect()
        }
    }

    private fun InputStream.readCapped(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) throw IOException("Response too large")
        }
        return out.toByteArray()
    }

    /** A failure whose message is meant for the user. */
    private class UpdateProblem(message: String) : Exception(message)

    companion object {
        /** Matches the FileProvider authority in the manifest. */
        const val AUTHORITY_SUFFIX = ".updates"
        private const val CACHE_DIR = "updates"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val MAX_REDIRECTS = 5
        private const val MAX_JSON_BYTES = 1 shl 20
        private const val MAX_IMAGE_BYTES = 6 shl 20
        private const val MAX_IMAGE_WIDTH = 1200
        private const val MAX_APK_BYTES = 200L shl 20
        private const val CHECK_FAILED = "Couldn't reach GitHub. Check your connection and try again."
        private const val DOWNLOAD_FAILED = "The download didn't finish. Check your connection and try again."
        private const val NOT_MNYBOXD = "This file isn't a MNYBOXD update, so it wasn't installed."
    }
}
