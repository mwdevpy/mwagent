package com.mwagent.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.composables.icons.materialsymbols.rounded.Delete
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
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.components.groupedListColors
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
                    TaskRow(task, index, taskItems.size, onDelete = { viewModel.cancelTask(task.id) })
                }
            }
            if (watchItems.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.automations_watches)) }
                itemsIndexed(watchItems, key = { _, watch -> "watch-${watch.id}" }) { index, watch ->
                    WatchRow(watch, index, watchItems.size, onDelete = { viewModel.removeWatch(watch.id) })
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, index: Int, count: Int, onDelete: () -> Unit) {
    val context = LocalContext.current
    AutomationRow(
        icon = { ShapeBadge(MaterialSymbols.Rounded.Event_repeat) },
        title = task.title,
        details = stringResource(R.string.automations_next, formatTime(task.triggerAt)) + " · " + repeatLabel(context, task),
        index = index,
        count = count,
        onDelete = onDelete,
    )
}

@Composable
private fun WatchRow(watch: WatchEntity, index: Int, count: Int, onDelete: () -> Unit) {
    val telegram = watch.source == WatchSource.TELEGRAM
    val source = stringResource(if (telegram) R.string.automations_watch_telegram else R.string.automations_watch_notifications)
    val who = watch.sender ?: stringResource(if (telegram) R.string.automations_watch_any_chat else R.string.automations_watch_any_app)
    val word = watch.keyword?.let { " · «$it»" }.orEmpty()
    AutomationRow(
        icon = { ShapeBadge(if (telegram) MaterialSymbols.Rounded.Send else MaterialSymbols.Rounded.Notifications_unread) },
        title = watch.title,
        details = "$source · $who$word",
        index = index,
        count = count,
        onDelete = onDelete,
    )
}

@Composable
private fun AutomationRow(icon: @Composable () -> Unit, title: String, details: String, index: Int, count: Int, onDelete: () -> Unit) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = groupedListColors(),
        leadingContent = icon,
        supportingContent = { Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(R.string.automations_delete))
            }
        },
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

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
