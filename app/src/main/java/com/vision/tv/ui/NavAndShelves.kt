package com.vision.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.tv.AppViewModel
import com.vision.tv.Screen
import com.vision.tv.data.Media
import com.vision.tv.data.MediaKind
import com.vision.tv.data.Progress
import com.vision.tv.data.SourceType
import kotlinx.coroutines.delay

/** Pide el foco a un elemento que quizá aún no está en pantalla: reintenta unos instantes. */
suspend fun FocusRequester.requestWhenReady(tries: Int = 20) {
    repeat(tries) {
        val ok = try {
            requestFocus()
            true
        } catch (e: IllegalStateException) {
            false
        }
        if (ok) return
        delay(60)
    }
}

// ───────────── Barra superior estilo Netflix ─────────────

/** Logo + pestañas a la izquierda; fuente y perfil a la derecha. La pestaña actual va resaltada. */
@Composable
fun NavBar(vm: AppViewModel, current: Screen) {
    val t = V.type
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val source by vm.activeSource.collectAsStateWithLifecycle()
    val xtream = source?.type == SourceType.XTREAM

    // Películas y series solo existen en cuentas Xtream; una lista M3U trae solo canales.
    val tabs = remember(xtream) {
        buildList<Pair<String, Screen>> {
            add("Inicio" to Screen.Home)
            add("TV en vivo" to Screen.Catalog(MediaKind.LIVE))
            if (xtream) {
                add("Películas" to Screen.Catalog(MediaKind.MOVIE))
                add("Series" to Screen.Catalog(MediaKind.SERIES))
            }
            add("Mi lista" to Screen.MyList)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Obsidian.copy(alpha = 0.92f), Color.Transparent)))
            .padding(start = 56.px, end = 56.px, top = 20.px, bottom = 12.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.px),
    ) {
        VisionMark(44.px)
        Spacer(Modifier.width(20.px))
        tabs.forEach { (label, screen) ->
            val selected = current == screen
            Focusable(
                onClick = { if (!selected) vm.switchTab(screen) },
                radius = Pill,
                fill = 0f,
                rim = 0f,
                scale = 0.03f,
                contentAlignment = Alignment.Center,
            ) { focused ->
                Column(
                    Modifier.padding(horizontal = 22.px, vertical = 10.px),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        style = t.labelLg,
                        color = if (selected || focused) Color.White else Dim,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.px))
                    Box(
                        Modifier
                            .width(28.px)
                            .height(3.px)
                            .clip(RoundedCornerShape(Pill))
                            .background(if (selected) Cyan else Color.Transparent)
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SourceChip(source?.name ?: "Fuentes") { vm.push(Screen.Sources) }
        Spacer(Modifier.width(4.px))
        ProfileChip(profile?.name ?: "", profile?.avatar ?: 0, profile?.emoji ?: "") { vm.switchProfile() }
    }
}

/** Ayuda de botones al pie de las pantallas con filas. */
@Composable
fun HintBar() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 64.px, vertical = 10.px),
        horizontalArrangement = Arrangement.spacedBy(32.px),
    ) {
        HintChip("OK", "Abrir")
        HintChip("MANTÉN OK", "Favorito")
        HintChip("ATRÁS", "Volver")
    }
}

// ───────────── Filas ─────────────

/** Una fila horizontal con título, de borde a borde de la pantalla. */
@Composable
fun MediaRow(title: String, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val t = V.type
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            Modifier.padding(start = 64.px, top = 12.px),
            style = t.headlineSm, color = Color.White, maxLines = 1,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 64.px, vertical = 20.px),
            horizontalArrangement = Arrangement.spacedBy(20.px),
            content = content,
        )
    }
}

/** Lo que las filas necesitan saber del perfil: favoritos, mi lista y avance. */
class Shelf(
    val vm: AppViewModel,
    val favKeys: Set<String>,
    val watchKeys: Set<String>,
    val progress: Map<String, Progress>,
) {
    fun open(p: Progress) {
        if (p.media.kind == MediaKind.EPISODE) vm.openDetail(vm.seriesOf(p.media)) else vm.playMovie(p.media)
    }
}

@Composable
fun rememberShelf(vm: AppViewModel): Shelf {
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val watch by vm.watchlist.collectAsStateWithLifecycle()
    val progress by vm.progressMap.collectAsStateWithLifecycle()
    val favKeys = remember(favs) { favs.map { it.key }.toHashSet() }
    val watchKeys = remember(watch) { watch.map { it.key }.toHashSet() }
    return Shelf(vm, favKeys, watchKeys, progress)
}

private fun Modifier.firstFocus(i: Int, first: FocusRequester?): Modifier =
    if (i == 0 && first != null) focusRequester(first) else this

/** Pósters de películas o series; [more] añade al final la tarjeta "Ver todo". */
fun LazyListScope.posterItems(
    list: List<Media>,
    s: Shelf,
    first: FocusRequester? = null,
    more: (() -> Unit)? = null,
) {
    itemsIndexed(list) { i, m ->
        val p = s.progress[m.key]
        PosterCard(
            m = m,
            fraction = if (p != null && !p.finished) p.fraction else 0f,
            isFav = m.key in s.favKeys,
            inList = m.key in s.watchKeys,
            onClick = { s.vm.openDetail(m) },
            onLongClick = { s.vm.toggleFavorite(m) },
            modifier = Modifier.width(190.px).firstFocus(i, first),
        )
    }
    if (more != null) item { SeeAllCard(poster = true, onClick = more) }
}

/** Canales; [queue] es la lista completa que se reproduce al elegir uno (zapping con arriba/abajo). */
fun LazyListScope.channelItems(
    list: List<Media>,
    queue: List<Media>,
    s: Shelf,
    first: FocusRequester? = null,
    more: (() -> Unit)? = null,
) {
    itemsIndexed(list) { i, c ->
        ChannelCard(
            c = c,
            isFav = c.key in s.favKeys,
            onPlay = { s.vm.play(queue, i) },
            onFavorite = { s.vm.toggleFavorite(c) },
            modifier = Modifier.width(360.px).firstFocus(i, first),
        )
    }
    if (more != null) item { SeeAllCard(poster = false, onClick = more) }
}

fun LazyListScope.continueItems(list: List<Progress>, s: Shelf, first: FocusRequester? = null) {
    itemsIndexed(list) { i, p -> ContinueCard(p, { s.open(p) }, Modifier.firstFocus(i, first)) }
}

@Composable
fun SeeAllCard(poster: Boolean, onClick: () -> Unit) {
    val t = V.type
    Focusable(
        onClick = onClick,
        modifier = if (poster) Modifier.width(190.px).height(356.px) else Modifier.width(220.px).height(104.px),
        radius = 16.px,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("→", style = t.headlineLg, color = if (focused) Cyan else Dim)
            Text("Ver todo", style = t.labelLg, color = if (focused) Color.White else Dim)
        }
    }
}

/** Canal en una fila o cuadrícula: logo, nombre y categoría. */
@Composable
fun ChannelCard(
    c: Media,
    isFav: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = V.type
    Focusable(
        onClick = onPlay,
        modifier = modifier.height(104.px),
        onLongClick = onFavorite,
        radius = 16.px,
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(c, 64.px)
            Spacer(Modifier.width(16.px))
            Column(Modifier.weight(1f)) {
                Text(
                    c.name,
                    style = t.bodyLg,
                    color = if (focused) Color.White else Dim,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.px))
                Text(c.group, style = t.labelMd, color = Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isFav) {
                Spacer(Modifier.width(8.px))
                Badge("FAV", BadgeTone.Accent)
            }
        }
    }
}
