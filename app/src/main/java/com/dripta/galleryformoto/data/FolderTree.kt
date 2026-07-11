package com.dripta.galleryformoto.data

/** A single level of the nested-folder hierarchy derived from each photo's on-disk path. */
data class FolderNode(
    val name: String,
    /** Full path from the root, e.g. "Pictures/WhatsApp Images". Empty string = root. */
    val path: String,
    val subfolders: List<FolderNode>,
    /** Media directly inside this folder (not counting descendants). */
    val directItems: List<MediaItem>,
    /** All media in this folder and everything beneath it, used for cover photo + counts. */
    val totalItems: List<MediaItem>
)

/**
 * Builds a folder tree from each item's [MediaItem.relativePath] (e.g. "Pictures/WhatsApp Images/Sent/"),
 * so albums nest the way they actually sit on disk instead of showing one flat list per MediaStore bucket.
 */
fun List<MediaItem>.buildFolderTree(): FolderNode {
    fun segments(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotBlank() }

    fun buildLevel(items: List<MediaItem>, depth: Int, pathPrefix: String): FolderNode {
        val direct = items.filter { segments(it.relativePath).size == depth }
        val deeper = items.filter { segments(it.relativePath).size > depth }
        val byNextSegment = deeper.groupBy { segments(it.relativePath)[depth] }

        val subfolders = byNextSegment.map { (segment, children) ->
            val childPath = if (pathPrefix.isEmpty()) segment else "$pathPrefix/$segment"
            buildLevel(children, depth + 1, childPath)
        }.sortedBy { it.name.lowercase() }

        return FolderNode(
            name = pathPrefix.substringAfterLast('/'),
            path = pathPrefix,
            subfolders = subfolders,
            directItems = direct.sortedByDescending { it.dateMillis },
            totalItems = items.sortedByDescending { it.dateMillis }
        )
    }

    return buildLevel(this, 0, "")
}

/** Finds the node at [path] within this tree, or null if it doesn't exist (e.g. folder was emptied). */
fun FolderNode.findNode(path: String): FolderNode? {
    if (path.isEmpty() || path == this.path) return this
    val remaining = path.removePrefix(this.path).trim('/')
    val nextSegment = remaining.substringBefore('/')
    val child = subfolders.find { it.path.substringAfterLast('/') == nextSegment } ?: return null
    return if (child.path == path) child else child.findNode(path)
}
