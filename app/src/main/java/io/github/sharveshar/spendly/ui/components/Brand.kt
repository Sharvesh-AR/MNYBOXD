// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.R

/** The app's name as shown on screen. */
const val APP_NAME = "MNYBOXD"

/**
 * MNYBOXD's logo: the launcher icon's three bars on their own, with no tile or shape around them.
 * Drawn in the theme's text colour (white on Mono Dark, ink on Mono Light); the bars' stepped
 * transparency gives the bright, grey and faint steps on either. [height] sets the size; the
 * width follows the logo's 5:4 proportions.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Image(
        painterResource(R.drawable.ic_logo),
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        modifier = modifier.size(width = height * 1.25f, height = height),
    )
}
