package com.vision.tv.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex

/** Curva del motor de foco: cubic-bezier(0.2, 0.9, 0.4, 1.1) en 240 ms. */
val FocusEasing = CubicBezierEasing(0.2f, 0.9f, 0.4f, 1.1f)

/** Radio "pastilla": se recorta solo a la mitad de la altura. */
val Pill: Dp = 999.dp

/**
 * Panel de vidrio líquido.
 *
 * Android no ofrece desenfoque del fondo (backdrop blur) en Fire OS 5/7, así que el efecto se
 * construye con capas: relleno translúcido, luz cenital, borde especular degradado y una línea de
 * brillo interior. Sobre el fondo ambiental de Vision se lee como vidrio esmerilado.
 *
 * @param focus 0..1: intensifica relleno, borde y halo (bloom).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 16.px,
    fill: Float = 0.06f,
    rim: Float = 0.12f,
    rimColor: Color = Color.White,
    brush: Brush? = null,
    focus: Float = 0f,
    bloom: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val k = LocalScale.current
    val shape = RoundedCornerShape(radius)
    val f = focus.coerceIn(0f, 1f)
    val rimWidth = 1.px.coerceAtLeast(1.dp)
    val rimBrush = Brush.verticalGradient(
        listOf(
            rimColor.copy(alpha = lerp(rim, 0.75f, f)),
            rimColor.copy(alpha = lerp(rim * 0.35f, 0.40f, f)),
        )
    )

    Box(
        modifier
            .then(
                if (bloom && f > 0.01f) Modifier.drawBehind { drawBloom(radius.toPx(), f, k) }
                else Modifier
            )
            .clip(shape)
            .drawBehind {
                if (brush != null) drawRect(brush) else drawRect(Color.White.copy(alpha = lerp(fill, 0.18f, f)))
                // luz cenital que cae desde el borde superior
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                        endY = size.height * 0.6f,
                    )
                )
                if (brush != null && f > 0f) drawRect(Color.White.copy(alpha = 0.16f * f))
                // brillo especular interior
                val r = minOf(radius.toPx(), size.minDimension / 2f)
                drawLine(
                    color = Color.White.copy(alpha = 0.14f + 0.25f * f),
                    start = Offset(r, 1f),
                    end = Offset(size.width - r, 1f),
                    strokeWidth = maxOf(1f, 1.5f * k),
                )
            }
            .border(rimWidth, rimBrush, shape),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Halo luminoso: aura cian hacia abajo + resplandor azul difuso alrededor. */
private fun DrawScope.drawBloom(r: Float, f: Float, k: Float) {
    val cyanLayers = 8
    val cyanSpread = 40f * k
    val dy = 20f * k
    for (i in 1..cyanLayers) {
        val s = cyanSpread * i / cyanLayers
        drawRoundRect(
            color = Cyan.copy(alpha = 0.035f * f),
            topLeft = Offset(-s, -s + dy),
            size = Size(size.width + 2 * s, size.height + 2 * s),
            cornerRadius = CornerRadius(r + s),
        )
    }
    val blueLayers = 6
    val blueSpread = 64f * k
    for (i in 1..blueLayers) {
        val s = blueSpread * i / blueLayers
        drawRoundRect(
            color = Blue.copy(alpha = 0.03f * f),
            topLeft = Offset(-s, -s),
            size = Size(size.width + 2 * s, size.height + 2 * s),
            cornerRadius = CornerRadius(r + s),
        )
    }
}

/**
 * Elemento navegable con control remoto: al enfocarse crece 1.05x, se ilumina y proyecta el halo.
 * `content` recibe `true` mientras tiene el foco (para cambiar color o peso del texto).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    radius: Dp = 16.px,
    fill: Float = 0.06f,
    rim: Float = 0.10f,
    rimColor: Color = Color.White,
    brush: Brush? = null,
    scale: Float = 0.05f,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val amt by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FocusEasing),
        label = "focus",
    )
    val source = remember { MutableInteractionSource() }

    GlassSurface(
        modifier = modifier
            .zIndex(amt)
            .graphicsLayer {
                val s = 1f + scale * amt
                scaleX = s
                scaleY = s
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        radius = radius,
        fill = fill,
        rim = rim,
        rimColor = rimColor,
        brush = brush,
        focus = amt,
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}
