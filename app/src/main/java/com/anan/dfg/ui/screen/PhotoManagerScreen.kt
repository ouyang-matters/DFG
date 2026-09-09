package com.anan.dfg.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anan.dfg.data.CheckEvent
import com.anan.dfg.data.Repository
import com.anan.dfg.ui.SimpleViewModelFactory
import com.anan.dfg.ui.theme.AppTheme
import com.anan.dfg.util.Format
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhotoManagerViewModel(private val repo: Repository, private val itemId: Long) : ViewModel() {

    val photos: StateFlow<List<CheckEvent>> = repo.observePhotoEvents(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var bytesUsed by mutableStateOf(0L)
        private set

    fun refreshSize() {
        viewModelScope.launch { bytesUsed = repo.photos.bytesUsed(itemId) }
    }

    fun delete(ids: Set<Long>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteEvents(ids.toList())
            refreshSize()
            onDone()
        }
    }

    fun fileFor(event: CheckEvent): File? = event.photoPath?.let { repo.photos.absoluteFile(it) }
}

/**
 * Photo library for one check: browse as a grid, long-press to multi-select,
 * delete in bulk. Deleting removes the log entry and the file on disk together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoManagerScreen(itemId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val vm: PhotoManagerViewModel =
        viewModel(factory = SimpleViewModelFactory { PhotoManagerViewModel(repo, itemId) })

    val photos by vm.photos.collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CheckEvent?>(null) }

    LaunchedEffect(photos.size) { vm.refreshSize() }

    val selecting = selected.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (selecting) {
                        Text(
                            selected.size.toString() + " selected",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = { if (selecting) selected = emptySet() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (photos.isNotEmpty()) {
                        IconButton(onClick = {
                            selected = if (selected.size == photos.size) {
                                emptySet()
                            } else {
                                photos.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                    }
                    if (selecting) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (!selecting) {
                    Text("Photos", style = MaterialTheme.typography.displaySmall)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                }
                Overline(
                    photos.size.toString() + " photos · " + Format.fileSize(vm.bytesUsed) +
                        " · long-press to select",
                )
            }

            if (photos.isEmpty()) {
                Text(
                    "No photos logged for this check yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(photos, key = { it.id }) { event ->
                    PhotoCell(
                        event = event,
                        file = vm.fileFor(event),
                        selected = event.id in selected,
                        onTap = {
                            if (selecting) {
                                selected = selected.toggle(event.id)
                            } else {
                                preview = event
                            }
                        },
                        onLongPress = { selected = selected.toggle(event.id) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete " + selected.size + " photos?") },
            text = { Text("Their log entries and image files are removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = selected
                    confirmDelete = false
                    vm.delete(target) { selected = emptySet() }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    preview?.let { event ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(Format.dateTime(event.timestamp)) },
            text = {
                Column {
                    AsyncImage(
                        model = vm.fileFor(event),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    if (!event.note.isNullOrBlank()) {
                        Text(event.note, Modifier.padding(top = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    val target = setOf(event.id)
                    preview = null
                    vm.delete(target) {}
                }) { Text("Delete") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    event: CheckEvent,
    file: File?,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = if (selected) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(vertical = 3.dp),
            ) {
                Text(
                    Format.time(event.timestamp),
                    style = AppTheme.timeStyle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                )
            }
        }
    }
}

private fun Set<Long>.toggle(value: Long): Set<Long> =
    if (value in this) this - value else this + value
