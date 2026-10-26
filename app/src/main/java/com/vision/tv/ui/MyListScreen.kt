package com.vision.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.tv.AppViewModel
import com.vision.tv.Screen
import com.vision.tv.data.MediaKind

/** "Mi lista" y favoritos del perfil, separados por tipo. */
@Composable
fun MyListScreen(vm: AppViewModel) {
    val t = V.type
    val watch by vm.watchlist.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val shelf = rememberShelf(vm)

    val favMovies = remember(favs) { favs.filter { it.kind == MediaKind.MOVIE } }
    val favSeries = remember(favs) { favs.filter { it.kind == MediaKind.SERIES } }
    val favLive = remember(favs) { favs.filter { it.kind == MediaKind.LIVE } }
    val empty = watch.isEmpty() && favs.isEmpty()

    val first = remember { FocusRequester() }
    LaunchedEffect(empty) { if (!empty) first.requestWhenReady() }

    Column(Modifier.fillMaxSize()) {
        NavBar(vm, Screen.MyList)

        if (empty) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Tu lista está vacía.\nAgrega títulos con «Mi lista» en su ficha o mantén OK sobre uno para marcarlo favorito.",
                    style = t.bodyLg, color = Dim, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.px)) {
                val firstRow = when {
                    watch.isNotEmpty() -> "watch"
                    favMovies.isNotEmpty() -> "movies"
                    favSeries.isNotEmpty() -> "series"
                    else -> "live"
                }
                fun focusFor(id: String): FocusRequester? = if (id == firstRow) first else null

                if (watch.isNotEmpty()) item { MediaRow("Mi lista") { posterItems(watch, shelf, focusFor("watch")) } }
                if (favMovies.isNotEmpty()) item { MediaRow("Películas favoritas") { posterItems(favMovies, shelf, focusFor("movies")) } }
                if (favSeries.isNotEmpty()) item { MediaRow("Series favoritas") { posterItems(favSeries, shelf, focusFor("series")) } }
                if (favLive.isNotEmpty()) item { MediaRow("Canales favoritos") { channelItems(favLive, favLive, shelf, focusFor("live")) } }
            }
        }
        HintBar()
    }
}
