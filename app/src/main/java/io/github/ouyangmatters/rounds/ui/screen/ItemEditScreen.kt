package io.github.ouyangmatters.rounds.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ouyangmatters.rounds.R
import io.github.ouyangmatters.rounds.data.ChecklistItem
import io.github.ouyangmatters.rounds.data.RecurrenceType
import io.github.ouyangmatters.rounds.data.ReminderRule
import io.github.ouyangmatters.rounds.data.Repository
import io.github.ouyangmatters.rounds.schedule.ReminderScheduler
import io.github.ouyangmatters.rounds.ui.SimpleViewModelFactory
import io.github.ouyangmatters.rounds.util.Format
import java.time.LocalDate
import kotlinx.coroutines.launch

class ItemEditViewModel(
    private val repo: Repository,
    private val appContext: Context,
    private val itemId: Long,
) : ViewModel() {

    val isNew = itemId == 0L

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var allowPhoto by mutableStateOf(true)
    var allowButton by mutableStateOf(true)
    var draftRules by mutableStateOf<List<ReminderRule>>(emptyList())
    var hasExistingEvents by mutableStateOf(false)
        private set

    private var loadedItem: ChecklistItem? = null
    private val removedRuleIds = mutableSetOf<Long>()

    /** Draft rules not yet in the database get decreasing negative ids. */
    private var nextTempId = -1L

    init {
        if (!isNew) {
            viewModelScope.launch {
                repo.getItem(itemId)?.let { item ->
                    loadedItem = item
                    name = item.name
                    description = item.description
                    allowPhoto = item.allowPhoto
                    allowButton = item.allowButton
                }
                draftRules = repo.rulesForItem(itemId)
                hasExistingEvents = repo.lastEventAt(itemId) != null
            }
        }
    }

    /** A neutral starting point: today, during waking hours. */
    fun newRuleTemplate(): ReminderRule = ReminderRule(
        id = nextTempId--,
        itemId = itemId,
        recurrence = RecurrenceType.WEEKLY,
        daysOfWeek = setOf(LocalDate.now().dayOfWeek.value),
        windowStartMinute = 9 * 60,
        windowEndMinute = 18 * 60,
    )

    fun upsertDraftRule(rule: ReminderRule) {
        draftRules = if (draftRules.any { it.id == rule.id }) {
            draftRules.map { if (it.id == rule.id) rule else it }
        } else {
            draftRules + rule
        }
    }

    fun removeDraftRule(rule: ReminderRule) {
        if (rule.id > 0) removedRuleIds += rule.id
        draftRules = draftRules.filterNot { it.id == rule.id }
    }

    fun canSave(): Boolean = name.isNotBlank() && (allowPhoto || allowButton)

    /**
     * Saves the item and its rules. Turning a check type off only limits what can be
     * added from now on; entries already logged are always kept.
     */
    fun save(onDone: () -> Unit) {
        if (!canSave()) return
        viewModelScope.launch {
            val base = loadedItem
            val savedId = if (base == null) {
                repo.createItem(
                    ChecklistItem(
                        name = name.trim(),
                        description = description.trim(),
                        allowPhoto = allowPhoto,
                        allowButton = allowButton,
                    ),
                )
            } else {
                repo.updateItem(
                    base.copy(
                        name = name.trim(),
                        description = description.trim(),
                        allowPhoto = allowPhoto,
                        allowButton = allowButton,
                    ),
                )
                base.id
            }

            removedRuleIds.forEach { repo.deleteRuleById(it) }
            removedRuleIds.clear()

            draftRules.forEach { rule ->
                // Temporary ids become 0 so Room assigns a real primary key.
                repo.upsertRule(rule.copy(id = if (rule.id > 0) rule.id else 0, itemId = savedId))
            }

            ReminderScheduler.reschedule(appContext)
            onDone()
        }
    }

    fun deleteItem(onDone: () -> Unit) {
        val target = loadedItem ?: return
        viewModelScope.launch {
            repo.deleteItem(target)
            ReminderScheduler.reschedule(appContext)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(itemId: Long, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val appContext = remember { context.applicationContext }
    val vm: ItemEditViewModel = viewModel(
        factory = SimpleViewModelFactory { ItemEditViewModel(repo, appContext, itemId) },
    )

    var editingRule by remember { mutableStateOf<ReminderRule?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (vm.isNew) R.string.edit_new_title else R.string.edit_settings_title,
                ),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text(stringResource(R.string.edit_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text(stringResource(R.string.edit_notes)) },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))
            Overline(stringResource(R.string.edit_how_logged))
            CheckTypeRow(
                title = stringResource(R.string.action_photo),
                subtitle = stringResource(R.string.edit_photo_sub),
                icon = { Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)) },
                checked = vm.allowPhoto,
                onCheckedChange = { vm.allowPhoto = it },
            )
            CheckTypeRow(
                title = stringResource(R.string.edit_tap),
                subtitle = stringResource(R.string.edit_tap_sub),
                icon = { Icon(Icons.Default.TaskAlt, null, Modifier.size(18.dp)) },
                checked = vm.allowButton,
                onCheckedChange = { vm.allowButton = it },
            )
            if (!vm.allowPhoto && !vm.allowButton) {
                Text(
                    stringResource(R.string.edit_pick_one),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!vm.isNew && vm.hasExistingEvents) {
                Text(
                    stringResource(R.string.edit_entries_kept),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Overline(stringResource(R.string.edit_schedules))
            if (vm.draftRules.isEmpty()) {
                Text(
                    stringResource(R.string.edit_no_schedule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.draftRules.forEach { rule ->
                RuleRow(
                    rule = rule,
                    onClick = { editingRule = rule },
                    onToggle = { vm.upsertDraftRule(rule.copy(enabled = it)) },
                )
            }
            OutlinedButton(
                onClick = { editingRule = vm.newRuleTemplate() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit_add_schedule))
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.save(onBack) },
                enabled = vm.canSave(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(if (vm.isNew) R.string.action_create else R.string.action_save))
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    editingRule?.let { target ->
        RuleEditorDialog(
            initial = target,
            canDelete = vm.draftRules.any { it.id == target.id },
            onDismiss = { editingRule = null },
            onSave = {
                vm.upsertDraftRule(it)
                editingRule = null
            },
            onDelete = {
                vm.removeDraftRule(target)
                editingRule = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_check_title)) },
            text = { Text(stringResource(R.string.delete_check_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteItem(onDeleted)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CheckTypeRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleRow(rule: ReminderRule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val res = LocalContext.current.resources
                Text(Format.rule(res, rule), style = MaterialTheme.typography.bodyLarge)
                Overline(Format.recurrenceName(res, rule.recurrence))
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}
