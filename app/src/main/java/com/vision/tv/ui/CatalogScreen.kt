package com.vision.tv.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.tv.AppViewModel
import com.vision.tv.CAT_ALL
import com.vision.tv.CAT_FAVS
import com.vision.tv.CAT_RECENTS
import com.vision.tv.CAT_WATCHLIST
import com.vision.tv.Screen
import com.vision.tv.data.Media
import com.vision.tv.data.MediaKind

private const val ROW_MAX = 30

private fun Media.cat(): String = group.ifBlank { "Otros" }

/**
 * Una sección (TV en vivo, Películas o Series) estilo Netflix: una fila por categoría.
 * "Ver todo" abre la categoría como cuadrícula; al buscar se muestran los resultados en cuadrícula.
 */
@Composable
fun CatalogScreen(vm: AppViewModel, kind: MediaKind) {
    val t = V.type
    val b = vm.browse.getValue(kind)
    val st by b.catalog.collectAsStateWithLifecycle()
    val category by b.category.collectAsStateWithLifecycle()
    val query by b.query.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val watch by vm.watchlist.collectAsStateWithLifecycle()
    val cont by vm.continueItems.collectAsStateWithLifecycle()
    val shelf = rememberShelf(vm)

    LaunchedEffect(kind) { vm.loadCatalog(kind) }

    val title = when (kind) {
        MediaKind.LIVE -> "TV en vivo"
        MediaKind.MOVIE -> "Películas"
        else -> "Series"
    }
    val noun = when (kind) {
        MediaKind.LIVE -> "canales"
        MediaKind.MOVIE -> "películas"
        else -> "series"
    }

    val favItems = remember(favs, kind) { favs.filter { it.kind == kind } }
    val watchItems = remember(watch, kind) { watch.filter { it.kind == kind } }
    val contItems = remember(cont, kind) {
        cont.filter {
            when (kind) {
                MediaKind.MOVIE -> it.media.kind == MediaKind.MOVIE
                MediaKind.SERIES -> it.media.kind == MediaKind.EPISODE
                else -> false
            }
        }
    }
    val groups = remember(st.items) {
        st.items.groupBy { it.cat() }.toList().sortedBy { it.first.lowercase() }
    }

    val searching = query.isNotBlank()
    val openCat = category.ifEmpty { null }
    val gridCat = if (searching) CAT_ALL else openCat
    val gridList: List<Media>? = remember(st.items, favItems, recents, watchItems, gridCat, query) {
        gridCat?.let { c ->
            val base = when (c) {
                CAT_FAVS -> favItems
                CAT_RECENTS -> recents
                CAT_WATCHLIST -> watchItems
                CAT_ALL -> st.items
                else -> st.items.filter { it.cat() == c }
            }
            if (query.isBlank()) base else base.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    // Atrás sale de la cuadrícula o de la búsqueda antes de salir de la sección.
    BackHandler(enabled = searching || openCat != null) {
        vm.setQuery(kind, "")
        vm.setCategory(kind, "")
    }

    val gridFirst = remember { FocusRequester() }
    val rowsFirst = remember { FocusRequester() }
    LaunchedEffect(openCat, st.loading, st.items.isEmpty()) {
        if (!st.loading && st.items.isNotEmpty() && !searching) {
            (if (openCat != null) gridFirst else rowsFirst).requestWhenReady()
        }
    }

    Column(Modifier.fillMaxSize()) {
        NavBar(vm, Screen.Catalog(kind))

        // ── Título, búsqueda y acciones ──
        Row(
            Modifier.fillMaxWidth().padding(start = 64.px, end = 64.px, bottom = 4.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.px),
        ) {
            Text(
                when {
                    searching -> "Resultados"
                    openCat != null -> openCat
                    else -> title
                },
                Modifier.weight(1f, fill = false),
                style = t.headlineLg, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (kind == MediaKind.LIVE && !searching && openCat == null) Badge("EN VIVO", BadgeTone.Live)
            if (!st.loading && st.error == null) {
                Badge("${gridList?.size ?: st.items.size} $noun".uppercase())
            }
            Spacer(Modifier.weight(1f))
            GlassField(
                value = query,
                onChange = { vm.setQuery(kind, it) },
                label = "Buscar en $title",
                modifier = Modifier.width(420.px),
                imeAction = ImeAction.Search,
                compact = true,
            )
            TvButton("Ver todo") { vm.setCategory(kind, CAT_ALL) }
            TvButton("Actualizar") { vm.loadCatalog(kind, force = true) }
        }

        // ── Contenido ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                st.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan, trackColor = Color.White.copy(alpha = 0.10f))
                    Spacer(Modifier.height(16.px))
                    Text("Cargando $title…", style = t.bodyLg, color = Dim)
                    if (kind != MediaKind.LIVE) {
                        Spacer(Modifier.height(4.px))
                        Text("La primera vez puede tardar un poco.", style = t.bodyMd, color = Faint)
                    }
                }
                st.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(st.error ?: "", style = t.bodyLg, color = ErrorTint, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.px))
                    TvButton("Reintentar", primary = true) { vm.loadCatalog(kind, force = true) }
                }
                gridList != null -> {
                    if (gridList.isEmpty()) {
                        Text(
                            when (openCat) {
                                CAT_FAVS -> "Aún no tienes favoritos.\nMantén presionado OK sobre un título para añadirlo."
                                CAT_WATCHLIST -> "Tu lista está vacía.\nAgrega títulos desde su ficha con «Mi lista»."
                                CAT_RECENTS -> "Todavía no has visto canales."
                                else -> "Sin resultados"
                            },
                            Modifier.align(Alignment.Center),
                            style = t.bodyLg, color = Dim, textAlign = TextAlign.Center,
                        )
                    } else if (kind == MediaKind.LIVE) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            state = b.gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 64.px, vertical = 24.px),
                            horizontalArrangement = Arrangement.spacedBy(20.px),
                            verticalArrangement = Arrangement.spacedBy(20.px),
                        ) {
                            itemsIndexed(gridList) { i, c ->
                                ChannelCard(
                                    c = c,
                                    isFav = c.key in shelf.favKeys,
                                    onPlay = { vm.play(gridList, i) },
                                    onFavorite = { vm.toggleFavorite(c) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (i == 0) Modifier.focusRequester(gridFirst) else Modifier),
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            state = b.gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 64.px, vertical = 24.px),
                            horizontalArrangement = Arrangement.spacedBy(16.px),
                            verticalArrangement = Arrangement.spacedBy(16.px),
                        ) {
                            itemsIndexed(gridList) { i, m ->
                                val p = shelf.progress[m.key]
                                PosterCard(
                                    m = m,
                                    fraction = if (p != null && !p.finished) p.fraction else 0f,
                                    isFav = m.key in shelf.favKeys,
                                    inList = m.key in shelf.watchKeys,
                                    onClick = { vm.openDetail(m) },
                                    onLongClick = { vm.toggleFavorite(m) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (i == 0) Modifier.focusRequester(gridFirst) else Modifier),
                                )
                            }
                        }
                    }
                }
                st.items.isEmpty() -> Text(
                    "Esta fuente no trae contenido en esta sección.",
                    Modifier.align(Alignment.Center),
                    style = t.bodyLg, color = Dim, textAlign = TextAlign.Center,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = b.catState,
                    contentPadding = PaddingValues(bottom = 32.px),
                ) {
                    // Filas personales primero; el foco inicial va a la primera que exista.
                    val firstRow = when {
                        kind != MediaKind.LIVE && contItems.isNotEmpty() -> "cont"
                        kind != MediaKind.LIVE && watchItems.isNotEmpty() -> "watch"
                        favItems.isNotEmpty() -> "favs"
                        kind == MediaKind.LIVE && recents.isNotEmpty() -> "recents"
                        else -> groups.firstOrNull()?.first
                    }
                    fun focusFor(id: String): FocusRequester? = if (id == firstRow) rowsFirst else null

                    if (kind != MediaKind.LIVE && contItems.isNotEmpty()) item {
                        MediaRow("Continuar viendo") { continueItems(contItems, shelf, focusFor("cont")) }
                    }
                    if (kind != MediaKind.LIVE && watchItems.isNotEmpty()) item {
                        MediaRow("Mi lista") {
                            posterItems(
                                watchItems.take(ROW_MAX), shelf, focusFor("watch"),
                                more = if (watchItems.size > ROW_MAX) ({ vm.setCategory(kind, CAT_WATCHLIST) }) else null,
                            )
                        }
                    }
                    if (favItems.isNotEmpty()) item {
                        val more = if (favItems.size > ROW_MAX) ({ vm.setCategory(kind, CAT_FAVS) }) else null
                        MediaRow("Favoritos") {
                            if (kind == MediaKind.LIVE) {
                                channelItems(favItems.take(ROW_MAX), favItems, shelf, focusFor("favs"), more)
                            } else {
                                posterItems(favItems.take(ROW_MAX), shelf, focusFor("favs"), more)
                            }
                        }
                    }
                    if (kind == MediaKind.LIVE && recents.isNotEmpty()) item {
                        MediaRow("Vistos recientemente") {
                            channelItems(recents, recents, shelf, focusFor("recents"))
                        }
                    }

                    items(groups.size) { gi ->
                        val (name, list) = groups[gi]
                        val more = if (list.size > ROW_MAX) ({ vm.setCategory(kind, name) }) else null
                        MediaRow(name) {
                            if (kind == MediaKind.LIVE) {
                                channelItems(list.take(ROW_MAX), list, shelf, focusFor(name), more)
                            } else {
                                posterItems(list.take(ROW_MAX), shelf, focusFor(name), more)
                            }
                        }
                    }
                }
            }
        }
        HintBar()
    }
}
