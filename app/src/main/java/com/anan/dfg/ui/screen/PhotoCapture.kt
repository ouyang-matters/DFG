package com.anan.dfg.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.anan.dfg.data.Repository
import java.io.File
import kotlinx.coroutines.launch

/**
 * Shared capture flow: open a file in the item's own folder, hand it to the camera
 * app, and delete the empty file again if the user backs out.
 */
@Composable
fun rememberPhotoCapture(
    repo: Repository,
    onCaptured: (itemId: Long, relativePath: String) -> Unit,
): (Long) -> Unit {
    val pendingItemId = remember { mutableStateOf(0L) }
    val pendingFile = remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingFile.value
        pendingFile.value = null
        if (file == null) return@rememberLauncherForActivityResult
        if (success && file.length() > 0) {
            onCaptured(pendingItemId.value, repo.photos.relativePath(file))
        } else {
            file.delete()
        }
    }

    return { itemId ->
        val file = repo.photos.newPhotoFile(itemId)
        pendingItemId.value = itemId
        pendingFile.value = file
        launcher.launch(repo.photos.shareUri(file))
    }
}

/** Picks an existing image from the library as the evidence for a check. */
@Composable
fun rememberPhotoImport(
    repo: Repository,
    onImported: (itemId: Long, relativePath: String) -> Unit,
): (Long) -> Unit {
    val pendingItemId = remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val itemId = pendingItemId.value
        scope.launch {
            repo.photos.importFrom(uri, itemId)?.let { onImported(itemId, it) }
        }
    }

    return { itemId ->
        pendingItemId.value = itemId
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}
