package app.nova.player.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.nova.player.AppViewModel
import app.nova.player.PlayRequest
import app.nova.player.data.Http
import app.nova.player.ui.theme.NovaColors
import app.nova.player.ui.theme.NovaFonts
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, request: PlayRequest) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var showOverlay by remember { mutableStateOf(true) }
    val retries = remember { intArrayOf(0) }
    // URL réellement lue : un flux live .ts refusé est retenté en .m3u8 (HLS), format accepté par certains panels seulement.
    var url by remember(request.url) { mutableStateOf(request.url) }

    val player = remember {
        val dataSource = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.USER_AGENT)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            // Démarrage rapide pour le zapping, tampon confortable ensuite.
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_000, 2_500).build())
            .build()
            .apply { playWhenReady = true }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { error = null; retries[0] = 0 }
            }

            override fun onPlayerError(e: PlaybackException) {
                // Flux live : on relance automatiquement quelques fois avant d'afficher l'erreur.
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                if (retries[0] < 2) {
                    retries[0]++
                    player.prepare()
                    return
                }
                if (url.endsWith(".ts") && "/live/" in url) {
                    url = url.removeSuffix(".ts") + ".m3u8"
                    return
                }
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Flux refusé par le serveur (abonnement expiré ou connexions max atteintes)."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connexion au flux impossible."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Format non pris en charge par cet appareil."
                    else -> "Lecture impossible (${e.errorCodeName})."
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    // Nouvelle URL (premier lancement ou zapping)
    LaunchedEffect(url) {
        error = null
        retries[0] = 0
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (url == request.url) overlayTick++
    }

    // Bandeau d'info : visible 4 s après chaque changement
    LaunchedEffect(overlayTick) {
        showOverlay = true
        delay(4_000)
        showOverlay = false
    }

    BackHandler { vm.closePlayer() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (request.live) Modifier
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key.nativeKeyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(request, -1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(request, +1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_INFO -> { overlayTick++; true }
                            else -> false
                        }
                    }
                else Modifier,
            ),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = !request.live
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    if (!request.live) { controllerShowTimeoutMs = 3_000; requestFocus() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            BasicText("Chargement…", style = BodyStyle.copy(color = NovaColors.Jade), modifier = Modifier.align(Alignment.Center))
        }

        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBanner(it)
                if (request.live) {
                    Spacer(Modifier.height(10.dp))
                    BasicText("▲ ▼ pour changer de chaîne · Retour pour quitter", style = MutedStyle)
                }
            }
        }

        // Tactile (téléphone) : glisser vers le haut/bas pour zapper, toucher pour les infos.
        if (request.live) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(request.url) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -120f) vm.zap(request, +1) else if (total > 120f) vm.zap(request, -1)
                            },
                        ) { _, dy -> total += dy }
                    }
                    .pointerInput(Unit) { detectTapGestures { overlayTick++ } },
            )
        }

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(40.dp),
        ) {
            Row(
                Modifier
                    .background(Color(0xCC050B1C), RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (request.live && request.playlist.isNotEmpty()) {
                    BasicText(
                        "${request.index + 1}",
                        style = BodyStyle.copy(fontFamily = NovaFonts.Display, fontSize = 26.sp, color = NovaColors.Jade, fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    BasicText(request.title, style = BodyStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                    if (request.live) BasicText("▲ ▼ ou glisser pour zapper · OK infos", style = MutedStyle)
                }
            }
        }
    }
}
