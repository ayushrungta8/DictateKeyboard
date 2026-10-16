/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.smartbar.PersonalKeyboardTheme
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.systemService

const val CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO: Int = 0
private const val MaxThumbnailPx = 1024

/** Full-width previews and explicit actions: pasting and typing are never hidden in a hold menu. */
@Composable
fun ClipboardInputLayout(modifier: Modifier = Modifier) {
    PersonalKeyboardTheme {
        val prefs by FlorisPreferenceStore
        val context = LocalContext.current
        val keyboard by context.keyboardManager()
        val clipboard by context.clipboardManager()
        val history by clipboard.historyFlow.collectAsState()
        val typing by clipboard.isTypingOut.collectAsState()
        val enabled by prefs.clipboard.historyEnabled.collectAsState()
        val keyguard = remember { context.systemService(AndroidKeyguardManager::class) }
        val locked = keyguard.isDeviceLocked || keyguard.isKeyguardLocked
        val scope = rememberCoroutineScope()
        var pinnedOnly by remember { mutableStateOf(false) }
        val clips = remember(history, pinnedOnly) {
            history.all.filter { !pinnedOnly || it.isPinned }
                .sortedWith(compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.creationTimestampMs })
        }
        val colors = MaterialTheme.colorScheme
        val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer, selectedLeadingIconColor = colors.onPrimaryContainer)
        Column(modifier.fillMaxWidth().height(FlorisImeSizing.panelUiHeight()).background(colors.surface)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { keyboard.activeState.imeUiMode = ImeUiMode.TEXT },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Keyboard", color = colors.onSurface)
                }
                Text("Clipboard", Modifier.weight(1f).padding(start = 8.dp), color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { keyboard.activateClipboardSearch() }, enabled = !locked && enabled && clips.isNotEmpty()) {
                    Icon(Icons.Default.Search, "Search clipboard")
                }
            }
            if (locked) {
                ClipboardMessage("Unlock your phone to see saved clips.", Modifier.weight(1f))
            } else if (!enabled) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Keep copied text here", style = MaterialTheme.typography.titleMedium)
                    Text("Your clipboard history is turned off.", Modifier.padding(vertical = 8.dp))
                    Button(onClick = { scope.launch { prefs.clipboard.historyEnabled.set(true) } }) { Text("Enable clipboard") }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !pinnedOnly, onClick = { pinnedOnly = false }, label = { Text("All (${history.all.size})") }, colors = chipColors)
                    FilterChip(selected = pinnedOnly, onClick = { pinnedOnly = true }, label = { Text("Pinned (${history.pinned.size})") },
                        leadingIcon = { Icon(Icons.Default.PushPin, null, Modifier.size(16.dp)) }, colors = chipColors)
                }
                if (clips.isEmpty()) {
                    ClipboardMessage(if (pinnedOnly) "Pin a clip to keep it at the top."
                        else "Copy text in any app. Your saved clips will appear here.", Modifier.weight(1f))
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(clips, key = { it.id }) { item ->
                            ClipboardEntry(item, paste = { clipboard.pasteItem(item) },
                                typeOut = { clipboard.typeOutItem(item) },
                                pin = { if (item.isPinned) clipboard.unpinClip(item) else clipboard.pinClip(item) },
                                delete = { clipboard.deleteClip(item, onlyIfUnpinned = false) })
                        }
                    }
                }
            }
            if (typing) {
                Row(Modifier.fillMaxWidth().height(56.dp).background(colors.primaryContainer).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Typing your clip…", Modifier.weight(1f), color = colors.onPrimaryContainer)
                    TextButton(onClick = { clipboard.cancelTypeOut() }, colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)) {
                        Icon(Icons.Default.Stop, null, Modifier.size(20.dp))
                        Text("Stop", color = colors.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardMessage(text: String, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ContentPaste, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClipboardEntry(item: ClipboardItem, paste: () -> Unit, typeOut: () -> Unit,
    pin: () -> Unit, delete: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var more by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceContainer) {
        Column {
            if (item.type == ItemType.TEXT) {
                Text(item.displayText(), Modifier.fillMaxWidth().clickable(onClick = paste)
                    .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else {
                val thumbnail = rememberMediaThumbnail(context, ContentUris.parseId(item.uri!!), item.type)
                if (thumbnail?.isSuccess == true) Image(thumbnail.getOrThrow(), item.type.toString(),
                    Modifier.fillMaxWidth().height(90.dp).clickable(onClick = paste), contentScale = ContentScale.Crop)
                else Text(item.displayText(), Modifier.padding(14.dp), color = colors.onSurface)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = paste, colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)) {
                    Text("Paste", color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                }
                if (item.type == ItemType.TEXT) TextButton(onClick = typeOut, colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)) {
                    Text("Type out", color = colors.onSurface)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = pin) {
                    Icon(if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        if (item.isPinned) "Unpin clip" else "Pin clip",
                        tint = colors.onSurface)
                }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, "Clip options") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Delete clip") }, onClick = { more = false; delete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberMediaThumbnail(context: Context, id: Long, type: ItemType): Result<ImageBitmap>? {
    return produceState<Result<ImageBitmap>?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeMediaThumbnail(context, id, type) }
        }
    }.value
}

private fun decodeMediaThumbnail(context: Context, id: Long, type: ItemType): ImageBitmap {
    val file = ClipboardFileStorage.getFileForId(context, id)
    check(file.exists()) { "Unable to resolve media at ${file.absolutePath}" }
    val bitmap = when (type) {
        ItemType.IMAGE -> {
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().also {
                it.inSampleSize = thumbnailSampleSize(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
        ItemType.VIDEO -> {
            if (AndroidVersion.ATLEAST_API29_Q) {
                val retriever = MediaMetadataRetriever()
                val size = try {
                    retriever.setDataSource(file.absolutePath)
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    thumbnailSize(width?.toIntOrNull() ?: 0, height?.toIntOrNull() ?: 0)
                } finally {
                    // Never released before, so every video preview leaked a native decoder.
                    retriever.release()
                }
                ThumbnailUtils.createVideoThumbnail(file, Size(size.first, size.second), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
            }
        }
        ItemType.TEXT -> null
    }
    checkNotNull(bitmap) { "Unable to decode media at ${file.absolutePath}" }
    return bitmap.asImageBitmap()
}

/**
 * The power of two [BitmapFactory] should shrink an image of [width] by [height] by, so that neither
 * edge exceeds [max]. Unknown bounds (a decode that failed, reported as 0) ask for no shrinking —
 * the decode is about to fail anyway, and a guessed factor would only make the failure quieter.
 */
internal fun thumbnailSampleSize(width: Int, height: Int, max: Int = MaxThumbnailPx): Int {
    var sampleSize = 1
    while (width / sampleSize > max || height / sampleSize > max) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * [width] by [height] scaled down to fit inside [max] on both edges, keeping the aspect ratio and
 * never going below one pixel. A video whose size cannot be read falls back to [max] square.
 */
internal fun thumbnailSize(width: Int, height: Int, max: Int = MaxThumbnailPx): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return max to max
    if (width <= max && height <= max) return width to height
    // Integer arithmetic on purpose: scaling by a double leaves the long edge at max - 1 for the
    // common 16:9 case, and createVideoThumbnail throws on a zero-pixel edge.
    val longestEdge = maxOf(width, height).toLong()
    val scaledWidth = (width.toLong() * max / longestEdge).toInt()
    val scaledHeight = (height.toLong() * max / longestEdge).toInt()
    return maxOf(1, scaledWidth) to maxOf(1, scaledHeight)
}
