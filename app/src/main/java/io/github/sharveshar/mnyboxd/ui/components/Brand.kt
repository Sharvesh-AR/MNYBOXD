// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** The app's name as shown on screen. */
const val APP_NAME = "MNYBOXD"

/**
 * "MNYBOXD", the name as the app's header. Two weights, one word: "MNY" firm, "BOXD" light and a
 * shade softer, with wide, even tracking. Theme text colour (white on Mono Dark, ink on Mono
 * Light). Reads as one word to screen readers.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val text = remember(ink) { wordmarkText(ink) }
    Text(
        text,
        style = wordmarkStyle(MaterialTheme.typography.titleLarge),
        color = ink,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The wordmark's type: shared with the opening animation so its last frame is the header. */
fun wordmarkStyle(titleLarge: TextStyle): TextStyle = titleLarge.copy(fontSize = 21.sp, letterSpacing = 4.2.sp)

/** The two-weight name: "MNY" semi-bold in [ink], "BOXD" light at 60%. */
fun wordmarkText(ink: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(APP_NAME.take(3)) }
    withStyle(SpanStyle(fontWeight = FontWeight.Light, color = boxdColor(ink))) { append(APP_NAME.drop(3)) }
}

/** The softer colour of "BOXD". */
fun boxdColor(ink: Color): Color = ink.copy(alpha = 0.6f)
