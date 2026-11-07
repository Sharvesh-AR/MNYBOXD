package io.github.sharveshar.spendly.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import java.io.File

/**
 * Captures the whole screen: the activity window plus any dialog windows above it (Quick Add,
 * popups), composited bottom to top. `onRoot()` alone fails once a dialog is open, and would miss
 * it. The test framework lists the newest window first, so roots are drawn in reverse.
 */
fun SemanticsNodeInteractionsProvider.captureScreen(): Bitmap {
    val roots = onAllNodes(isRoot())
    val count = roots.fetchSemanticsNodes(atLeastOneRootRequired = true).size
    val base = roots[count - 1].captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(base)
    for (i in count - 2 downTo 0) canvas.drawBitmap(roots[i].captureToImage().asAndroidBitmap(), 0f, 0f, null)
    return base
}

fun Bitmap.savePng(file: File) = file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
