package com.vision.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.vision.tv.data.AccountInfo
import com.vision.tv.data.Media
import com.vision.tv.data.MediaKind
import com.vision.tv.data.Progress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────── Barra de progreso ─────────────

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.px) {
    val shape = RoundedCornerShape(Pill)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Cyan)
        )
    }
}

// ───────────── Chips de la cápsula de navegación ─────────────

@Composable
fun ProfileChip(name: String, avatar: Int, emoji: String, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        radius = Pill,
        fill = 0.08f,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(start = 8.px, end = 22.px, top = 6.px, bottom = 6.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(avatar, avatarGlyph(emoji, name), 36.px)
            Spacer(Modifier.width(12.px))
            Text(name, style = V.type.labelLg, maxLines = 1)
        }
    }
}

@Composable
fun SourceChip(name: String, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        radius = Pill,
        fill = 0.08f,
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            name,
            Modifier.widthIn(max = 260.px).padding(horizontal = 24.px, vertical = 14.px),
            style = V.type.labelLg, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Conexiones en uso y vencimiento de la cuenta Xtream. */
@Composable
fun AccountBadges(a: AccountInfo?) {
    if (a == null) return
    val max = if (a.maxConnections > 0) a.maxConnections.toString() else "?"
    val full = a.maxConnections > 0 && a.activeConnections >= a.maxConnections
    Row(horizontalArrangement = Arrangement.spacedBy(12.px), verticalAlignment = Alignment.CenterVertically) {
        Badge(
            "CONEXIONES ${a.activeConnections}/$max" + if (full) " · LÍMITE ALCANZADO" else "",
            if (full) BadgeTone.Alert else BadgeTone.Neutral,
        )
        a.expDate?.let {
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it * 1000))
            Badge("VENCE $date")
        }
    }
}

// ───────────── Imágenes ─────────────

/** Logo del canal sobre un cuadro de vidrio; si no hay logo, muestra la inicial. */
@Composable
fun ChannelLogo(c: Media, dimension: Dp) {
    val t = V.type
    Box(
        Modifier
            .size(dimension)
            .clip(RoundedCornerShape(14.px))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (c.logo != null) {
            AsyncImage(
                model = c.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.px),
            )
        } else {
            Text(c.name.take(1).uppercase(), style = t.headlineSm, color = Dim)
        }
    }
}

// ───────────── Tarjetas ─────────────

/** Póster de película o serie con título, marcas de favorito / mi lista y barra de avance. */
@Composable
fun PosterCard(
    m: Media,
    fraction: Float,
    isFav: Boolean,
    inList: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = V.type
    val meta = listOfNotNull(
        m.year.ifBlank { null },
        m.rating.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 }?.let { "Nota $it" },
    ).joinToString(" · ")

    Focusable(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        radius = 16.px,
    ) { focused ->
        Column(Modifier.padding(10.px)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.px))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                if (m.logo != null) {
                    AsyncImage(
                        model = m.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        m.name.take(1).uppercase(),
                        Modifier.align(Alignment.Center),
                        style = t.headlineLg, color = Dim,
                    )
                }
                if (isFav || inList) {
                    Badge(
                        if (isFav) "FAV" else "MI LISTA",
                        if (isFav) BadgeTone.Accent else BadgeTone.Neutral,
                        Modifier.align(Alignment.TopStart).padding(8.px),
                    )
                }
                if (fraction > 0f) {
                    ProgressBar(
                        fraction,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.px),
                    )
                }
            }
            Spacer(Modifier.height(10.px))
            Text(
                m.name,
                style = t.labelLg,
                color = if (focused) Color.White else Dim,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta.ifEmpty { " " }, style = t.labelMd, color = Faint, maxLines = 1)
        }
    }
}

/** Tarjeta del tablero "Continuar viendo": póster, título, episodio y cuánto falta. */
@Composable
fun ContinueCard(p: Progress, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = V.type
    val m = p.media
    val isEpisode = m.kind == MediaKind.EPISODE

    Focusable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(560.px).height(216.px),
        radius = 20.px,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(14.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.px))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                if (m.logo != null) {
                    AsyncImage(
                        model = m.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        (if (isEpisode) m.seriesName else m.name).take(1).uppercase(),
                        Modifier.align(Alignment.Center),
                        style = t.headlineLg, color = Dim,
                    )
                }
            }
            Spacer(Modifier.width(20.px))
            Column(Modifier.weight(1f)) {
                Badge(if (isEpisode) "SERIE" else "PELÍCULA", BadgeTone.Accent)
                Spacer(Modifier.height(8.px))
                Text(
                    if (isEpisode) m.seriesName else m.name,
                    style = t.headlineSm,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.9f),
                    maxLines = if (isEpisode) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isEpisode) {
                    Text(
                        "T${m.season} · E${m.episode} · ${m.name}",
                        style = t.bodyMd, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.px))
                if (!p.finished) {
                    ProgressBar(p.fraction, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.px))
                    Text(remainingText(p.positionMs, p.durationMs), style = t.labelMd, color = Faint, maxLines = 1)
                } else {
                    Text("Terminado · sigue con el siguiente", style = t.labelMd, color = Faint, maxLines = 2)
                }
            }
        }
    }
}
