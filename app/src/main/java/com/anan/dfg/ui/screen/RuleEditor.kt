package com.anan.dfg.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anan.dfg.data.IntervalUnit
import com.anan.dfg.data.RecurrenceType
import com.anan.dfg.data.ReminderRule
import com.anan.dfg.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Editor for one reminder rule.
 *
 * The window means "at least one check between these two times" — a check outside
 * it does not count — so the copy here says that explicitly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorDialog(
    initial: ReminderRule,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReminderRule) -> Unit,
    onDelete: () -> Unit,
) {
    var rule by remember { mutableStateOf(initial) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editingWindowStart by remember { mutableStateOf(false) }
    var editingWindowEnd by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.heightIn(max = 640.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(
                        "Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete rule",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rule active",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { rule = rule.copy(enabled = it) },
                        )
                    }

                    Overline("Repeats")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceType.entries.forEach { type ->
                            FilterChip(
                                selected = rule.recurrence == type,
                                onClick = { rule = rule.copy(recurrence = type) },
                                label = { Text(Format.recurrenceName(type)) },
                                shape = RoundedCornerShape(10.dp),
                                colors = selectedChipColors(),
                            )
                        }
                    }

                    when (rule.recurrence) {
                        RecurrenceType.ONCE -> {
                            Overline("Date")
                            AssistChip(
                                onClick = { showDatePicker = true },
                                label = { Text(rule.onceDate ?: "Pick a date") },
                                shape = RoundedCornerShape(10.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }

                        RecurrenceType.WEEKLY -> {
                            Overline("Days of the week")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..7).forEach { day ->
                                    DayToggle(
                                        label = Format.weekdayInitial(day),
                                        selected = day in rule.daysOfWeek,
                                        onClick = {
                                            rule = rule.copy(
                                                daysOfWeek = rule.daysOfWeek.toggle(day),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        RecurrenceType.MONTHLY -> {
                            Overline("Days of the month")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..31).forEach { day ->
                                    FilterChip(
                                        selected = day in rule.daysOfMonth,
                                        onClick = {
                                            rule = rule.copy(
                                                daysOfMonth = rule.daysOfMonth.toggle(day),
                                            )
                                        },
                                        label = { Text("$day") },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = selectedChipColors(),
                                    )
                                }
                            }
                            Hint(
                                "If a month is too short for the day you picked — the 31st in " +
                                    "February, say — it lands on the last day of that month.",
                            )
                        }

                        RecurrenceType.INTERVAL -> {
                            Overline("Interval")
                            IntervalRow(
                                count = rule.intervalCount,
                                unit = rule.intervalUnit,
                                onCountChange = { rule = rule.copy(intervalCount = it) },
                                onUnitChange = { rule = rule.copy(intervalUnit = it) },
                            )
                            Hint(
                                "Each period needs at least one check. When a period ends " +
                                    "with nothing logged, you get a reminder.",
                            )
                        }
                    }

                    if (rule.recurrence != RecurrenceType.INTERVAL) {
                        Spacer(Modifier.height(2.dp))
                        Overline("Window")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TimeChip(
                                label = Format.minuteOfDay(rule.windowStartMinute),
                                onClick = { editingWindowStart = true },
                            )
                            Text("to", style = MaterialTheme.typography.bodyMedium)
                            TimeChip(
                                label = Format.minuteOfDay(rule.windowEndMinute),
                                onClick = { editingWindowEnd = true },
                            )
                        }
                        Hint(
                            "At least one check has to land inside this window, otherwise you " +
                                "get a reminder at " + Format.minuteOfDay(rule.windowEndMinute) +
                                ". Checks outside the window don't count towards it." +
                                if (rule.windowEndMinute <= rule.windowStartMinute) {
                                    " The end time is earlier than the start, so the window runs " +
                                        "into the next day."
                                } else {
                                    ""
                                },
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Overline("Preview")
                            Spacer(Modifier.height(4.dp))
                            Text(Format.rule(rule), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { onSave(rule) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Save schedule")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = rule.onceDate?.let {
            runCatching {
                LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // The picker reports UTC midnight, so read it back in UTC.
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        rule = rule.copy(onceDate = date.toString())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (editingWindowStart) {
        TimePickerDialog(
            title = "Window opens",
            initialMinuteOfDay = rule.windowStartMinute,
            onDismiss = { editingWindowStart = false },
            onConfirm = {
                rule = rule.copy(windowStartMinute = it)
                editingWindowStart = false
            },
        )
    }

    if (editingWindowEnd) {
        TimePickerDialog(
            title = "Window closes (reminder time)",
            initialMinuteOfDay = rule.windowEndMinute,
            onDismiss = { editingWindowEnd = false },
            onConfirm = {
                rule = rule.copy(windowEndMinute = it)
                editingWindowEnd = false
            },
        )
    }
}

/** Selected chips use the primary colour so the choice is obvious at a glance. */
@Composable
private fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.height(44.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalRow(
    count: Int,
    unit: IntervalUnit,
    onCountChange: (Int) -> Unit,
    onUnitChange: (IntervalUnit) -> Unit,
) {
    var text by remember(count) { mutableStateOf(count.toString()) }
    var expanded by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Every", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it.isDigit() }.take(4)
                text.toIntOrNull()?.takeIf { it > 0 }?.let(onCountChange)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(88.dp),
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = Format.unitName(unit, count),
                onValueChange = {},
                readOnly = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .width(140.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                IntervalUnit.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(Format.unitName(option, count)) },
                        onClick = {
                            onUnitChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Set<Int>.toggle(value: Int): Set<Int> =
    if (value in this) this - value else this + value
