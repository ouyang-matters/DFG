package io.github.ouyangmatters.rounds.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ouyangmatters.rounds.R
import io.github.ouyangmatters.rounds.data.CheckKind
import io.github.ouyangmatters.rounds.data.ChecklistItem
import io.github.ouyangmatters.rounds.data.ReminderRule
import io.github.ouyangmatters.rounds.data.Repository
import io.github.ouyangmatters.rounds.schedule.ItemStatus
import io.github.ouyangmatters.rounds.schedule.StatusComputer
import io.github.ouyangmatters.rounds.ui.SimpleViewModelFactory
import io.github.ouyangmatters.rounds.ui.theme.AppTheme
import io.github.ouyangmatters.rounds.util.Format
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeRow(
    val item: ChecklistItem,
    val rules: List<ReminderRule>,
    val status: ItemStatus,
)

private data class Snapshot(
    val items: List<ChecklistItem>,
    val rules: List<ReminderRule>,
    val now: Long,
)

class HomeViewModel(private val repo: Repository) : ViewModel() {

    /** Keeps the running/upcoming copy fresh without a manual refresh. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<HomeRow>> =
        combine(
            repo.observeItems(),
            repo.observeAllRules(),
            repo.observeEventChanges(),
            ticker,
        ) { items, rules, _, now -> Snapshot(items, rules, now) }
            .mapLatest { snapshot ->
                snapshot.items.map { item ->
                    val itemRules = snapshot.rules.filter { it.itemId == item.id }
                    HomeRow(
                        item = item,
                        rules = itemRules,
                        status = StatusComputer.compute(repo, item.id, itemRules, snapshot.now),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun quickCheckIn(itemId: Long, onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(repo.addEvent(itemId, CheckKind.BUTTON, note = null)) }
    }

    fun savePhoto(itemId: Long, relativePath: String) {
        viewModelScope.launch {
            repo.addEvent(itemId, CheckKind.PHOTO, note = null, photoPath = relativePath)
        }
    }

    fun undo(eventId: Long) {
        viewModelScope.launch { repo.deleteEvents(listOf(eventId)) }
    }
}

@Composable
fun HomeScreen(
    onOpenItem: (Long) -> Unit,
    onCreateItem: () -> Unit,
    notificationsAllowed: Boolean,
    onFixNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val vm: HomeViewModel = viewModel(factory = SimpleViewModelFactory { HomeViewModel(repo) })
    val rows by vm.rows.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val capture = rememberPhotoCapture(repo) { itemId, path -> vm.savePhoto(itemId, path) }
    // Resolved up front: a snackbar is shown from a coroutine, not from composition.
    val loggedMessage = stringResource(R.string.snackbar_logged)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateItem,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_new_check)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Masthead(
                    pending = rows.count {
                        it.status.activeWindow != null && !it.status.activeSatisfied
                    },
                    hasItems = rows.isNotEmpty(),
                )
            }

            if (!notificationsAllowed) {
                item {
                    NoticeCard(
                        text = stringResource(R.string.home_notif_off),
                        actionLabel = stringResource(R.string.home_turn_on),
                        onAction = onFixNotifications,
                    )
                }
            }
            if (rows.isEmpty()) {
                item { EmptyState() }
            }
            items(rows, key = { it.item.id }) { row ->
                ItemCard(
                    row = row,
                    onClick = { onOpenItem(row.item.id) },
                    onQuickCheck = {
                        vm.quickCheckIn(row.item.id) { eventId ->
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = loggedMessage.format(row.item.name),
                                    actionLabel = undoLabel,
                                )
                                if (result == SnackbarResult.ActionPerformed) vm.undo(eventId)
                            }
                        }
                    },
                    onQuickPhoto = { capture(row.item.id) },
                )
            }
        }
    }
}

@Composable
private fun Masthead(pending: Int, hasItems: Boolean) {
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Overline(Format.dayHeading(LocalContext.current.resources, LocalDate.now()))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                !hasItems -> stringResource(R.string.home_none_scheduled)
                pending == 0 -> stringResource(R.string.home_all_clear)
                else -> pluralStringResource(R.plurals.home_open_count, pending, pending)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(50),
        ) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(18.dp).size(30.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeCard(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemCard(
    row: HomeRow,
    onClick: () -> Unit,
    onQuickCheck: () -> Unit,
    onQuickPhoto: () -> Unit,
) {
    val now = remember(row) { System.currentTimeMillis() }
    val res = LocalContext.current.resources
    val tone = statusTone(row.status, now)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(16.dp)) {
            AccentBar(tone.accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.item.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (row.item.description.isNotBlank()) {
                            Text(
                                row.item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusPill(tone.label, tone.container, tone.content)
                }

                val focus = row.status.focusWindow
                if (focus != null) {
                    Spacer(Modifier.height(14.dp))
                    WindowTrack(
                        window = focus,
                        now = now,
                        eventTimes = row.status.activeEventTimes,
                        accent = tone.accent,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Overline(
                    row.rules.filter { it.enabled }.take(2)
                        .joinToString("  ·  ") { Format.rule(res, it) }
                        .ifEmpty { stringResource(R.string.headline_log_only) },
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.item.allowPhoto) {
                        OutlinedButton(
                            onClick = onQuickPhoto,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.action_photo),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    if (row.item.allowButton) {
                        FilledTonalButton(
                            onClick = onQuickCheck,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.TaskAlt, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.action_log_check),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                row.status.lastEventAt?.let {
                    Spacer(Modifier.height(10.dp))
                    Box {
                        Text(
                            stringResource(R.string.home_last, Format.dateTime(res, it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
