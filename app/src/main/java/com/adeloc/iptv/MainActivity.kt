package com.adeloc.iptv

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.adeloc.iptv.data.local.AppDatabase
import com.adeloc.iptv.data.local.datastore.UserPreferences
import com.adeloc.iptv.ui.login.LoginScreen
import com.adeloc.iptv.ui.search.GlobalSearchScreen
import com.adeloc.iptv.ui.search.GlobalSearchViewModel
import com.adeloc.iptv.ui.series.SeriesDetailScreen
import com.adeloc.iptv.ui.series.SeriesDetailViewModel
import com.adeloc.iptv.ui.settings.SettingsScreen
import com.adeloc.iptv.ui.settings.SettingsViewModel
import com.adeloc.iptv.ui.tv.LiveTvDashboard
import com.adeloc.iptv.ui.tv.LiveTvViewModel
import com.adeloc.iptv.ui.tvguide.TvGuideScreen
import com.adeloc.iptv.ui.vod.VodViewModel
import com.adeloc.iptv.ui.vod.VodDashboard
import com.adeloc.iptv.ui.player.PlayerViewModel
import com.adeloc.iptv.util.PipState
import com.adeloc.iptv.ui.multiview.MultiViewScreen
import com.google.android.gms.cast.framework.CastContext
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    var isPipSupported by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        isPipSupported = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try { CastContext.getSharedInstance(this) } catch (e: Exception) {}

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF2979FF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(isInPipMode)
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPipSupported && PipState.isVideoPlaying) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

@Composable
fun AppNavigation(isInPipMode: Boolean) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences.getInstance(context) }
    val activePlaylistId by userPrefs.activePlaylistIdFlow.collectAsState(initial = -1)

    LaunchedEffect(activePlaylistId) {
        if (activePlaylistId != -1 && navController.currentDestination?.route == "login") {
            navController.navigate("dashboard/$activePlaylistId") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = if (activePlaylistId != -1) "dashboard/$activePlaylistId" else "login") {
        composable("login") {
            LoginScreen(onLoginSuccess = { result ->
                if (result is Int) {
                    navController.navigate("dashboard/$result") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            })
        }

        composable(
            route = "dashboard/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 1
            MainMenuScreen(
                playlistId = playlistId,
                onLiveTvClick = { navController.navigate("live_tv/$playlistId") },
                onVodClick = { navController.navigate("vod/$playlistId") },
                onSeriesClick = { navController.navigate("series/$playlistId") },
                onSearchClick = { navController.navigate("search/$playlistId") },
                onSettingsClick = { navController.navigate("settings") },
                onMultiViewClick = { navController.navigate("multi_view") },
                onTvGuideClick = { navController.navigate("tv_guide") }
            )
        }

        composable("multi_view") {
            MultiViewScreen()
        }

        composable("tv_guide") {
            TvGuideScreen(navController)
        }

        composable(
            route = "live_tv/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 1
            val database = AppDatabase.getInstance(context)
            val viewModel: LiveTvViewModel = viewModel(factory = LiveTvViewModel.Factory(database, playlistId))
            
            LiveTvDashboard(
                viewModel = viewModel,
                isInPipMode = isInPipMode,
                onFullScreenClick = { url -> 
                    navController.navigate("player?url=${URLEncoder.encode(url, "UTF-8")}&type=live&playlistId=$playlistId")
                }
            )
        }

        composable("settings") {
            val database = AppDatabase.getInstance(context)
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(database, context))
            SettingsScreen(
                viewModel = viewModel,
                onNavigateToDashboard = {
                    navController.navigate("dashboard/$activePlaylistId") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "vod/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 1
            val database = AppDatabase.getInstance(context)
            val viewModel: VodViewModel = viewModel(factory = VodViewModel.Factory(database, playlistId, "vod"))
            
            VodDashboard(
                viewModel = viewModel,
                title = "Movies",
                onStreamClick = { stream, resumePosition ->
                    navController.navigate("player?url=${URLEncoder.encode(stream.url, "UTF-8")}&title=${URLEncoder.encode(stream.name, "UTF-8")}&poster=${URLEncoder.encode(stream.logoUrl ?: "", "UTF-8")}&id=${stream.streamId}&type=vod&resumePosition=$resumePosition&playlistId=$playlistId")
                }
            )
        }

        composable(
            route = "series/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 1
            val database = AppDatabase.getInstance(context)
            val viewModel: VodViewModel = viewModel(factory = VodViewModel.Factory(database, playlistId, "series"))
            
            VodDashboard(
                viewModel = viewModel,
                title = "Series",
                onStreamClick = { stream, _ ->
                    navController.navigate("series_detail/${playlistId}/${stream.streamId}")
                }
            )
        }

        composable(
            route = "series_detail/{playlistId}/{seriesId}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.IntType },
                navArgument("seriesId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 1
            val database = AppDatabase.getInstance(context)
            val viewModel: SeriesDetailViewModel = viewModel(
                factory = SeriesDetailViewModel.Factory(database, seriesId)
            )
            
            SeriesDetailScreen(
                viewModel = viewModel,
                onPlayEpisode = { streamUrl, title, resumePosition ->
                    navController.navigate("player?url=${URLEncoder.encode(streamUrl, "UTF-8")}&title=${URLEncoder.encode(title, "UTF-8")}&seriesApiId=${seriesId}&playlistId=${playlistId}&type=series&resumePosition=$resumePosition")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "search/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 1
            val database = AppDatabase.getInstance(context)
            val viewModel: GlobalSearchViewModel = viewModel(factory = GlobalSearchViewModel.Factory(database, context))
            GlobalSearchScreen(
                viewModel = viewModel,
                onChannelClick = { channel ->
                    navController.navigate("player?url=${URLEncoder.encode(channel.streamUrl, "UTF-8")}&title=${URLEncoder.encode(channel.name, "UTF-8")}&poster=${URLEncoder.encode(channel.logoUrl ?: "", "UTF-8")}&type=live&playlistId=$playlistId")
                },
                onStreamClick = { stream ->
                    if (stream.streamType == com.adeloc.iptv.data.local.entity.StreamType.VOD || stream.streamType == com.adeloc.iptv.data.local.entity.StreamType.LIVE) {
                        val type = if (stream.streamType == com.adeloc.iptv.data.local.entity.StreamType.LIVE) "live" else "vod"
                        navController.navigate("player?url=${URLEncoder.encode(stream.url, "UTF-8")}&title=${URLEncoder.encode(stream.name, "UTF-8")}&poster=${URLEncoder.encode(stream.logoUrl ?: "", "UTF-8")}&id=${stream.streamId}&type=$type&playlistId=$playlistId")
                    } else {
                        navController.navigate("series_detail/${playlistId}/${stream.streamId}")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "player?url={url}&title={title}&poster={poster}&id={id}&seriesApiId={seriesApiId}&playlistId={playlistId}&type={type}&resumePosition={resumePosition}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "Video Stream" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("id") { type = NavType.StringType; defaultValue = "" },
                navArgument("seriesApiId") { type = NavType.StringType; defaultValue = "" },
                navArgument("playlistId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("type") { type = NavType.StringType; defaultValue = "" },
                navArgument("resumePosition") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val title = backStackEntry.arguments?.getString("title")?.let { URLDecoder.decode(it, "UTF-8") } ?: "Video Stream"
            val poster = backStackEntry.arguments?.getString("poster")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val seriesApiId = backStackEntry.arguments?.getString("seriesApiId") ?: ""
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: -1
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val resumePosition = backStackEntry.arguments?.getLong("resumePosition") ?: 0L

            val database = AppDatabase.getInstance(context)
            val playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory(database.streamDao(), database.playlistDao(), database.recentDao()))

            FullScreenPlayer(
                url = url, 
                title = title, 
                posterUrl = poster, 
                streamId = id,
                seriesApiId = seriesApiId,
                playlistId = playlistId,
                streamType = type,
                resumePosition = resumePosition,
                viewModel = playerViewModel,
                isInPipMode = isInPipMode, 
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun FullScreenPlayer(
    url: String, 
    title: String, 
    posterUrl: String, 
    streamId: String,
    seriesApiId: String,
    playlistId: Int,
    streamType: String,
    resumePosition: Long,
    viewModel: PlayerViewModel,
    isInPipMode: Boolean, 
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(url) {
        if (streamId.isNotEmpty()) {
            viewModel.updateLastWatched(streamId, streamType)
        } else if (seriesApiId.isNotEmpty() && playlistId != -1) {
            viewModel.updateLastWatched(seriesApiId, "series")
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { 
            // Global immersive
        }
    }

    com.adeloc.iptv.ui.player.VideoPlayer(
        videoUrl = url,
        title = title,
        posterUrl = posterUrl,
        resumePosition = resumePosition,
        streamId = streamId,
        streamType = streamType,
        onProgressUpdate = { pos, dur ->
            viewModel.saveProgress(url, pos, dur, seriesApiId, playlistId)
        },
        modifier = Modifier.fillMaxSize(),
        enablePip = true,
        isFullscreen = true,
        onBack = onBack,
        showCastButton = !isInPipMode
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    playlistId: Int,
    onLiveTvClick: () -> Unit,
    onVodClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMultiViewClick: () -> Unit,
    onTvGuideClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    // Observe the playlist directly from the database to ensure UI updates when data changes
    val playlist by database.playlistDao().observePlaylistById(playlistId).collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Adeloc IPTV") 
                        Text(
                            text = "Expires: ${playlist?.expDate ?: "Unlimited"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionCard("LIVE TV", Icons.Default.Tv, Color(0xFF2979FF), onLiveTvClick)
                SelectionCard("MOVIES", Icons.Default.Movie, Color(0xFFFF5252), onVodClick)
                SelectionCard("SERIES", Icons.Default.VideoLibrary, Color(0xFF4CAF50), onSeriesClick)
                SelectionCard("TV GUIDE", Icons.AutoMirrored.Filled.List, Color(0xFFFFC107), onTvGuideClick)
                SelectionCard("MULTI-VIEW", Icons.Default.GridView, Color(0xFF9C27B0), onMultiViewClick)
            }
        }
    }
}

@Composable
fun SelectionCard(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1E1E1E)).clickable { onClick() }.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(64.dp), tint = color)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, color = Color.White, fontSize = 18.sp, style = MaterialTheme.typography.titleMedium)
    }
}
