package com.vision.tv.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

// ───────────── Fondo ambiental ─────────────

/** Vacío obsidiana con tres luces difusas (cian, azul, amatista) que dan profundidad al vidrio. */
@Composable
fun VisionBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Obsidian)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(w * 0.12f, h * 0.05f),
                        radius = w * 0.55f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Amethyst.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(w * 0.95f, h * 0.95f),
                        radius = w * 0.60f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Blue.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(w * 0.60f, h * 0.30f),
                        radius = w * 0.45f,
                    )
                )
            },
        content = content,
    )
}

// ───────────── Marca ─────────────

/** Lente de vidrio con anillo espectral y símbolo de reproducción. Es el mismo del icono. */
@Composable
fun VisionMark(dimension: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(dimension)) {
        val s = size.minDimension
        val corner = CornerRadius(s * 0.28f)
        drawRoundRect(
            Brush.linearGradient(listOf(Color.White.copy(0.22f), Color.White.copy(0.05f))),
            cornerRadius = corner,
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f))),
            cornerRadius = corner,
            style = Stroke(width = maxOf(1f, s * 0.012f)),
        )
        drawCircle(
            brush = Brush.sweepGradient(listOf(Cyan, Blue, Amethyst, Cyan), center),
            radius = s * 0.30f,
            center = center,
            style = Stroke(width = s * 0.06f),
        )
        val c = center
        val play = Path().apply {
            moveTo(c.x - s * 0.07f, c.y - s * 0.13f)
            lineTo(c.x + s * 0.15f, c.y)
            lineTo(c.x - s * 0.07f, c.y + s * 0.13f)
            close()
        }
        drawPath(play, Color.White)
        drawPath(play, Color.White, style = Stroke(width = s * 0.035f, join = StrokeJoin.Round))
    }
}

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 44.px,
    textStyle: TextStyle = V.type.headlineSm,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        VisionMark(markSize)
        Spacer(Modifier.width(14.px))
        Text("Vision", style = textStyle.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
    }
}

// ───────────── Avatares ─────────────

val AvatarGradients: List<Pair<Color, Color>> = listOf(
    Cyan to Blue,
    Blue to Amethyst,
    Amethyst to Color(0xFFFF4FA3),
    Cyan to Color(0xFF2EE6A6),
    Color(0xFFFFB347) to Color(0xFFFF4FA3),
    Color(0xFF2EE6A6) to Blue,
    Color(0xFF7DF4FF) to Amethyst,
    Blue to Color(0xFFFF4FA3),
)

fun avatarBrush(index: Int): Brush {
    val (a, b) = AvatarGradients[index.mod(AvatarGradients.size)]
    return Brush.linearGradient(listOf(a.copy(alpha = 0.92f), b.copy(alpha = 0.92f)))
}

@Composable
fun Avatar(index: Int, letter: String, diameter: Dp, modifier: Modifier = Modifier) {
    val fontSize: TextUnit = with(LocalDensity.current) { (diameter.toPx() * 0.42f).toSp() }
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(avatarBrush(index))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(this.size.width * 0.3f, this.size.height * 0.2f),
                        radius = this.size.width * 0.7f,
                    )
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = V.type.headlineLg.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = TextUnit.Unspecified,
            ),
            color = Color.White,
        )
    }
}

// ───────────── Botones ─────────────

@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val primaryBrush = remember {
        Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.92f), Blue.copy(alpha = 0.92f)))
    }
    Focusable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.px),
        radius = Pill,
        fill = if (selected) 0.16f else 0.07f,
        rim = if (selected) 0.55f else 0.14f,
        rimColor = if (selected) Cyan else Color.White,
        brush = if (primary) primaryBrush else null,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Text(
            text,
            Modifier.padding(horizontal = 28.px, vertical = 12.px),
            style = V.type.labelLg,
            color = when {
                primary -> OnPrimaryDark
                focused -> Color.White
                else -> Color.White.copy(alpha = 0.82f)
            },
            maxLines = 1,
        )
    }
}

// ───────────── Campo de texto de vidrio ─────────────

@Composable
fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val t = V.type
    var focused by remember { mutableStateOf(false) }
    val amt by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FocusEasing),
        label = "field",
    )

    Column(modifier) {
        if (!compact) {
            Text(label, style = t.labelMd, color = lerpColor(Dim, Cyan, amt.coerceIn(0f, 1f)))
            Spacer(Modifier.height(8.px))
        }
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = (if (compact) 48 else 56).px),
            radius = 16.px,
            fill = 0.06f,
            rim = 0.14f,
            focus = amt,
            bloom = false,
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = t.bodyLg.copy(color = Color.White),
                cursorBrush = SolidColor(Cyan),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password && keyboard == KeyboardType.Text) KeyboardType.Password else keyboard,
                    imeAction = imeAction,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .padding(horizontal = 20.px, vertical = 14.px),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && compact) Text(label, style = t.bodyLg, color = Faint)
                        inner()
                    }
                },
            )
        }
    }
}

// ───────────── Insignias ─────────────

enum class BadgeTone { Neutral, Live, Alert, Accent }

@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.Neutral, modifier: Modifier = Modifier) {
    val (bg, line, fg) = when (tone) {
        BadgeTone.Neutral -> Triple(Color.White.copy(0.10f), Color.White.copy(0.18f), Color.White.copy(0.85f))
        BadgeTone.Live -> Triple(LiveRed.copy(0.15f), LiveRed.copy(0.40f), LiveRed)
        BadgeTone.Alert -> Triple(ErrorTint.copy(0.12f), ErrorTint.copy(0.40f), ErrorTint)
        BadgeTone.Accent -> Triple(Cyan.copy(0.12f), Cyan.copy(0.40f), Cyan)
    }
    val shape = RoundedCornerShape(Pill)
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, line, shape)
            .padding(horizontal = 14.px, vertical = 6.px),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == BadgeTone.Live) {
            val pulse = rememberInfiniteTransition(label = "live")
            val a by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "dot",
            )
            Box(
                Modifier
                    .size(6.px)
                    .graphicsLayer { alpha = a }
                    .background(LiveRed, CircleShape)
            )
            Spacer(Modifier.width(8.px))
        }
        Text(text, style = V.type.labelBadge, color = fg, maxLines = 1)
    }
}

/** Tecla + descripción, para recordar los controles del mando. */
@Composable
fun HintChip(key: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Badge(key)
        Spacer(Modifier.width(10.px))
        Text(label, style = V.type.labelMd, color = Faint)
    }
}
