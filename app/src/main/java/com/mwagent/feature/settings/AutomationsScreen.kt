package com.mwagent.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Event_repeat
import com.composables.icons.materialsymbols.rounded.Notifications_unread
import com.composables.icons.materialsymbols.rounded.Send
import com.mwagent.R
import com.mwagent.data.automation.TaskRepository
import com.mwagent.data.automation.TaskSchedule
import com.mwagent.data.automation.WatchRepository
import com.mwagent.data.db.TaskEntity
import com.mwagent.data.db.WatchEntity
import com.mwagent.data.db.WatchSource
import com.mwagent.data.telegram.TelegramAccountStore
import com.mwagent.system.notifications.AppNotifications
import com.mwagent.system.notifications.NotificationLog
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.CheckSwitch
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.components.groupedListColors
import com.mwagent.ui.haptics.clickHaptics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

class AutomationsViewModel(
    private val tasks: TaskRepository,
    private val watches: WatchRepository,
    accounts: TelegramAccountStore,
) : ViewModel() {
    val taskList: StateFlow<List<TaskEntity>?> = tasks.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val watchList: StateFlow<List<WatchEntity>?> = watches.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val telegramLinked: StateFlow<Boolean> = accounts.account.map { it.linked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    fun cancelTask(id: String) {
        viewModelScope.launch { tasks.cancel(id) }
    }

    fun removeWatch(id: String) {
        viewModelScope.launch { watches.delete(id) }
    }

    fun pauseTask(id: String, paused: Boolean) {
        viewModelScope.launch { tasks.setPaused(id, paused) }
    }

    fun pauseWatch(id: String, paused: Boolean) {
        viewModelScope.launch { watches.setPaused(id, paused) }
    }

    fun editTask(id: String, title: String, prompt: String) {
        viewModelScope.launch { runCatching { tasks.rename(id, title, prompt) } }
    }

    fun editWatch(id: String, title: String, sender: String, keyword: String, instruction: String) {
        viewModelScope.launch { runCatching { watches.edit(id, title, sender, keyword, instruction) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun AutomationsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { AutomationsViewModel(it.tasks, it.watches, it.telegramAccounts) }
    val tasks by viewModel.taskList.collectAsStateWithLifecycle()
    val watches by viewModel.watchList.collectAsStateWithLifecycle()
    val linked by viewModel.telegramLinked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(AppNotifications.canPost(context)) }
    var canListen by remember { mutableStateOf(NotificationLog.isEnabled(context)) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var editingWatch by remember { mutableStateOf<WatchEntity?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = AppNotifications.canPost(context)
        canListen = NotificationLog.isEnabled(context)
    }

    SettingsScaffold(stringResource(R.string.settings_automations), onBack) { padding ->
        val taskItems = tasks ?: return@SettingsScaffold
        val watchItems = watches ?: return@SettingsScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { Notice(stringResource(R.string.automations_hint)) }
            if (!canNotify) item { Notice(stringResource(R.string.automations_notifications_off), isWarning = true) }
            if (!canListen && watchItems.any { it.source == WatchSource.NOTIFICATIONS }) {
                item { Notice(stringResource(R.string.automations_access_off), isWarning = true) }
            }
            if (!linked && watchItems.any { it.source == WatchSource.TELEGRAM }) {
                item { Notice(stringResource(R.string.automations_telegram_off), isWarning = true) }
            }
            if (taskItems.isEmpty() && watchItems.isEmpty()) item { Notice(stringResource(R.string.automations_empty)) }
            if (taskItems.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.automations_tasks), topPadding = 8.dp) }
                itemsIndexed(taskItems, key = { _, task -> "task-${task.id}" }) { index, task ->
                    TaskRow(task, index, taskItems.size, onOpen = { editingTask = task }, onPause = { viewModel.pauseTask(task.id, it) })
                }
            }
            if (watchItems.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.automations_watches)) }
                itemsIndexed(watchItems, key = { _, watch -> "watch-${watch.id}" }) { index, watch ->
                    WatchRow(watch, index, watchItems.size, onOpen = { editingWatch = watch }, onPause = { viewModel.pauseWatch(watch.id, it) })
                }
            }
        }
    }

    editingTask?.let { task ->
        EditDialog(
            title = stringResource(R.string.automations_edit_task),
            fields = listOf(
                stringResource(R.string.automations_field_title) to task.title,
                stringResource(R.string.automations_field_prompt) to task.prompt,
            ),
            onSave = { values -> viewModel.editTask(task.id, values[0], values[1]) },
            onDelete = { viewModel.cancelTask(task.id) },
            onDismiss = { editingTask = null },
        )
    }
    editingWatch?.let { watch ->
        EditDialog(
            title = stringResource(R.string.automations_edit_watch),
            fields = listOf(
                stringResource(R.string.automations_field_title) to watch.title,
                stringResource(R.string.automations_field_from) to watch.sender.orEmpty(),
                stringResource(R.string.automations_field_keyword) to watch.keyword.orEmpty(),
                stringResource(R.string.automations_field_instruction) to watch.instruction,
            ),
            onSave = { values -> viewModel.editWatch(watch.id, values[0], values[1], values[2], values[3]) },
            onDelete = { viewModel.removeWatch(watch.id) },
            onDismiss = { editingWatch = null },
        )
    }
}

@Composable
private fun EditDialog(
    title: String,
    fields: List<Pair<String, String>>,
    onSave: (List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember { fields.map { it.second }.toMutableStateList() }
    val valid = values.first().isNotBlank() && values.last().isNotBlank()
    AlertDialog(
        modifier = Modifier.clickHaptics(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                fields.forEachIndexed { index, (label, _) ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { values[index] = it },
                        label = { Text(label) },
                        singleLine = index < fields.lastIndex,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values.toList()); onDismiss() }, enabled = valid) { Text(stringResource(R.string.automations_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.automations_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_delete_chat_cancel)) }
            }
        },
    )
}

@Composable
private fun TaskRow(task: TaskEntity, index: Int, count: Int, onOpen: () -> Unit, onPause: (Boolean) -> Unit) {
    val context = LocalContext.current
    AutomationRow(
        icon = { ShapeBadge(MaterialSymbols.Rounded.Event_repeat) },
        title = task.title,
        details = if (task.paused) {
            stringResource(R.string.automations_paused) + " · " + repeatLabel(context, task)
        } else {
            stringResource(R.string.automations_next, formatTime(task.triggerAt)) + " · " + repeatLabel(context, task)
        },
        active = !task.paused,
        index = index,
        count = count,
        onOpen = onOpen,
        onPause = onPause,
    )
}

@Composable
private fun WatchRow(watch: WatchEntity, index: Int, count: Int, onOpen: () -> Unit, onPause: (Boolean) -> Unit) {
    val telegram = watch.source == WatchSource.TELEGRAM
    val source = stringResource(if (telegram) R.string.automations_watch_telegram else R.string.automations_watch_notifications)
    val who = watch.sender ?: stringResource(if (telegram) R.string.automations_watch_any_chat else R.string.automations_watch_any_app)
    val word = watch.keyword?.let { " · «$it»" }.orEmpty()
    AutomationRow(
        icon = { ShapeBadge(if (telegram) MaterialSymbols.Rounded.Send else MaterialSymbols.Rounded.Notifications_unread) },
        title = watch.title,
        details = (if (watch.paused) stringResource(R.string.automations_paused) + " · " else "") + "$source · $who$word",
        active = !watch.paused,
        index = index,
        count = count,
        onOpen = onOpen,
        onPause = onPause,
    )
}

@Composable
private fun AutomationRow(
    icon: @Composable () -> Unit,
    title: String,
    details: String,
    active: Boolean,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onPause: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = onOpen,
        modifier = Modifier.alpha(if (active) 1f else INACTIVE_ALPHA),
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = groupedListColors(),
        leadingContent = icon,
        supportingContent = { Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { CheckSwitch(checked = active, onCheckedChange = { onPause(!it) }) },
    ) { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun Notice(text: String, isWarning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isWarning) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (isWarning) colors.onErrorContainer else colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

private fun repeatLabel(context: Context, task: TaskEntity): String = when {
    task.everyMinutes != null -> context.getString(R.string.automations_every, task.everyMinutes)
    task.days != 0 -> TaskSchedule.days(task.days).joinToString { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    else -> context.getString(R.string.automations_once)
}

private const val INACTIVE_ALPHA = 0.6f

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
