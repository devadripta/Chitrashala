package com.dripta.galleryformoto.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dripta.galleryformoto.data.MediaItem

@Composable
fun AlbumDetailScreen(
    albumName: String,
    items: List<MediaItem>,
    viewModel: GalleryViewModel,
    onItemClick: (List<MediaItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    MediaListScreen(
        title = albumName,
        items = items,
        viewModel = viewModel,
        mode = ScreenMode.ALBUM,
        onItemClick = onItemClick,
        modifier = modifier
    )
}
