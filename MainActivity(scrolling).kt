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
import android.net.Uri

class MainActivity : ComponentActivity() {

    private val playlistUrl = "https://raw.githubusercontent.com/hjacked/iptv/refs/heads/main/hjedv1.m3u"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val player = ExoPlayer.Builder(this).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            playWhenReady = true
        }

        setContent {
            var showSplash by remember { mutableStateOf(true) }

            if (showSplash) {
                SplashScreen {
                    showSplash = false
                }
            } else {
                KinsFolkTVAppFixed(playlistUrl, this, player)
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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "KinsFolkTV",
                fontSize = 42.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(4.dp),
                color = Color.White,
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
fun KinsFolkTVAppFixed(playlistUrl: String, context: Context, player: ExoPlayer) {
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val sharedPrefs = context.getSharedPreferences("kinsfolk_prefs", Context.MODE_PRIVATE)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val listState = rememberLazyListState()

    // Filter channels based on search text input
    val filteredChannels = remember(channels, searchQuery) {
        if (searchQuery.isBlank()) {
            channels
        } else {
            channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // --- NEW: TRACK HIGHLIGHT DURING SCROLLING ---
    // Automatically finds the index of the item currently visible at the top of the list
    val currentScrolledIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    // Dynamically update the selection highlight to follow your scrolling index
    LaunchedEffect(currentScrolledIndex, filteredChannels, drawerState.isOpen) {
        // Only auto-highlight while scrolling if the drawer is actually open
        if (drawerState.isOpen && filteredChannels.isNotEmpty() && currentScrolledIndex < filteredChannels.size) {
            val scrolledChannel = filteredChannels[currentScrolledIndex]
            selectedChannel = scrolledChannel

            // Optional: Automatically play the channel you scroll onto
            playChannel(player, scrolledChannel.url)
        }
    }
    // ----------------------------------------------

    LaunchedEffect(Unit) {
        try {
            val content = withContext(Dispatchers.IO) { URL(playlistUrl).readText() }
            channels = M3uParser.parse(content)
        } catch (_: Exception) {
            channels = M3uParser.parse(
                """
                #EXTM3U
                #EXTINF:-1 tvg-name="NASA TV",NASA TV
                https://akamaized.net
                """.trimIndent()
            )
        }

        if (channels.isNotEmpty()) {
            val lastUrl = sharedPrefs.getString("last_channel_url", null)
            selectedChannel = channels.find { it.url == lastUrl } ?: channels.firstOrNull()
            selectedChannel?.let { playChannel(player, it.url) }
        }
    }

    // Handles initial focus/scroll position when drawer opens
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen && filteredChannels.isNotEmpty()) {
            val currentOrFirstUrl = selectedChannel?.url ?: filteredChannels.first().url
            focusRequesters[currentOrFirstUrl]?.requestFocus()
            val initialIndex = filteredChannels.indexOfFirst { it.url == currentOrFirstUrl }.coerceAtLeast(0)
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
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(Color(0xCC000000))
                    .padding(top = 16.dp, start = 8.dp, end = 8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search channels...", color = Color.Gray, fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF222222),
                        unfocusedContainerColor = Color(0xFF111111),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFFD500),
                        focusedIndicatorColor = Color(0xFFFFD500),
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredChannels) { index, channel ->
                        val focusRequester = focusRequesters.getOrPut(channel.url) { FocusRequester() }

                        // Highlight works off our state updated by scroll position OR click
                        val isSelected = selectedChannel == channel

                        TextButton(
                            onClick = {
                                selectedChannel = channel
                                scope.launch { drawerState.close() }
                                sharedPrefs.edit { putString("last_channel_url", channel.url) }
                                playChannel(player, channel.url)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .focusRequester(focusRequester)
                                .focusable()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        selectedChannel = channel
                                        playChannel(player, channel.url)
                                    }
                                },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSelected) Color(0xFF3D3D3D) else Color.Transparent,
                                contentColor = if (isSelected) Color(0xFFFFD500) else Color.White
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD500)) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!channel.logoUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(0x1AFFFFFF), androidx.compose.foundation.shape.CircleShape)
                                            .padding(4.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(0x33FFFFFF), androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("TV", fontSize = 10.sp, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = channel.name,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            }
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

fun playChannel(player: ExoPlayer, url: String) {
    player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
    player.prepare()
    player.play()
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(channel: Channel) {
    val context = LocalContext.current

    val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)

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

    val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
        .setDataSourceFactory(httpDataSourceFactory)

    customDrmSessionManager?.let { drmManager ->
        mediaSourceFactory.setDrmSessionManagerProvider { drmManager }
    }

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

fun base64UrlEncode(hexString: String): String {
    val bArray = hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    return android.util.Base64.encodeToString(bArray, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE)
}
