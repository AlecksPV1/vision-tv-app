package com.vision.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vision.tv.AppViewModel
import com.vision.tv.Screen
import com.vision.tv.data.Media
import com.vision.tv.data.MediaKind
import com.vision.tv.data.Progress

private const val SHELF_MAX = 30

/** Inicio estilo Netflix: banner destacado y filas (continuar viendo, mi lista, favoritos, populares…). */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val source by vm.activeSource.collectAsStateWithLifecycle()
    val cont by vm.continueItems.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val watch by vm.watchlist.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val movies by vm.browse.getValue(MediaKind.MOVIE).catalog.collectAsStateWithLifecycle()
    val series by vm.browse.getValue(MediaKind.SERIES).catalog.collectAsStateWithLifecycle()
    val shelf = rememberShelf(vm)

    // Películas y series se bajan en segundo plano para llenar las filas.
    LaunchedEffect(source?.id) { vm.preloadShelves() }

    val favTitles = remember(favs) { favs.filter { it.kind != MediaKind.LIVE } }
    val topMovies = remember(movies.items) { movies.items.top() }
    val topSeries = remember(series.items) { series.items.top() }
    val resume = remember(cont) { cont.filter { !it.finished } }
    val featured: Progress? = resume.firstOrNull()
    val fallback: Media? = if (featured == null) topMovies.firstOrNull() else null

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(featured == null && fallback == null) { firstFocus.requestWhenReady() }

    Column(Modifier.fillMaxSize()) {
        NavBar(vm, Screen.Home)

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.px),
        ) {
            // ── Banner ──
            item {
                when {
                    featured != null -> {
                        val m = featured.media
                        val isEp = m.kind == MediaKind.EPISODE
                        HeroBanner(
                            kicker = "CONTINUAR VIENDO",
                            title = if (isEp) m.seriesName else m.name,
                            subtitle = if (isEp) "T${m.season} · E${m.episode} · ${m.name}" else m.year,
                            image = m.logo,
                            primary = "Continuar",
                            onPrimary = { shelf.open(featured) },
                            secondary = "Más información",
                            onSecondary = { vm.openDetail(if (isEp) vm.seriesOf(m) else m) },
                            focus = firstFocus,
                        )
                    }
                    fallback != null -> HeroBanner(
                        kicker = "DESTACADA",
                        title = fallback.name,
                        subtitle = listOf(
                            fallback.year,
                            fallback.rating.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 }?.let { "Nota $it" } ?: "",
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        image = fallback.logo,
                        primary = "Ver ficha",
                        onPrimary = { vm.openDetail(fallback) },
                        secondary = null,
                        onSecondary = {},
                        focus = firstFocus,
                    )
                    else -> HeroBanner(
                        kicker = "BIENVENIDO",
                        title = "Hola, ${profile?.name ?: ""}",
                        subtitle = "¿Qué quieres ver hoy?",
                        image = null,
                        primary = null,
                        onPrimary = {},
                        secondary = null,
                        onSecondary = {},
                        focus = null,
                    )
                }
            }

            // El foco inicial va al banner; si no hay botón, a la primera fila que exista.
            val rowIds = listOf(
                "cont" to (resume.size > 1), "watch" to watch.isNotEmpty(), "favs" to favTitles.isNotEmpty(),
                "recents" to recents.isNotEmpty(), "movies" to topMovies.isNotEmpty(), "series" to topSeries.isNotEmpty(),
            )
            val firstRow = if (featured == null && fallback == null) rowIds.firstOrNull { it.second }?.first else null
            fun focusFor(id: String): FocusRequester? = if (id == firstRow) firstFocus else null

            if (resume.size > 1) item {
                MediaRow("Continuar viendo") { continueItems(resume, shelf, focusFor("cont")) }
            }
            if (watch.isNotEmpty()) item {
                MediaRow("Mi lista") { posterItems(watch.take(SHELF_MAX), shelf, focusFor("watch")) }
            }
            if (favTitles.isNotEmpty()) item {
                MediaRow("Tus favoritos") { posterItems(favTitles.take(SHELF_MAX), shelf, focusFor("favs")) }
            }
            if (recents.isNotEmpty()) item {
                MediaRow("Canales recientes") { channelItems(recents, recents, shelf, focusFor("recents")) }
            }
            if (topMovies.isNotEmpty()) item {
                MediaRow("Películas populares") {
                    posterItems(topMovies, shelf, focusFor("movies")) { vm.switchTab(Screen.Catalog(MediaKind.MOVIE)) }
                }
            }
            if (topSeries.isNotEmpty()) item {
                MediaRow("Series populares") {
                    posterItems(topSeries, shelf, focusFor("series")) { vm.switchTab(Screen.Catalog(MediaKind.SERIES)) }
                }
            }
            if (movies.loading || series.loading) item {
                Text(
                    "Cargando películas y series…",
                    Modifier.padding(horizontal = 64.px, vertical = 16.px),
                    style = V.type.bodyLg, color = Faint,
                )
            }
        }
        HintBar()
    }
}

/** Las mejor calificadas (o las primeras si el proveedor no trae notas). */
private fun List<Media>.top(): List<Media> =
    sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(SHELF_MAX)

@Composable
private fun HeroBanner(
    kicker: String,
    title: String,
    subtitle: String,
    image: String?,
    primary: String?,
    onPrimary: () -> Unit,
    secondary: String?,
    onSecondary: () -> Unit,
    focus: FocusRequester?,
) {
    val t = V.type
    Box(Modifier.fillMaxWidth().height(380.px)) {
        // Póster a la derecha, difuminado hacia el fondo.
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 120.px)
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .alpha(0.55f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Obsidian, Obsidian.copy(alpha = 0.85f), Color.Transparent),
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Obsidian)))
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 64.px).width(820.px),
        ) {
            Badge(kicker, BadgeTone.Accent)
            Spacer(Modifier.height(14.px))
            Text(title, style = t.headlineXl, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(6.px))
                Text(subtitle, style = t.bodyXl, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (primary != null) {
                Spacer(Modifier.height(28.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton(
                        primary,
                        modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier,
                        primary = true,
                        onClick = onPrimary,
                    )
                    if (secondary != null) TvButton(secondary, onClick = onSecondary)
                }
            }
        }
    }
}
