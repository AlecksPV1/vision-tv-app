@file:OptIn(UnstableApi::class)

package com.vision.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vision.tv.AppViewModel
import com.vision.tv.data.Media
import com.vision.tv.data.MediaKind
import com.vision.tv.data.Net
import com.vision.tv.data.UA
import kotlinx.coroutines.delay

private const val SEEK_MS = 10_000L
private const val BIG_SEEK_MS = 30_000L

/**
 * Reproductor para TV en vivo, películas y episodios.
 * - En vivo: Arriba/Abajo cambian de canal.
 * - Películas y series: reanuda donde te quedaste, guarda el avance cada 10 s y, al terminar un
 *   episodio, pasa al siguiente.
 */
@Composable
fun PlayerScreen(vm: AppViewModel, startIndex: Int) {
    val queue = vm.queue
    if (queue.isEmpty()) {
        LaunchedEffect(Unit) { vm.pop() }
        return
    }

    val t = V.type
    val ctx = LocalContext.current
    val favs by vm.favorites.collectAsStateWithLifecycle()

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, queue.lastIndex)) }
    var infoTick by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var userPaused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var triedTs by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    val currentIndex by rememberUpdatedState(index)
    val focus = remember { FocusRequester() }

    val channel = queue[index]
    val currentChannel by rememberUpdatedState(channel)
    val isLive = channel.kind == MediaKind.LIVE
    val isEpisode = channel.kind == MediaKind.EPISODE
    val isFav = favs.any { it.key == channel.key }

    val exo = remember {
        // Mismo cliente (y DNS de respaldo) que la API, para que el video también se resuelva.
        val http = OkHttpDataSource.Factory(Net.client).setUserAgent(UA)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }

    /** Guarda dónde va la película o el episodio (no hace nada en TV en vivo). */
    fun saveFor(c: Media) {
        if (c.kind == MediaKind.LIVE) return
        val dur = exo.duration
        val pos = exo.currentPosition
        if (dur > 0 && pos > 0) vm.saveProgress(c, pos, dur)
    }

    fun step(delta: Int) {
        if (isLive) {
            index = (index + delta).mod(queue.size)
        } else {
            val next = index + delta
            if (next in 0..queue.lastIndex) index = next
        }
    }

    fun seekBy(delta: Long) {
        val dur = exo.duration
        val target = exo.currentPosition + delta
        exo.seekTo(if (dur > 0) target.coerceIn(0L, dur) else target.coerceAtLeast(0L))
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
                if (playbackState == Player.STATE_ENDED) {
                    val c = queue[currentIndex]
                    if (c.kind != MediaKind.LIVE) {
                        val dur = exo.duration
                        if (dur > 0) vm.saveProgress(c, dur, dur) // queda marcado como visto
                        if (c.kind == MediaKind.EPISODE && currentIndex < queue.lastIndex) {
                            index = currentIndex + 1
                        } else {
                            vm.pop()
                        }
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                val url = queue[currentIndex].streamUrl
                if (!triedTs && url.endsWith(".m3u8")) {
                    // Algunos servidores Xtream solo sirven el canal en .ts
                    triedTs = true
                    exo.setMediaItem(MediaItem.fromUri(url.removeSuffix(".m3u8") + ".ts"))
                    exo.prepare()
                    exo.play()
                } else {
                    error = "No se pudo reproducir.\nPuede que tu proveedor haya alcanzado el límite de conexiones."
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            saveFor(currentChannel)
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Al cambiar de episodio, guarda el que se deja (el reproductor aún tiene el anterior).
    DisposableEffect(index) {
        val leaving = channel
        onDispose { saveFor(leaving) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        saveFor(currentChannel)
        exo.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (exo.mediaItemCount > 0 && !userPaused) exo.play()
    }

    LaunchedEffect(index) {
        triedTs = false
        error = null
        userPaused = false
        val c = channel
        val fromStart = first && vm.fromStart
        first = false
        val start = if (c.kind == MediaKind.LIVE || fromStart) 0L else vm.resumePosition(c)
        exo.setMediaItem(MediaItem.fromUri(c.streamUrl), start)
        exo.prepare()
        exo.playWhenReady = true
        vm.addRecent(c)
        infoTick++
    }

    // Guarda el avance cada 10 segundos mientras ves una película o un episodio.
    LaunchedEffect(index) {
        if (channel.kind != MediaKind.LIVE) {
            while (true) {
                delay(10_000)
                if (exo.isPlaying) saveFor(channel)
            }
        }
    }

    // Posición y duración para la barra de avance.
    LaunchedEffect(index) {
        while (!isLive) {
            posMs = exo.currentPosition.coerceAtLeast(0L)
            durMs = exo.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // El panel se oculta solo a los 4 segundos sin actividad (y se queda si pausaste).
    LaunchedEffect(infoTick) {
        info = true
        delay(4000)
        info = false
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun togglePause() {
        if (exo.isPlaying || exo.playWhenReady) {
            exo.pause()
            userPaused = true
        } else {
            exo.play()
            userPaused = false
        }
        infoTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionDown, Key.ChannelUp -> {
                        if (isLive) step(1) else infoTick++
                        true
                    }
                    Key.DirectionUp, Key.ChannelDown -> {
                        if (isLive) step(-1) else infoTick++
                        true
                    }
                    Key.DirectionRight -> {
                        if (!isLive) {
                            seekBy(SEEK_MS)
                            infoTick++
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!isLive) {
                            seekBy(-SEEK_MS)
                            infoTick++
                        }
                        true
                    }
                    Key.MediaFastForward -> {
                        if (!isLive) {
                            seekBy(BIG_SEEK_MS)
                            infoTick++
                        }
                        true
                    }
                    Key.MediaRewind -> {
                        if (!isLive) {
                            seekBy(-BIG_SEEK_MS)
                            infoTick++
                        }
                        true
                    }
                    Key.MediaNext -> {
                        step(1)
                        true
                    }
                    Key.MediaPrevious -> {
                        step(-1)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (isLive) infoTick++ else togglePause()
                        true
                    }
                    Key.MediaPlayPause -> {
                        togglePause()
                        true
                    }
                    Key.MediaPlay -> {
                        if (!exo.playWhenReady) togglePause()
                        true
                    }
                    Key.MediaPause -> {
                        if (exo.playWhenReady) togglePause()
                        true
                    }
                    Key.Menu -> {
                        if (!isEpisode) vm.toggleFavorite(channel)
                        infoTick++
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = false
                    player = exo
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = Cyan,
                trackColor = Color.White.copy(alpha = 0.10f),
            )
        }

        error?.let {
            GlassSurface(
                Modifier.align(Alignment.Center).widthIn(max = 760.px),
                radius = 24.px,
                brush = SolidColor(Layer1.copy(alpha = 0.85f)),
                rim = 0.20f,
            ) {
                Text(
                    it,
                    Modifier.padding(horizontal = 40.px, vertical = 32.px),
                    style = t.bodyXl, color = ErrorTint, textAlign = TextAlign.Center,
                )
            }
        }

        // ── HUD flotante de vidrio ──
        AnimatedVisibility(
            visible = info || userPaused,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.px),
            enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(240)) + slideOutVertically(tween(240)) { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassSurface(
                    Modifier.widthIn(max = 1200.px),
                    radius = if (isLive) Pill else 32.px,
                    brush = SolidColor(Layer1.copy(alpha = 0.80f)),
                    rim = 0.20f,
                ) {
                    Column(Modifier.padding(start = 20.px, end = 32.px, top = 16.px, bottom = 16.px)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(channel, 64.px)
                            Spacer(Modifier.width(20.px))
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(
                                    if (isEpisode) channel.seriesName else channel.name,
                                    style = t.headlineSm, color = Color.White,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    when {
                                        isEpisode -> "T${channel.season} · E${channel.episode} · ${channel.name}"
                                        else -> channel.group
                                    },
                                    style = t.bodyMd, color = Dim,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(28.px))
                            if (isLive) Badge("EN VIVO", BadgeTone.Live)
                            if (userPaused) Badge("EN PAUSA")
                            if (isFav) {
                                Spacer(Modifier.width(10.px))
                                Badge("FAVORITO", BadgeTone.Accent)
                            }
                        }
                        if (!isLive) {
                            Spacer(Modifier.height(16.px))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(clock(posMs), style = t.labelLg, color = Color.White)
                                Spacer(Modifier.width(16.px))
                                ProgressBar(
                                    if (durMs > 0) posMs.toFloat() / durMs else 0f,
                                    Modifier.weight(1f),
                                    height = 8.px,
                                )
                                Spacer(Modifier.width(16.px))
                                Text(clock(durMs), style = t.labelLg, color = Dim)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.px))
                Row {
                    if (isLive) {
                        HintChip("ARRIBA / ABAJO", "Cambiar canal")
                        Spacer(Modifier.width(32.px))
                        HintChip("MENÚ", "Favorito")
                    } else {
                        HintChip("IZQ / DER", "Retroceder / adelantar 10 s")
                        Spacer(Modifier.width(32.px))
                        HintChip("OK", "Pausa")
                        if (isEpisode && index < queue.lastIndex) {
                            Spacer(Modifier.width(32.px))
                            HintChip("SIGUIENTE", "Próximo episodio")
                        }
                    }
                    Spacer(Modifier.width(32.px))
                    HintChip("ATRÁS", "Volver")
                }
            }
        }
    }
}
