package com.dripta.galleryformoto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dripta.galleryformoto.ui.AlbumDetailScreen
import com.dripta.galleryformoto.ui.AlbumsScreen
import com.dripta.galleryformoto.ui.CleanupScreen
import com.dripta.galleryformoto.ui.FavoritesScreen
import com.dripta.galleryformoto.ui.GalleryViewModel
import com.dripta.galleryformoto.ui.HiddenGate
import com.dripta.galleryformoto.ui.HiddenScreen
import com.dripta.galleryformoto.ui.MediaListScreen
import com.dripta.galleryformoto.ui.PermissionScreen
import com.dripta.galleryformoto.ui.PhotosScreen
import com.dripta.galleryformoto.ui.ScreenMode
import com.dripta.galleryformoto.ui.TrashScreen
import com.dripta.galleryformoto.ui.EditorScreen
import com.dripta.galleryformoto.ui.ProvideImageLoader
import com.dripta.galleryformoto.ui.SearchScreen
import com.dripta.galleryformoto.ui.StoriesScreen
import com.dripta.galleryformoto.ui.StoryViewerScreen
import com.dripta.galleryformoto.ui.ViewerScreen
import com.dripta.galleryformoto.ui.PlacesScreen
import com.dripta.galleryformoto.ui.TrashScreen
import com.dripta.galleryformoto.ui.theme.GalleryForMotoTheme

private val mediaPermissions: Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    // Needed to read unredacted GPS EXIF data for the Places feature; MediaStore itself
    // stopped exposing location columns starting API 29. No dialog is shown for this one -
    // it's silently granted once requested alongside the other media permissions.
    if (Build.VERSION.SDK_INT >= 29) {
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
}.toTypedArray()

class MainActivity : FragmentActivity() {
    // Only fires for screenshots taken while this app is on screen, there's no API (on this or
    // any platform) to know what happens to a photo after it's been shared out to another app.
    private val screenCaptureCallback = object : android.app.Activity.ScreenCaptureCallback {
        override fun onScreenCaptured() {
            android.widget.Toast.makeText(this@MainActivity, "Screenshot taken", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    private var screenCaptureCallbackRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Some OEM builds (e.g. this Motorola ROM) enforce DETECT_SCREEN_CAPTURE, a
            // signature permission third-party apps can't hold, and throw SecurityException here.
            try {
                registerScreenCaptureCallback(mainExecutor, screenCaptureCallback)
                screenCaptureCallbackRegistered = true
            } catch (e: SecurityException) {
                screenCaptureCallbackRegistered = false
            }
        }
        setContent {
            GalleryApp()
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && screenCaptureCallbackRegistered) {
            unregisterScreenCaptureCallback(screenCaptureCallback)
        }
        super.onDestroy()
    }
}

private fun hasMediaPermission(context: android.content.Context): Boolean =
    mediaPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@androidx.compose.runtime.Composable
fun GalleryApp() {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionGranted = results.values.all { it }
        if (!permissionGranted) permissionDeniedOnce = true
    }

    if (!permissionGranted) {
        PermissionScreen(
            permanentlyDenied = permissionDeniedOnce,
            onRequestPermission = {
                if (permissionDeniedOnce) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                } else {
                    permissionLauncher.launch(mediaPermissions)
                }
            }
        )
        return
    }

    val viewModel: GalleryViewModel = viewModel()
    val themeMode by viewModel.settings.themeMode.collectAsState()

    GalleryForMotoTheme(themeMode = themeMode) {
        ProvideImageLoader {
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

    val navController = rememberNavController()
    val bottomRoutes = setOf("photos", "albums", "favorites")
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomRoutes) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    tonalElevation = 3.dp
                ) {
                    NavigationBarItem(
                        selected = currentRoute == "photos",
                        onClick = { navController.navigate("photos") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = {
                            Icon(
                                if (currentRoute == "photos") Icons.Filled.Photo else Icons.Outlined.Photo,
                                contentDescription = "Photos",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Photos") },
                        colors = NavigationBarItemDefaults.colors()
                    )
                    NavigationBarItem(
                        selected = currentRoute == "albums",
                        onClick = { navController.navigate("albums") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = {
                            Icon(
                                if (currentRoute == "albums") Icons.Filled.Collections else Icons.Outlined.Collections,
                                contentDescription = "Albums",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Albums") },
                        colors = NavigationBarItemDefaults.colors()
                    )
                    NavigationBarItem(
                        selected = currentRoute == "favorites",
                        onClick = { navController.navigate("favorites") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = {
                            Icon(
                                if (currentRoute == "favorites") Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorites",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Favorites") },
                        colors = NavigationBarItemDefaults.colors()
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { navController.navigate("hidden_gate") },
                        icon = { Icon(Icons.Outlined.Lock, contentDescription = "Hidden", modifier = Modifier.size(24.dp)) },
                        label = { Text("Hidden") },
                        colors = NavigationBarItemDefaults.colors()
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "photos",
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(350)
                ) { it / 3 } + fadeIn(tween(350))
            },
            exitTransition = { fadeOut(tween(250)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(350)
                ) { it / 3 } + fadeOut(tween(300))
            }
        ) {
            composable("photos") {
                val media by viewModel.visibleMedia.collectAsState()
                PhotosScreen(
                    items = media,
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onItemClick = { list, index ->
                        viewModel.setActiveViewerList(list)
                        navController.navigate("viewer/$index")
                    },
                    onSearchClick = { navController.navigate("search") },
                    onCleanupClick = { navController.navigate("cleanup") },
                    onStoriesClick = { navController.navigate("stories") },
                    onTrashClick = { navController.navigate("trash") }
                )
            }
            composable("search") {
                // Label + tag search, the on-device ML Kit index that actually works.
                // (SemanticSearchScreen needs a real CLIP-style embedding model; its
                // backend is a stub, so wiring it here would be promising magic we
                // can't deliver.)
                SearchScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onBack = { navController.popBackStack() },
                    onPlacesClick = { navController.navigate("places") },
                    onItemClick = { list, index ->
                        viewModel.setActiveViewerList(list)
                        navController.navigate("viewer/$index")
                    }
                )
            }
            composable("cleanup") {
                val allMedia by viewModel.visibleMedia.collectAsState()
                CleanupScreen(
                    viewModel = viewModel,
                    allMedia = allMedia,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("stories") {
                val allMedia by viewModel.visibleMedia.collectAsState()
                val stories by viewModel.stories.collectAsState()
                StoriesScreen(
                    viewModel = viewModel,
                    allMedia = allMedia,
                    onStoryClick = { storyId -> navController.navigate("story/$storyId") },
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                )
            }
            composable("story/{storyId}") { backStack ->
                val storyId = backStack.arguments?.getString("storyId")?.toLongOrNull()
                val stories by viewModel.stories.collectAsState()
                val story = stories.find { it.id == storyId }
                StoryViewerScreen(
                    story = story,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("smart_album/{category}") { backStack ->
                val category = backStack.arguments?.getString("category") ?: ""
                val media by viewModel.smartAlbumMedia(category).collectAsState()
                MediaListScreen(
                    title = category.replace("_", " ").replaceFirstChar { it.uppercase() },
                    items = media,
                    viewModel = viewModel,
                    mode = ScreenMode.ALL,
                    onItemClick = { list, index ->
                        viewModel.setActiveViewerList(list)
                        navController.navigate("viewer/$index")
                    },
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                )
            }
            composable("albums") {
                val albums by viewModel.albums.collectAsState()
                AlbumsScreen(
                    albums = albums,
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onAlbumClick = { album -> navController.navigate("album/${album.id}") },
                    onPlacesClick = { navController.navigate("places") },
                    onFoldersClick = { navController.navigate("folders") },
                    onTrashClick = { navController.navigate("trash") },
                    onSmartAlbumClick = { category -> navController.navigate("smart_album/$category") }
                )
            }
            composable("favorites") {
                val favorites by viewModel.favoriteMedia.collectAsState()
                FavoritesScreen(
                    items = favorites,
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onItemClick = { list, index ->
                        viewModel.setActiveViewerList(list)
                        navController.navigate("viewer/$index")
                    }
                )
            }
            composable("places") {
                val allMedia by viewModel.visibleMedia.collectAsState()
                PlacesScreen(
                    items = allMedia,
                    onBack = { navController.popBackStack() },
                    onPlaceClick = { placeItems, name ->
                        viewModel.setActiveViewerList(placeItems)
                        navController.navigate("viewer/0")
                    }
                )
            }
            composable("trash") {
                val trashedMedia by viewModel.trashedMedia.collectAsState()
                TrashScreen(
                    items = trashedMedia,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("folders") {
                var currentPath by remember { mutableStateOf("") }
                com.dripta.galleryformoto.ui.FoldersScreen(
                    viewModel = viewModel,
                    currentPath = currentPath,
                    onNavigateToPath = { path -> currentPath = path },
                    onItemClick = { list, index ->
                        viewModel.setActiveViewerList(list)
                        navController.navigate("viewer/$index")
                    },
                    onBack = {
                        if (currentPath.isEmpty()) {
                            navController.popBackStack()
                        } else {
                            currentPath = currentPath.substringBeforeLast('/', "")
                        }
                    },
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                )
            }
            composable("album/{bucketId}") { backStack ->
                val bucketId = backStack.arguments?.getString("bucketId")?.toLongOrNull()
                val allMedia by viewModel.visibleMedia.collectAsState()
                val albumMedia = remember(allMedia, bucketId) {
                    allMedia.filter { it.bucketId == bucketId }
                }
                val albumName = albumMedia.firstOrNull()?.bucketName ?: "Album"
                AlbumDetailScreen(
                    albumName = albumName,
                    items = albumMedia,
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onItemClick = { list, index ->
                        viewModel.setActiveViewerList(list)
                        navController.navigate("viewer/$index")
                    }
                )
            }
            composable("hidden_gate") {
                HiddenGate(
                    onUnlocked = {
                        navController.navigate("hidden") { popUpTo("hidden_gate") { inclusive = true } }
                    },
                    onCancelled = { navController.popBackStack() }
                )
            }
            composable("hidden") {
                val hiddenMedia by viewModel.hiddenMedia.collectAsState()
                HiddenScreen(
                    items = hiddenMedia,
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    onItemClick = { list, index ->
                        viewModel.setActiveViewerList(list)
                        navController.navigate("viewer/$index")
                    }
                )
            }
            composable("viewer/{index}") { backStack ->
                val index = backStack.arguments?.getString("index")?.toIntOrNull() ?: 0
                ViewerScreen(
                    items = viewModel.activeViewerList,
                    startIndex = index,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { item ->
                        viewModel.setEditingItem(item)
                        navController.navigate("editor")
                    }
                )
            }
            composable("editor") {
                val item = viewModel.editingItem
                if (item != null) {
                    EditorScreen(
                        item = item,
                        viewModel = viewModel,
                        onSaved = {
                            viewModel.refresh()
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }
        }
    }
    } // ProvideImageLoader
    } // GalleryForMotoTheme
}
