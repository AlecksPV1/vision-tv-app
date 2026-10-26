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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vision.tv.AppViewModel
import com.vision.tv.data.Media
import com.vision.tv.data.MediaKind

/** Ficha de una película o serie: sinopsis, acciones (continuar, mi lista, favorito) y episodios. */
@Composable
fun DetailScreen(vm: AppViewModel, media: Media) {
    val t = V.type
    val d by vm.detail.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val watch by vm.watchlist.collectAsStateWithLifecycle()
    val progress by vm.progressMap.collectAsStateWithLifecycle()

    val isSeries = media.kind == MediaKind.SERIES
    val isFav = favs.any { it.key == media.key }
    val inList = watch.any { it.key == media.key }

    // Todos los episodios en orden (temporada 1, 2, …) para reproducir de corrido.
    val allEps = remember(d) { d.seasons.flatMap { d.episodes[it].orEmpty() } }
    val target = remember(allEps, progress) { vm.episodeTarget(allEps) }
    var picked by remember { mutableIntStateOf(-1) }
    val season = if (picked in d.seasons) picked else (allEps.getOrNull(target.first)?.season ?: d.seasons.firstOrNull() ?: 0)
    val seasonEps = d.episodes[season].orEmpty()

    val ready = !d.loading && d.error == null
    val primary = remember { FocusRequester() }
    LaunchedEffect(ready, isSeries) {
        if (!isSeries || ready) runCatching { primary.requestFocus() }
    }

    val year = d.year.ifBlank { media.year }
    val rating = d.rating.ifBlank { media.rating }.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 }

    Column(Modifier.fillMaxSize().padding(horizontal = 64.px, vertical = 40.px)) {

        Row(Modifier.fillMaxWidth()) {
            // ── Póster ──
            Box(
                Modifier
                    .width(200.px)
                    .height(300.px)
                    .clip(RoundedCornerShape(16.px))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                val image = d.cover ?: media.logo
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        media.name.take(1).uppercase(),
                        Modifier.align(Alignment.Center),
                        style = t.displayHero, color = Dim,
                    )
                }
            }
            Spacer(Modifier.width(40.px))

            // ── Datos y acciones ──
            Column(Modifier.weight(1f)) {
                Text(
                    media.name,
                    style = t.headlineLg, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.px))
                Row(horizontalArrangement = Arrangement.spacedBy(10.px), verticalAlignment = Alignment.CenterVertically) {
                    Badge(if (isSeries) "SERIE" else "PELÍCULA", BadgeTone.Accent)
                    if (year.isNotBlank()) Badge(year)
                    rating?.let { Badge("NOTA $it") }
                    if (d.durationSec > 0) Badge(minutesText(d.durationSec).uppercase())
                    if (isSeries && d.seasons.isNotEmpty()) {
                        Badge("${d.seasons.size} TEMPORADA" + if (d.seasons.size > 1) "S" else "")
                    }
                    if (isFav) Badge("FAVORITO", BadgeTone.Accent)
                    if (inList) Badge("EN MI LISTA")
                }
                if (d.genre.isNotBlank()) {
                    Spacer(Modifier.height(8.px))
                    Text(d.genre, style = t.bodyMd, color = Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.px))
                Text(
                    when {
                        d.loading -> "Cargando ficha…"
                        d.error != null -> d.error ?: ""
                        d.plot.isNotBlank() -> d.plot
                        else -> "Sin sinopsis disponible."
                    },
                    style = t.bodyLg,
                    color = if (d.error != null) ErrorTint else Dim,
                    maxLines = if (isSeries) 3 else 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.px))

                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    if (d.error != null) {
                        TvButton("Reintentar", Modifier.focusRequester(primary), primary = true) { vm.loadDetail(media) }
                    } else if (isSeries) {
                        if (ready && allEps.isNotEmpty()) {
                            val (idx, resume) = target
                            val ep = allEps[idx]
                            val ref = "T${ep.season} · E${ep.episode}"
                            val seen = allEps.any { progress.containsKey(it.key) }
                            val lastDone = progress[ep.key]?.finished == true
                            val label = when {
                                resume -> "Continuar $ref"
                                lastDone -> "Ver de nuevo $ref"
                                seen -> "Siguiente: $ref"
                                else -> "Comenzar $ref"
                            }
                            TvButton(label, Modifier.focusRequester(primary), primary = true) { vm.play(allEps, idx) }
                        }
                    } else {
                        val p = progress[media.key]
                        val resumable = p != null && !p.finished && p.positionMs >= 10_000
                        TvButton(
                            if (resumable) "Reanudar · ${clock(p!!.positionMs)}" else "Ver película",
                            Modifier.focusRequester(primary),
                            primary = true,
                        ) { vm.playMovie(media) }
                        if (resumable) TvButton("Desde el inicio") { vm.playMovie(media, fromStart = true) }
                    }
                    TvButton(if (inList) "Quitar de mi lista" else "Mi lista", selected = inList) { vm.toggleWatchlist(media) }
                    TvButton(if (isFav) "Quitar favorito" else "Favorito", selected = isFav) { vm.toggleFavorite(media) }
                    TvButton("Volver") { vm.pop() }
                }
            }
        }

        // ── Temporadas y episodios ──
        if (isSeries && ready && d.seasons.isNotEmpty()) {
            Spacer(Modifier.height(24.px))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.px, vertical = 12.px),
                horizontalArrangement = Arrangement.spacedBy(12.px),
            ) {
                items(d.seasons) { s ->
                    TvButton("Temporada $s", selected = s == season) { picked = s }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.px, vertical = 12.px),
                verticalArrangement = Arrangement.spacedBy(10.px),
            ) {
                items(seasonEps, key = { it.key }) { e ->
                    val p = progress[e.key]
                    Focusable(
                        onClick = { vm.play(allEps, allEps.indexOfFirst { it.key == e.key }.coerceAtLeast(0)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.px),
                        radius = 16.px,
                        scale = 0.02f,
                        contentAlignment = Alignment.CenterStart,
                    ) { focused ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.px, vertical = 14.px),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("E${e.episode}", Modifier.width(80.px), style = t.headlineSm, color = Cyan)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.name,
                                    style = t.bodyXl,
                                    color = if (focused) Color.White else Dim,
                                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (p != null && !p.finished) {
                                    Spacer(Modifier.height(8.px))
                                    ProgressBar(p.fraction, Modifier.fillMaxWidth(0.5f))
                                }
                            }
                            Spacer(Modifier.width(16.px))
                            when {
                                p?.finished == true -> Badge("VISTO")
                                p != null -> Text(remainingText(p.positionMs, p.durationMs), style = t.labelMd, color = Faint)
                                e.durationSec > 0 -> Text(minutesText(e.durationSec), style = t.labelMd, color = Faint)
                            }
                        }
                    }
                }
            }
        }
    }
}
