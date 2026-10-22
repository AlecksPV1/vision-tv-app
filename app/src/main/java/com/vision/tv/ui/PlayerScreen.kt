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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vision.tv.AppViewModel
import com.vision.tv.data.UA
import kotlinx.coroutines.delay

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
    var error by remember { mutableStateOf<String?>(null) }
    var triedTs by remember { mutableStateOf(false) }
    val currentIndex by rememberUpdatedState(index)
    val focus = remember { FocusRequester() }

    val channel = queue[index]
    val isFav = favs.any { it.key == channel.key }

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
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
                    error = "No se pudo reproducir este canal.\nPuede que tu proveedor haya alcanzado el límite de conexiones."
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { exo.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (exo.mediaItemCount > 0) exo.play() }

    LaunchedEffect(index) {
        triedTs = false
        error = null
        exo.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        exo.prepare()
        exo.playWhenReady = true
        vm.addRecent(channel)
        infoTick++
    }

    // El HUD se oculta solo a los 4 segundos sin actividad.
    LaunchedEffect(infoTick) {
        info = true
        delay(4000)
        info = false
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

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
                        index = (index + 1) % queue.size
                        true
                    }
                    Key.DirectionUp, Key.ChannelDown -> {
                        index = (index - 1 + queue.size) % queue.size
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        infoTick++
                        true
                    }
                    Key.Menu -> {
                        vm.toggleFavorite(channel)
                        infoTick++
                        true
                    }
                    Key.MediaPlayPause -> {
                        if (exo.isPlaying) exo.pause() else exo.play()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> true
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
            visible = info,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.px),
            enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(240)) + slideOutVertically(tween(240)) { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassSurface(
                    Modifier.widthIn(max = 1200.px),
                    radius = Pill,
                    brush = SolidColor(Layer1.copy(alpha = 0.80f)),
                    rim = 0.20f,
                ) {
                    Row(
                        Modifier.padding(start = 20.px, end = 32.px, top = 16.px, bottom = 16.px),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelLogo(channel, 64.px)
                        Spacer(Modifier.width(20.px))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                channel.name,
                                style = t.headlineSm, color = Color.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                channel.group,
                                style = t.bodyMd, color = Dim,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(28.px))
                        Badge("EN VIVO", BadgeTone.Live)
                        if (isFav) {
                            Spacer(Modifier.width(10.px))
                            Badge("FAVORITO", BadgeTone.Accent)
                        }
                    }
                }
                Spacer(Modifier.height(16.px))
                Row {
                    HintChip("ARRIBA / ABAJO", "Cambiar canal")
                    Spacer(Modifier.width(32.px))
                    HintChip("MENÚ", "Favorito")
                    Spacer(Modifier.width(32.px))
                    HintChip("ATRÁS", "Volver a la lista")
                }
            }
        }
    }
}
