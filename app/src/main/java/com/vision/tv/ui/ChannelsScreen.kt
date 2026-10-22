package com.vision.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vision.tv.AppViewModel
import com.vision.tv.CAT_ALL
import com.vision.tv.CAT_FAVS
import com.vision.tv.CAT_RECENTS
import com.vision.tv.Screen
import com.vision.tv.data.Channel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChannelsScreen(vm: AppViewModel) {
    val t = V.type
    val st by vm.channels.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val source by vm.activeSource.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    val favKeys = remember(favs) { favs.map { it.key }.toHashSet() }
    val counts = remember(st.channels) { st.channels.groupingBy { it.group }.eachCount() }
    val categories = remember(counts) { listOf(CAT_FAVS, CAT_RECENTS, CAT_ALL) + counts.keys.sorted() }
    val shown = remember(st.channels, favs, recents, category, query) {
        val base = when (category) {
            CAT_FAVS -> favs
            CAT_RECENTS -> recents
            CAT_ALL -> st.channels
            else -> st.channels.filter { it.group == category }
        }
        if (query.isBlank()) base else base.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 64.px, vertical = 40.px)) {

        // ── Cápsula de navegación flotante ──
        GlassSurface(
            Modifier.fillMaxWidth(),
            radius = Pill,
            brush = SolidColor(Layer1.copy(alpha = 0.75f)),
            rim = 0.12f,
        ) {
            Row(
                Modifier.padding(horizontal = 16.px, vertical = 10.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.px),
            ) {
                VisionMark(44.px)
                Focusable(
                    onClick = { vm.switchProfile() },
                    radius = Pill,
                    fill = 0.08f,
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        Modifier.padding(start = 8.px, end = 22.px, top = 6.px, bottom = 6.px),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(profile?.avatar ?: 0, profile?.name?.take(1)?.uppercase() ?: "?", 36.px)
                        Spacer(Modifier.width(12.px))
                        Text(profile?.name ?: "", style = t.labelLg, maxLines = 1)
                    }
                }
                Focusable(
                    onClick = { vm.push(Screen.Sources) },
                    radius = Pill,
                    fill = 0.08f,
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        source?.name ?: "Fuentes",
                        Modifier.widthIn(max = 260.px).padding(horizontal = 24.px, vertical = 14.px),
                        style = t.labelLg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassField(
                    value = query,
                    onChange = { vm.setQuery(it) },
                    label = "Buscar canal",
                    modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Search,
                    compact = true,
                )
                TvButton("Actualizar") { vm.loadChannels() }
            }
        }

        // ── Estado de la cuenta ──
        Row(
            Modifier.padding(top = 20.px, start = 8.px),
            horizontalArrangement = Arrangement.spacedBy(12.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge("EN VIVO", BadgeTone.Live)
            if (!st.loading && st.error == null) Badge("${shown.size} CANALES")
            st.account?.let { a ->
                val max = if (a.maxConnections > 0) a.maxConnections.toString() else "?"
                val full = a.maxConnections > 0 && a.activeConnections >= a.maxConnections
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
        Spacer(Modifier.height(12.px))

        // ── Categorías + canales ──
        Row(Modifier.weight(1f)) {
            GlassSurface(Modifier.width(300.px).fillMaxHeight(), radius = 24.px, fill = 0.05f, rim = 0.12f) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = vm.catState,
                    verticalArrangement = Arrangement.spacedBy(6.px),
                    contentPadding = PaddingValues(14.px),
                ) {
                    items(categories) { c ->
                        val selected = c == category
                        val n = when (c) {
                            CAT_FAVS -> favs.size
                            CAT_RECENTS -> recents.size
                            CAT_ALL -> st.channels.size
                            else -> counts[c] ?: 0
                        }
                        Focusable(
                            onClick = { vm.setCategory(c) },
                            modifier = Modifier.fillMaxWidth(),
                            radius = 14.px,
                            fill = if (selected) 0.14f else 0f,
                            rim = if (selected) 0.55f else 0f,
                            rimColor = Cyan,
                            scale = 0.03f,
                            contentAlignment = Alignment.CenterStart,
                        ) { focused ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.px, vertical = 12.px),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    c,
                                    Modifier.weight(1f),
                                    style = t.labelLg,
                                    color = if (selected || focused) Color.White else Dim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.px))
                                Text(n.toString(), style = t.labelMd, color = Faint)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(24.px))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    st.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Cyan, trackColor = Color.White.copy(alpha = 0.10f))
                        Spacer(Modifier.height(16.px))
                        Text("Cargando canales…", style = t.bodyLg, color = Dim)
                    }
                    st.error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(st.error ?: "", style = t.bodyLg, color = ErrorTint, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.px))
                        TvButton("Reintentar", primary = true) { vm.loadChannels() }
                    }
                    shown.isEmpty() -> Text(
                        if (category == CAT_FAVS) "Aún no tienes favoritos.\nMantén presionado OK sobre un canal para añadirlo."
                        else "Sin resultados",
                        Modifier.align(Alignment.Center),
                        style = t.bodyLg, color = Dim, textAlign = TextAlign.Center,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = vm.gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(28.px),
                        horizontalArrangement = Arrangement.spacedBy(24.px),
                        verticalArrangement = Arrangement.spacedBy(24.px),
                    ) {
                        itemsIndexed(shown) { i, c ->
                            ChannelTile(c, c.key in favKeys, onPlay = { vm.play(shown, i) }, onFavorite = { vm.toggleFavorite(c) })
                        }
                    }
                }
            }
        }

        Row(
            Modifier.padding(top = 12.px, start = 8.px),
            horizontalArrangement = Arrangement.spacedBy(32.px),
        ) {
            HintChip("OK", "Reproducir")
            HintChip("MANTÉN OK", "Favorito")
            HintChip("ATRÁS", "Volver")
        }
    }
}

@Composable
private fun ChannelTile(c: Channel, isFav: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    val t = V.type
    Focusable(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().height(104.px),
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

/** Logo del canal sobre un cuadro de vidrio; si no hay logo, muestra la inicial. */
@Composable
fun ChannelLogo(c: Channel, dimension: Dp) {
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
