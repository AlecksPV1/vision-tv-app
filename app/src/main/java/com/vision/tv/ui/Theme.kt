package com.vision.tv.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

// ───────────── Paleta "Liquid Glass Cinematic" ─────────────
val Obsidian = Color(0xFF060810)      // lienzo base (negro obsidiana)
val Layer1 = Color(0xFF0B0F19)
val Layer2 = Color(0xFF121826)

val Cyan = Color(0xFF00F0FF)      // acento espectral primario: estados activos y foco
val Blue = Color(0xFF007AFF)      // acento atmosférico secundario
val Amethyst = Color(0xFF8A2BE2)  // acento terciario
val LiveRed = Color(0xFFFF3B30)
val ErrorTint = Color(0xFFFFB4AB)
val OnPrimaryDark = Color(0xFF00202A)

val Dim = Color.White.copy(alpha = 0.70f)
val Faint = Color.White.copy(alpha = 0.50f)

// ───────────── Escala 1080p ─────────────
// El diseño está definido en píxeles de una pantalla de 1920 px de ancho. Esta escala lo
// adapta a cualquier densidad (Fire Stick HD/4K, Android TV) manteniendo las proporciones.
val LocalScale = staticCompositionLocalOf { 1f }

/** Píxeles de diseño (1080p) convertidos a dp reales. */
val Int.px: Dp
    @Composable @ReadOnlyComposable
    get() = (this * LocalScale.current / LocalDensity.current.density).dp

// ───────────── Tipografía (Inter) ─────────────
@Immutable
class VType(
    val displayHero: TextStyle,
    val headlineXl: TextStyle,
    val headlineLg: TextStyle,
    val headlineMd: TextStyle,
    val headlineSm: TextStyle,
    val bodyXl: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val labelLg: TextStyle,
    val labelMd: TextStyle,
    val labelBadge: TextStyle,
)

val LocalVType = staticCompositionLocalOf<VType> { error("VType no disponible") }

object V {
    val type: VType
        @Composable @ReadOnlyComposable
        get() = LocalVType.current
}

private fun buildType(k: Float, d: Density, family: FontFamily): VType {
    fun s(size: Int, weight: Int, lineHeight: Int, spacing: Double): TextStyle = with(d) {
        TextStyle(
            fontFamily = family,
            fontSize = (size * k).toSp(),
            fontWeight = FontWeight(weight),
            lineHeight = (lineHeight * k).toSp(),
            letterSpacing = spacing.em,
            fontFeatureSettings = "tnum", // cifras tabulares (horas, duraciones, conexiones)
        )
    }
    return VType(
        displayHero = s(64, 800, 72, -0.03),
        headlineXl = s(48, 700, 56, -0.025),
        headlineLg = s(36, 700, 44, -0.02),
        headlineMd = s(28, 600, 36, -0.015),
        headlineSm = s(24, 600, 32, -0.01),
        bodyXl = s(22, 400, 32, -0.005),
        bodyLg = s(18, 400, 26, 0.0),
        bodyMd = s(16, 400, 24, 0.0),
        labelLg = s(16, 600, 20, 0.02),
        labelMd = s(14, 600, 18, 0.04),
        labelBadge = s(12, 700, 14, 0.08),
    )
}

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current

    val k = remember(cfg.screenWidthDp, density.density) {
        (cfg.screenWidthDp * density.density / 1920f).coerceIn(0.4f, 4f)
    }
    val family = remember {
        FontFamily(
            Font("fonts/Inter-Regular.otf", ctx.assets, FontWeight.Normal),
            Font("fonts/Inter-SemiBold.otf", ctx.assets, FontWeight.SemiBold),
            Font("fonts/Inter-Bold.otf", ctx.assets, FontWeight.Bold),
            Font("fonts/Inter-ExtraBold.otf", ctx.assets, FontWeight.ExtraBold),
        )
    }
    val type = remember(k, density.density, density.fontScale, family) { buildType(k, density, family) }

    CompositionLocalProvider(LocalScale provides k, LocalVType provides type) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Cyan,
                onPrimary = OnPrimaryDark,
                secondary = Blue,
                tertiary = Amethyst,
                background = Obsidian,
                surface = Layer1,
                onBackground = Color.White,
                onSurface = Color.White,
                error = ErrorTint,
            )
        ) {
            ProvideTextStyle(type.bodyLg) {
                Surface(Modifier.fillMaxSize(), color = Obsidian, content = content)
            }
        }
    }
}
