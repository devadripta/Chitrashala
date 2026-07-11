package com.dripta.galleryformoto.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    hideLabel: String = "Hide",
    visible: Boolean = true,
    onBatchEdit: (() -> Unit)? = null,
    onCollage: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    allSelected: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.7f)
        ) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(250)
        ) { -it } + fadeOut(tween(150))
    ) {
        TopAppBar(
            title = {
                Text(
                    "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                }
            },
            actions = {
                if (onSelectAll != null) {
                    SelectionAction(
                        icon = if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                        onClick = onSelectAll
                    )
                }
                if (onBatchEdit != null) {
                    SelectionAction(icon = Icons.Filled.AutoFixHigh, onClick = onBatchEdit)
                }
                if (onCollage != null) {
                    SelectionAction(icon = Icons.Filled.Dashboard, onClick = onCollage)
                }
                SelectionAction(icon = Icons.Filled.Favorite, onClick = onFavorite)
                SelectionAction(icon = Icons.Filled.VisibilityOff, onClick = onHide)
                SelectionAction(icon = Icons.Filled.Share, onClick = onShare)
                SelectionAction(
                    icon = Icons.Filled.Delete,
                    onClick = onDelete,
                    tintColor = MaterialTheme.colorScheme.error
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            windowInsets = WindowInsets.statusBars
        )
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    onClick: () -> Unit,
    tintColor: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tintColor
        )
    }
}
