package com.dripta.galleryformoto.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dripta.galleryformoto.data.MediaItem

@Composable
fun FavoritesScreen(
    items: List<MediaItem>,
    viewModel: GalleryViewModel,
    onItemClick: (List<MediaItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    MediaListScreen(
        title = "Favorites",
        items = items,
        viewModel = viewModel,
        mode = ScreenMode.FAVORITES,
        onItemClick = onItemClick,
        modifier = modifier
    )
}
