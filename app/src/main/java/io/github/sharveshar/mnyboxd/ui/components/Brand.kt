// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The app's name as shown on screen. */
const val APP_NAME = "MNYBOXD"

/**
 * "MNYBOXD", the name as the app's header: bold capitals with a little air between the letters,
 * in the theme's text colour (white on Mono Dark, ink on Mono Light).
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        APP_NAME,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        modifier = modifier,
    )
}
