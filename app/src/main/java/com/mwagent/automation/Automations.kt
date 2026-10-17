package com.mwagent.automation

import android.content.Context
import com.mwagent.data.automation.TaskRepository
import com.mwagent.data.automation.WatchRepository
import com.mwagent.data.db.TaskEntity
import com.mwagent.data.db.WatchSource
import com.mwagent.data.telegram.TelegramAccount
import com.mwagent.data.telegram.TelegramAccountStore
import com.mwagent.system.notifications.NotificationLog
import com.mwagent.telegram.TelegramWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Automations(
    private val context: Context,
    val tasks: TaskRepository,
    val watches: WatchRepository,
    val notifications: NotificationLog,
    private val runs: AutoRuns,
    private val telegram: TelegramWatcher,
    accounts: TelegramAccountStore,
    val scope: CoroutineScope,
) {
    private val engine = WatchEngine(watches, runs, scope)

    val telegramAccount: Flow<TelegramAccount?> = combine(engine.sources, accounts.account) { sources, account ->
        account.takeIf { WatchSource.TELEGRAM in sources && it.linked }
    }.distinctUntilChanged()

    val watching: Flow<Boolean> = combine(engine.sources, telegramAccount) { sources, account ->
        account != null || WatchSource.NOTIFICATIONS in sources
    }.distinctUntilChanged()

    init {
        notifications.onPosted = { engine.offer(it.toEvent()) }
        scope.launch {
            watching.filter { it }.collect {
                withContext(Dispatchers.Main) { WatchService.start(context) }
            }
        }
    }

    suspend fun fire(id: String) {
        val task = tasks.fire(id) ?: return
        scope.launch { run(task) }
    }

    suspend fun restore() {
        tasks.restore().forEach { scope.launch { run(it) } }
    }

    suspend fun listen(account: TelegramAccount) = telegram.start(account) { engine.offer(it.toEvent()) }

    suspend fun unlisten() = telegram.stop()

    private suspend fun run(task: TaskEntity) {
        val chatId = runs.run(task.title, task.chatId, task.prompt, note(task)) ?: return
        if (chatId != task.chatId) tasks.moveTo(task.id, chatId)
    }

    private fun note(task: TaskEntity): String =
        "This message is the scheduled task \"${task.title}\" the user set up earlier; they are not watching the chat right now. " +
            "Do it now and reply with the result, which is shown to them as a notification. " +
            "If the task only asks to report something and there is nothing to report, reply with exactly ${AutoRuns.QUIET}."
}
