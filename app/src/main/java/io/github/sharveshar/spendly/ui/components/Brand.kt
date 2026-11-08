package io.github.sharveshar.spendly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sharveshar.spendly.ui.theme.Spendly

/** The app's name as shown on screen. */
const val APP_NAME = "MNYBOXD"

/** MNYBOXD's mark: a light disc with a dark "M", following the theme. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(modifier.size(size).background(Spendly.colors.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            "M",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, fontSize = (size.value * 0.5f).sp, letterSpacing = 0.sp),
            color = Spendly.colors.onAccent,
        )
    }
}

/** "MNYBOXD", set in capitals with a little air between the letters. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        APP_NAME,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
        modifier = modifier,
    )
}
