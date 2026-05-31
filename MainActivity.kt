package com.example.kinsfolktv

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed


// Note: 'Channel' and 'M3uParser' definitions have been removed from this file
// to resolve the duplicate "Redeclaration" compiler errors.

class MainActivity : ComponentActivity() {

    private val playlistUrl = "https://raw.githubusercontent.com/hjacked/iptv/refs/heads/main/hjedv1.m3u"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            var showSplash by remember { mutableStateOf(true) }

            if (showSplash) {
                SplashScreen {
                    showSplash = false
                }
            } else {
                KinsFolkTVApp(playlistUrl, this)
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        // Runs smooth fade/zoom intro vectors concurrently inside the coroutine lifecycle scope
        launch { alphaAnim.animateTo(1f, animationSpec = tween(800)) }
        launch { scaleAnim.animateTo(1f, animationSpec = tween(800)) }

        val duration = 3500
        val steps = 100
        repeat(steps) {
            progress = (it + 1) / steps.toFloat()
            delay(duration / steps.toLong())
        }
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Pitch-black cinematic background space profile
        contentAlignment = Alignment.Center
    ) {
        // Column naturally handles safe top-to-bottom layout alignment placement
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "KinsFolkTV",
                fontSize = 42.sp, // Bold cinematic application title presentation display
                color = Color.White
            )

            // Provides absolute structured vertical spacing directly between your text title and loader
            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.4f) // Limits tracking density width footprint cleanly on wide TV screens
                    .height(4.dp),
                color = Color.White, // Retains your custom gold tracking color accent channel line tint
                trackColor = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "brought to you by hjed",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}


@OptIn(UnstableApi::class)
@Composable
fun KinsFolkTVApp(playlistUrl: String, context: Context) {
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    val sharedPrefs = context.getSharedPreferences("kinsfolk_prefs", Context.MODE_PRIVATE)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // CRITICAL TV FIX 1: LazyListState added to monitor and control list scrolling position programmatically
    val listState = rememberLazyListState()

    val fallbackM3u = """
        #EXTM3U
        #EXTINF:-1 tvg-name="NASA TV",NASA TV
        https://akamaized.net
    """.trimIndent()

    LaunchedEffect(Unit) {
        try {
            val playlistContent = withContext(Dispatchers.IO) { URL(playlistUrl).readText() }
            channels = M3uParser.parse(playlistContent)
        } catch (_: Exception) {
            channels = M3uParser.parse(fallbackM3u)
        }

        if (channels.isNotEmpty()) {
            val lastUrl = sharedPrefs.getString("last_channel_url", null)
            selectedChannel = channels.find { it.url == lastUrl } ?: channels.firstOrNull()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen && channels.isNotEmpty()) {
            val currentOrFirstUrl = selectedChannel?.url ?: channels.first().url
            focusRequesters[currentOrFirstUrl]?.requestFocus()

            // CRITICAL TV FIX 2: Automatically snap scroll bar to the previously selected channel when opening drawer
            val initialIndex = channels.indexOfFirst { it.url == currentOrFirstUrl }.coerceAtLeast(0)
            listState.scrollToItem(initialIndex)
        }
    }

    androidx.activity.compose.BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            LazyColumn(
                state = listState, // CRITICAL TV FIX 3: Bound listState tracker directly here
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp) // FIXED: Widened to comfortably hold channel logos + text next to each other
                    .background(Color(0xCC000000))
                    .padding(top = 12.dp)
            ) {
                itemsIndexed(channels) { index, channel -> // Swapped to itemsIndexed to fetch index coordinates
                    val focusRequester = focusRequesters.getOrPut(channel.url) { FocusRequester() }
                    var isFocused by remember { mutableStateOf(false) }

                    // Trigger smooth automated scroll adjustments instantly during live D-Pad scrolling actions
                    LaunchedEffect(isFocused) {
                        if (isFocused) {
                            // Keeps the currently highlighted channel centered on your TV screen array context
                            val scrollTarget = (index - 2).coerceAtLeast(0)
                            listState.animateScrollToItem(scrollTarget)
                        }
                    }

                    TextButton(
                        onClick = {
                            selectedChannel = channel
                            scope.launch { drawerState.close() }
                            sharedPrefs.edit { putString("last_channel_url", channel.url) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .focusRequester(focusRequester)
                            .focusable()
                            .onFocusChanged { focusState -> isFocused = focusState.isFocused },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isFocused) Color(0xFF262626) else Color.Transparent,
                            contentColor = if (isFocused) Color(0xFFFFD500) else Color.White
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        // MERGED ITEM 3: Horizontal Row layout placing the logo and text side-by-side
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!channel.logoUrl.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0x22FFFFFF), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .padding(2.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0x44FFFFFF), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Text("TV", fontSize = 12.sp, color = Color.LightGray)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(text = channel.name, fontSize = 18.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            selectedChannel?.let { channel ->
                key(channel.url) {
                    VideoPlayer(channel = channel)
                }
            }

            if (!drawerState.isOpen) {
                Button(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .padding(top = 40.dp, start = 16.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text("☰ Channels")
                }
            }
        }
    }
}



// RE-ADDED VISUAL ANCHOR: Safe baseline video renderer component sitting correctly at bottom
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(channel: Channel) {
    val context = LocalContext.current

    // 1. Set up standard network data source parameters
    val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)

    // 2. Programmatically parse and prepare the ClearKey DRM session if the channel has a key
    val customDrmSessionManager = if (!channel.drmLicenseKey.isNullOrBlank()) {
        val parts = channel.drmLicenseKey.split(":")
        if (parts.size == 2) {
            val keyId = parts[0]
            val key = parts[1]

            val clearKeyJson = """
                {
                  "keys": [
                    {
                      "kty": "oct",
                      "kid": "${base64UrlEncode(keyId)}",
                      "k": "${base64UrlEncode(key)}"
                    }
                  ],
                  "type": "temporary"
                }
            """.trimIndent()

            val localDrmCallback = androidx.media3.exoplayer.drm.LocalMediaDrmCallback(clearKeyJson.toByteArray())

            androidx.media3.exoplayer.drm.DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(
                    androidx.media3.common.C.CLEARKEY_UUID,
                    androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER
                )
                .build(localDrmCallback)
        } else null
    } else null

    // 3. Initialize MediaSourceFactory and cleanly inject the DRM configuration manager
    val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
        .setDataSourceFactory(httpDataSourceFactory)

    customDrmSessionManager?.let { drmManager ->
        mediaSourceFactory.setDrmSessionManagerProvider { drmManager }
    }

    // 4. Construct the container-aware ExoPlayer instance
    val exoPlayer = remember(channel.url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(channel.url) {
        val mediaItemBuilder = MediaItem.Builder().setUri(channel.url)

        if (!channel.drmLicenseKey.isNullOrBlank()) {
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.CLEARKEY_UUID)
                    .setMultiSession(true)
                    .build()
            )
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "DRM Handshake Failure: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        })
    }

    DisposableEffect(channel.url) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// FIX: Re-added the missing Base64 URL codec encoder utility block outside the Composable function
fun base64UrlEncode(hexString: String): String {
    val bArray = hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return android.util.Base64.encodeToString(bArray, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE)
}
