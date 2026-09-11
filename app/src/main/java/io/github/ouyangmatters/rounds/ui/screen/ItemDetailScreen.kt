package io.github.ouyangmatters.rounds.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.ouyangmatters.rounds.R
import io.github.ouyangmatters.rounds.data.CheckEvent
import io.github.ouyangmatters.rounds.data.CheckKind
import io.github.ouyangmatters.rounds.data.ChecklistItem
import io.github.ouyangmatters.rounds.data.ReminderRule
import io.github.ouyangmatters.rounds.data.Repository
import io.github.ouyangmatters.rounds.schedule.ItemStatus
import io.github.ouyangmatters.rounds.schedule.StatusComputer
import io.github.ouyangmatters.rounds.ui.SimpleViewModelFactory
import io.github.ouyangmatters.rounds.ui.theme.AppTheme
import io.github.ouyangmatters.rounds.util.Format
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val repo: Repository, private val itemId: Long) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val item: StateFlow<ChecklistItem?> = repo.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<CheckEvent>> = repo.observeEvents(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<ReminderRule>> = repo.observeRules(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val status: StateFlow<ItemStatus?> =
        combine(repo.observeRules(itemId), repo.observeEventChanges(), ticker) { rules, _, now ->
            rules to now
        }
            .mapLatest { (rules, now) -> StatusComputer.compute(repo, itemId, rules, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addCheck(note: String?) {
        viewModelScope.launch { repo.addEvent(itemId, CheckKind.BUTTON, note) }
    }

    fun addPhoto(relativePath: String) {
        viewModelScope.launch {
            repo.addEvent(itemId, CheckKind.PHOTO, note = null, photoPath = relativePath)
        }
    }

    fun updateNote(event: CheckEvent, note: String?) {
        viewModelScope.launch { repo.updateEventNote(event, note) }
    }

    fun deleteEvent(event: CheckEvent) {
        viewModelScope.launch { repo.deleteEvents(listOf(event.id)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEditItem: () -> Unit,
    onManagePhotos: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val res = context.resources
    val vm: DetailViewModel =
        viewModel(factory = SimpleViewModelFactory { DetailViewModel(repo, itemId) })

    val item by vm.item.collectAsState()
    val events by vm.events.collectAsState()
    val rules by vm.rules.collectAsState()
    val status by vm.status.collectAsState()

    val capture = rememberPhotoCapture(repo) { _, path -> vm.addPhoto(path) }
    val pickFromLibrary = rememberPhotoImport(repo) { _, path -> vm.addPhoto(path) }

    var noteDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CheckEvent?>(null) }
    var deleting by remember { mutableStateOf<CheckEvent?>(null) }
    var viewingPhoto by remember { mutableStateOf<String?>(null) }

    val current = item

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (current?.allowPhoto == true) {
                        IconButton(onClick = onManagePhotos) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = stringResource(R.string.detail_photos),
                            )
                        }
                    }
                    IconButton(onClick = onEditItem) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.edit_settings_title),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (current != null) {
                CheckInBar(
                    item = current,
                    onPhoto = { capture(itemId) },
                    onLibrary = { pickFromLibrary(itemId) },
                    onLog = { noteDialog = true },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp),
        ) {
            item {
                Text(
                    current?.name ?: "",
                    style = MaterialTheme.typography.displaySmall,
                )
                if (current != null && current.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        current.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(20.dp))
                StatusPanel(status, rules)
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline(stringResource(R.string.detail_history))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        events.size.toString(),
                        style = AppTheme.timeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.detail_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }

            // Timeline grouped by day; each day gets one heading.
            var lastDate: LocalDate? = null
            events.forEachIndexed { index, event ->
                val date = Instant.ofEpochMilli(event.timestamp)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                val isFirstOfDay = date != lastDate
                if (isFirstOfDay) {
                    lastDate = date
                    item(key = "h" + date) { DayHeading(date) }
                }
                item(key = "e" + event.id) {
                    TimelineEntry(
                        event = event,
                        photoFile = event.photoPath?.let { repo.photos.absoluteFile(it) },
                        isFirst = isFirstOfDay,
                        isLast = index == events.lastIndex,
                        onViewPhoto = { viewingPhoto = event.photoPath },
                        onEditNote = { editing = event },
                        onDelete = { deleting = event },
                    )
                }
            }
        }
    }

    if (noteDialog) {
        NoteDialog(
            title = stringResource(R.string.dialog_log_title),
            initial = "",
            confirmLabel = stringResource(R.string.dialog_log_confirm),
            onDismiss = { noteDialog = false },
            onConfirm = {
                vm.addCheck(it)
                noteDialog = false
            },
        )
    }

    editing?.let { target ->
        NoteDialog(
            title = stringResource(R.string.dialog_edit_note_title),
            initial = target.note ?: "",
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { editing = null },
            onConfirm = {
                vm.updateNote(target, it)
                editing = null
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = {
                Text(
                    stringResource(
                        if (target.photoPath != null) {
                            R.string.delete_entry_photo_body
                        } else {
                            R.string.delete_entry_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEvent(target)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    viewingPhoto?.let { path ->
        Dialog(onDismissRequest = { viewingPhoto = null }) {
            AsyncImage(
                model = repo.photos.absoluteFile(path),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun CheckInBar(
    item: ChecklistItem,
    onPhoto: () -> Unit,
    onLibrary: () -> Unit,
    onLog: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!item.allowPhoto && !item.allowButton) {
                Text(
                    stringResource(R.string.detail_no_type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Row
            }
            if (item.allowPhoto) {
                Button(
                    onClick = onPhoto,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_photo))
                }
                OutlinedButton(onClick = onLibrary, shape = RoundedCornerShape(14.dp)) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.detail_pick_library),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (item.allowButton) {
                Button(
                    onClick = onLog,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.allowPhoto) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (item.allowPhoto) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                    ),
                ) {
                    Icon(Icons.Default.TaskAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_log_check))
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(status: ItemStatus?, rules: List<ReminderRule>) {
    val now = remember(status) { System.currentTimeMillis() }
    val tone = statusTone(status, now)

    Surface(
        color = tone.container,
        contentColor = tone.content,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(tone.headline, style = MaterialTheme.typography.headlineSmall)
            val focus = status?.focusWindow
            if (focus != null) {
                Spacer(Modifier.height(4.dp))
                Overline(
                    Format.window(LocalContext.current.resources, focus),
                    color = tone.content.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(14.dp))
                WindowTrack(
                    window = focus,
                    now = now,
                    eventTimes = status.activeEventTimes,
                    accent = tone.accent,
                )
            }
            val enabled = rules.filter { it.enabled }
            if (enabled.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                val res = LocalContext.current.resources
                enabled.forEach {
                    Text(
                        Format.rule(res, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = tone.content.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeading(date: LocalDate) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Overline(
            Format.dayHeading(LocalContext.current.resources, date),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f).height(1.dp),
        ) {}
    }
}

@Composable
private fun TimelineEntry(
    event: CheckEvent,
    photoFile: File?,
    isFirst: Boolean,
    isLast: Boolean,
    onViewPhoto: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = if (event.kind == CheckKind.PHOTO) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    // IntrinsicSize.Min lets the rail line stretch to whatever height the card needs.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(54.dp).fillMaxHeight()) {
            // Continuous rail behind the node, trimmed at the first and last entry.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isFirst) 13.dp else 0.dp)
                    .width(1.5.dp)
                    .then(if (isLast) Modifier.height(13.dp) else Modifier.fillMaxHeight())
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(
                Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    Format.time(event.timestamp),
                    style = AppTheme.timeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).padding(bottom = 12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                if (photoFile != null) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = stringResource(R.string.kind_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onViewPhoto),
                    )
                    if (!event.note.isNullOrBlank()) Spacer(Modifier.height(10.dp))
                }
                if (!event.note.isNullOrBlank()) {
                    Text(event.note, style = MaterialTheme.typography.bodyMedium)
                } else if (photoFile == null) {
                    Text(
                        stringResource(R.string.detail_checked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Overline(
                        stringResource(
                            if (event.kind == CheckKind.PHOTO) {
                                R.string.kind_photo
                            } else {
                                R.string.kind_tap
                            },
                        ),
                        Modifier.weight(1f),
                    )
                    IconButton(onClick = onEditNote, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.detail_edit_note),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.dialog_note_label)) },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
