// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val text = remember(ink) {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(APP_NAME.take(3)) }
            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = ink.copy(alpha = 0.6f))) { append(APP_NAME.drop(3)) }
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, letterSpacing = 4.2.sp),
        color = ink,
        maxLines = 1,
        modifier = modifier,
    )
}
