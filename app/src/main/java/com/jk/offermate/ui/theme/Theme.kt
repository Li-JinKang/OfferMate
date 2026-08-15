package com.jk.offermate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = OnIndigoContainer,
    background = ScreenBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = ScreenBackground,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft
)

/**
 * 应用主题。当前仅提供浅色方案（贴合设计稿）；深色留待后续。
 */
@Composable
fun OfferMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 目前统一使用浅色配色，避免深色下设计稿走样；后续再补深色方案。
    MaterialTheme(
        colorScheme = LightColors,
        typography = OfferMateTypography,
        content = content
    )
}
