package app.latch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LatchColors {
    val Text = Color.White
    val TextDim = Color.White.copy(alpha = 0.6f)
    val Tile = Color.White.copy(alpha = 0.08f)
    val TileHover = Color.White.copy(alpha = 0.18f)
    val DrawerScrim = Color(0xFF0B0B0B).copy(alpha = 0.82f)
    val HeaderScrim = Color(0xFF0B0B0B).copy(alpha = 0.55f)
    val Placeholder = Color.White.copy(alpha = 0.15f)
    val SearchPanel = Color(0xFF3A3A3A).copy(alpha = 0.72f)
}

/** Soft dark shadow so white text stays readable on bright wallpapers. */
val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 2f), blurRadius = 10f)

object LatchType {
    val CategoryName = TextStyle(
        color = LatchColors.Text,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.ExtraBold,
        shadow = TextShadow,
    )
    val SectionHeader = TextStyle(
        color = LatchColors.Text,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        shadow = TextShadow,
    )
    val TileLabel = TextStyle(
        color = LatchColors.Text,
        fontSize = 13.sp,
        lineHeight = 15.sp,
        shadow = TextShadow,
    )
    val Hint = TextStyle(color = LatchColors.TextDim, fontSize = 15.sp, shadow = TextShadow)
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2A2A2A),
)

@Composable
fun LatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
