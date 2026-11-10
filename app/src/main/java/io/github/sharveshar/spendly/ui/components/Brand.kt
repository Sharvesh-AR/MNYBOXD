package io.github.sharveshar.spendly.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.R
import io.github.sharveshar.spendly.ui.theme.Spendly

/** The app's name as shown on screen. */
const val APP_NAME = "MNYBOXD"

/** The launcher icon's background (`@color/spendly_background`), so the logo matches it exactly. */
private val LogoBackground = Color(0xFF0B0B0C)

/**
 * MNYBOXD's app logo, the same artwork as the launcher icon: three rounded bars on matte black.
 * The adaptive-icon foreground is 108dp with the visible part in the middle 72dp, so it's drawn
 * 1.5× larger and clipped, as a launcher would. On dark themes a hairline outlines the tile so it
 * doesn't melt into the matte-black page.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val shape = RoundedCornerShape(percent = 30)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(LogoBackground)
            .then(if (Spendly.colors.isDark) Modifier.border(1.dp, Spendly.colors.hairline, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}
