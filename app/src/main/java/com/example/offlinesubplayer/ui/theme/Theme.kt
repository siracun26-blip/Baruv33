package com.example.offlinesubplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinesubplayer.R

val SubPlayerSkyAccent = Color(0xFF7DD3FC)
val SubPlayerDeepNavy = Color(0xFF090D16)
val SubPlayerSurfaceElevated = Color(0xFF131B2E)
val SubPlayerDangerTint = Color(0xFFFCA5A5)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

private val SubPlayerDarkColorScheme = darkColorScheme(
    primary = SubPlayerSkyAccent,
    onPrimary = SubPlayerDeepNavy,
    secondary = Color(0xFF38BDF8),
    onSecondary = SubPlayerDeepNavy,
    surface = SubPlayerDeepNavy,
    onSurface = Color.White,
    surfaceVariant = SubPlayerSurfaceElevated,
    onSurfaceVariant = Color(0xFFCBD5E1),
    background = SubPlayerDeepNavy,
    onBackground = Color.White,
    error = SubPlayerDangerTint,
    onError = SubPlayerDeepNavy
)

private val SubPlayerTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val SubPlayerShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun SubPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SubPlayerDarkColorScheme,
        typography = SubPlayerTypography,
        shapes = SubPlayerShapes,
        content = content
    )
}
