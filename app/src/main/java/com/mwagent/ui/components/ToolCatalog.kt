package com.mwagent.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Account_circle
import com.composables.icons.materialsymbols.rounded.Chat
import com.composables.icons.materialsymbols.rounded.Construction
import com.composables.icons.materialsymbols.rounded.Battery_android_full
import com.composables.icons.materialsymbols.rounded.Event_busy
import com.composables.icons.materialsymbols.rounded.Event_repeat
import com.composables.icons.materialsymbols.rounded.Flashlight_on
import com.composables.icons.materialsymbols.rounded.Notifications_unread
import com.composables.icons.materialsymbols.rounded.Pending_actions
import com.composables.icons.materialsymbols.rounded.Radar
import com.composables.icons.materialsymbols.rounded.Visibility
import com.composables.icons.materialsymbols.rounded.Visibility_off
import com.composables.icons.materialsymbols.rounded.Volume_up
import com.composables.icons.materialsymbols.rounded.Qr_code_2
import com.composables.icons.materialsymbols.rounded.Event
import com.composables.icons.materialsymbols.rounded.Call
import com.composables.icons.materialsymbols.rounded.Sms
import com.composables.icons.materialsymbols.rounded.Directions
import com.composables.icons.materialsymbols.rounded.Download
import com.composables.icons.materialsymbols.rounded.Download_done
import com.composables.icons.materialsymbols.rounded.Currency_exchange
import com.composables.icons.materialsymbols.rounded.Share
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.rounded.Content_copy
import com.composables.icons.materialsymbols.rounded.Content_paste
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Library_books
import com.composables.icons.materialsymbols.rounded.Location_on
import com.composables.icons.materialsymbols.rounded.Play_circle
import com.composables.icons.materialsymbols.rounded.Send
import com.composables.icons.materialsymbols.rounded.Manage_search
import com.composables.icons.materialsymbols.rounded.Bookmark_remove
import com.composables.icons.materialsymbols.rounded.Edit_note
import com.composables.icons.materialsymbols.rounded.Bookmark_add
import com.composables.icons.materialsymbols.rounded.Bar_chart
import com.composables.icons.materialsymbols.rounded.Build
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Description
import com.composables.icons.materialsymbols.rounded.Edit_document
import com.composables.icons.materialsymbols.rounded.Folder_open
import com.composables.icons.materialsymbols.rounded.Hub
import com.composables.icons.materialsymbols.rounded.Perm_media
import com.composables.icons.materialsymbols.rounded.Language
import com.composables.icons.materialsymbols.rounded.Partly_cloudy_day
import com.composables.icons.materialsymbols.rounded.Quiz
import com.composables.icons.materialsymbols.rounded.Screenshot_monitor
import com.composables.icons.materialsymbols.rounded.Smart_button
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Travel_explore
import com.composables.icons.materialsymbols.rounded.Alarm_add
import com.composables.icons.materialsymbols.rounded.Event_upcoming
import com.composables.icons.materialsymbols.rounded.Notification_add
import com.composables.icons.materialsymbols.rounded.Notifications_off
import com.composables.icons.materialsymbols.rounded.Timer
import com.composables.icons.materialsymbols.rounded.Account_tree
import com.composables.icons.materialsymbols.rounded.Forum
import com.composables.icons.materialsymbols.rounded.Article
import com.composables.icons.materialsymbols.rounded.File_save
import com.composables.icons.materialsymbols.rounded.Phone_android
import com.composables.icons.materialsymbols.rounded.Touch_app
import com.mwagent.R
import com.mwagent.mcp.McpTool
import com.mwagent.tools.AskUserTool
import com.mwagent.tools.ReadScreenTool
import com.mwagent.tools.ShowChartTool
import com.mwagent.tools.ShowDiagramTool
import com.mwagent.tools.ShowUiTool
import com.mwagent.tools.Tool
import com.mwagent.tools.chats.ExportChatTool
import com.mwagent.tools.chats.ListChatsTool
import com.mwagent.tools.chats.ReadChatTool
import com.mwagent.tools.chats.SearchMessagesTool
import com.mwagent.tools.files.ListFilesTool
import com.mwagent.tools.files.ReadFileTool
import com.mwagent.tools.files.SendFilesTool
import com.mwagent.tools.files.WriteFileTool
import com.mwagent.tools.location.LocationTool
import com.mwagent.tools.skills.SkillDeleteTool
import com.mwagent.tools.skills.SkillListTool
import com.mwagent.tools.skills.SkillRunTool
import com.mwagent.tools.skills.SkillSaveTool
import com.mwagent.tools.telegram.TelegramChatsTool
import com.mwagent.tools.telegram.TelegramMeTool
import com.mwagent.tools.telegram.TelegramMessagesTool
import com.mwagent.tools.telegram.TelegramSearchTool
import com.mwagent.tools.system.CopyToClipboardTool
import com.mwagent.tools.system.ReadClipboardTool
import com.mwagent.tools.apps.AddCalendarEventTool
import com.mwagent.tools.apps.ComposeMessageTool
import com.mwagent.tools.apps.DialTool
import com.mwagent.tools.apps.NavigateTool
import com.mwagent.tools.apps.OpenLinkTool
import com.mwagent.tools.apps.ShareTool
import com.mwagent.tools.media.DownloadMediaTool
import com.mwagent.tools.media.SaveToDownloadsTool
import com.mwagent.tools.web.CurrencyTool
import com.mwagent.tools.device.DeviceStatusTool
import com.mwagent.tools.device.FlashlightTool
import com.mwagent.tools.device.QrCodeTool
import com.mwagent.tools.device.SetVolumeTool
import com.mwagent.tools.telegram.TelegramSendTool
import com.mwagent.tools.memory.MemoryDeleteTool
import com.mwagent.tools.memory.MemorySaveTool
import com.mwagent.tools.memory.MemorySearchTool
import com.mwagent.tools.memory.MemoryUpdateTool
import com.mwagent.tools.reminders.CancelReminderTool
import com.mwagent.tools.automation.AddWatchTool
import com.mwagent.tools.automation.CancelTaskTool
import com.mwagent.tools.automation.ListTasksTool
import com.mwagent.tools.automation.ListWatchesTool
import com.mwagent.tools.automation.RemoveWatchTool
import com.mwagent.tools.automation.ScheduleTaskTool
import com.mwagent.tools.notifications.ReadNotificationsTool
import com.mwagent.tools.reminders.ListRemindersTool
import com.mwagent.tools.reminders.SetAlarmTool
import com.mwagent.tools.reminders.SetReminderTool
import com.mwagent.tools.reminders.SetTimerTool
import com.mwagent.tools.phone.PhoneActionTool
import com.mwagent.tools.phone.PhoneScreenTool
import com.mwagent.tools.phone.PhoneScreenshotTool
import com.mwagent.tools.system.RunCommandTool
import com.mwagent.tools.system.RunPythonTool
import com.mwagent.tools.weather.WeatherTool
import com.mwagent.tools.web.ReadWebpageTool
import com.mwagent.tools.web.WebSearchTool
import com.mwagent.ui.text.UiText

enum class ToolCategory(@StringRes val title: Int) {
    ASSISTANT(R.string.tools_category_assistant),
    MEMORY(R.string.tools_category_memory),
    CHATS(R.string.tools_category_chats),
    WEB(R.string.tools_category_web),
    SCREEN(R.string.tools_category_screen),
    PHONE(R.string.tools_category_phone),
    DEVICE(R.string.tools_category_device),
    APPS(R.string.tools_category_apps),
    TELEGRAM(R.string.tools_category_telegram),
    FILES(R.string.tools_category_files),
    TIME(R.string.tools_category_time),
    AUTOMATION(R.string.tools_category_automation),
    SYSTEM(R.string.tools_category_system),
    SKILLS(R.string.tools_category_skills),
    MCP(R.string.tools_category_mcp),
    OTHER(R.string.tools_category_other),
}

data class ToolInfo(val icon: ImageVector, val title: UiText, val summary: UiText, val category: ToolCategory)

object ToolCatalog {
    private val known = mapOf(
        AskUserTool.NAME to known(ToolCategory.ASSISTANT, MaterialSymbols.Rounded.Quiz, R.string.tools_ask_user_title, R.string.tools_ask_user_summary),
        MemorySaveTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Bookmark_add, R.string.tools_memory_save_title, R.string.tools_memory_save_summary),
        MemoryUpdateTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Edit_note, R.string.tools_memory_update_title, R.string.tools_memory_update_summary),
        MemoryDeleteTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Bookmark_remove, R.string.tools_memory_delete_title, R.string.tools_memory_delete_summary),
        MemorySearchTool.NAME to known(ToolCategory.MEMORY, MaterialSymbols.Rounded.Manage_search, R.string.tools_memory_search_title, R.string.tools_memory_search_summary),
        ListChatsTool.NAME to known(ToolCategory.CHATS, MaterialSymbols.Rounded.Forum, R.string.tools_list_chats_title, R.string.tools_list_chats_summary),
        ReadChatTool.NAME to known(ToolCategory.CHATS, MaterialSymbols.Rounded.Article, R.string.tools_read_chat_title, R.string.tools_read_chat_summary),
        SearchMessagesTool.NAME to known(ToolCategory.CHATS, MaterialSymbols.Rounded.Manage_search, R.string.tools_search_messages_title, R.string.tools_search_messages_summary),
        ExportChatTool.NAME to known(ToolCategory.CHATS, MaterialSymbols.Rounded.File_save, R.string.tools_export_chat_title, R.string.tools_export_chat_summary),
        WebSearchTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Travel_explore, R.string.tools_web_search_title, R.string.tools_web_search_summary),
        ReadWebpageTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Language, R.string.tools_read_webpage_title, R.string.tools_read_webpage_summary),
        CurrencyTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Currency_exchange, R.string.tools_currency_title, R.string.tools_currency_summary),
        DownloadMediaTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Download, R.string.tools_download_media_title, R.string.tools_download_media_summary),
        WeatherTool.NAME to known(ToolCategory.WEB, MaterialSymbols.Rounded.Partly_cloudy_day, R.string.tools_weather_title, R.string.tools_weather_summary),
        ReadScreenTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Screenshot_monitor, R.string.tools_read_screen_title, R.string.tools_read_screen_summary),
        ShowUiTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Smart_button, R.string.tools_show_ui_title, R.string.tools_show_ui_summary),
        ShowDiagramTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Account_tree, R.string.tools_show_diagram_title, R.string.tools_show_diagram_summary),
        ShowChartTool.NAME to known(ToolCategory.SCREEN, MaterialSymbols.Rounded.Bar_chart, R.string.tools_show_chart_title, R.string.tools_show_chart_summary),
        PhoneScreenTool.NAME to known(ToolCategory.PHONE, MaterialSymbols.Rounded.Phone_android, R.string.tools_phone_screen_title, R.string.tools_phone_screen_summary),
        PhoneScreenshotTool.NAME to known(ToolCategory.PHONE, MaterialSymbols.Rounded.Screenshot_monitor, R.string.tools_phone_screenshot_title, R.string.tools_phone_screenshot_summary),
        SkillSaveTool.NAME to known(ToolCategory.SKILLS, MaterialSymbols.Rounded.Construction, R.string.tools_skill_save_title, R.string.tools_skill_save_summary),
        SkillRunTool.NAME to known(ToolCategory.SKILLS, MaterialSymbols.Rounded.Play_circle, R.string.tools_skill_run_title, R.string.tools_skill_run_summary),
        SkillListTool.NAME to known(ToolCategory.SKILLS, MaterialSymbols.Rounded.Library_books, R.string.tools_skill_list_title, R.string.tools_skill_list_summary),
        SkillDeleteTool.NAME to known(ToolCategory.SKILLS, MaterialSymbols.Rounded.Delete, R.string.tools_skill_delete_title, R.string.tools_skill_delete_summary),
        TelegramMeTool.NAME to known(ToolCategory.TELEGRAM, MaterialSymbols.Rounded.Account_circle, R.string.tools_telegram_me_title, R.string.tools_telegram_me_summary),
        TelegramChatsTool.NAME to known(ToolCategory.TELEGRAM, MaterialSymbols.Rounded.Forum, R.string.tools_telegram_chats_title, R.string.tools_telegram_chats_summary),
        TelegramMessagesTool.NAME to known(ToolCategory.TELEGRAM, MaterialSymbols.Rounded.Chat, R.string.tools_telegram_messages_title, R.string.tools_telegram_messages_summary),
        TelegramSearchTool.NAME to known(ToolCategory.TELEGRAM, MaterialSymbols.Rounded.Manage_search, R.string.tools_telegram_search_title, R.string.tools_telegram_search_summary),
        TelegramSendTool.NAME to known(ToolCategory.TELEGRAM, MaterialSymbols.Rounded.Send, R.string.tools_telegram_send_title, R.string.tools_telegram_send_summary),
        LocationTool.NAME to known(ToolCategory.PHONE, MaterialSymbols.Rounded.Location_on, R.string.tools_location_title, R.string.tools_location_summary),
        PhoneActionTool.NAME to known(ToolCategory.PHONE, MaterialSymbols.Rounded.Touch_app, R.string.tools_phone_action_title, R.string.tools_phone_action_summary),
        RunCommandTool.NAME to known(ToolCategory.SYSTEM, MaterialSymbols.Rounded.Terminal, R.string.tools_run_command_title, R.string.tools_run_command_summary),
        RunPythonTool.NAME to known(ToolCategory.SYSTEM, MaterialSymbols.Rounded.Code, R.string.tools_run_python_title, R.string.tools_run_python_summary),
        DeviceStatusTool.NAME to known(ToolCategory.DEVICE, MaterialSymbols.Rounded.Battery_android_full, R.string.tools_device_status_title, R.string.tools_device_status_summary),
        FlashlightTool.NAME to known(ToolCategory.DEVICE, MaterialSymbols.Rounded.Flashlight_on, R.string.tools_flashlight_title, R.string.tools_flashlight_summary),
        SetVolumeTool.NAME to known(ToolCategory.DEVICE, MaterialSymbols.Rounded.Volume_up, R.string.tools_set_volume_title, R.string.tools_set_volume_summary),
        QrCodeTool.NAME to known(ToolCategory.DEVICE, MaterialSymbols.Rounded.Qr_code_2, R.string.tools_qr_code_title, R.string.tools_qr_code_summary),
        AddCalendarEventTool.NAME to known(ToolCategory.APPS, MaterialSymbols.Rounded.Event, R.string.tools_calendar_event_title, R.string.tools_calendar_event_summary),
        DialTool.NAME to known(ToolCategory.APPS, MaterialSymbols.Rounded.Call, R.string.tools_dial_title, R.string.tools_dial_summary),
        ComposeMessageTool.NAME to known(ToolCategory.APPS, MaterialSymbols.Rounded.Sms, R.string.tools_compose_message_title, R.string.tools_compose_message_summary),
        OpenLinkTool.NAME to known(ToolCategory.APPS, MaterialSymbols.Rounded.Open_in_new, R.string.tools_open_link_title, R.string.tools_open_link_summary),
        ShareTool.NAME to known(ToolCategory.APPS, MaterialSymbols.Rounded.Share, R.string.tools_share_title, R.string.tools_share_summary),
        NavigateTool.NAME to known(ToolCategory.APPS, MaterialSymbols.Rounded.Directions, R.string.tools_navigate_title, R.string.tools_navigate_summary),
        SaveToDownloadsTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Download_done, R.string.tools_save_downloads_title, R.string.tools_save_downloads_summary),
        CopyToClipboardTool.NAME to known(ToolCategory.SYSTEM, MaterialSymbols.Rounded.Content_copy, R.string.tools_copy_clipboard_title, R.string.tools_copy_clipboard_summary),
        ReadClipboardTool.NAME to known(ToolCategory.SYSTEM, MaterialSymbols.Rounded.Content_paste, R.string.tools_read_clipboard_title, R.string.tools_read_clipboard_summary),
        ListFilesTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Folder_open, R.string.tools_list_files_title, R.string.tools_list_files_summary),
        ReadFileTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Description, R.string.tools_read_file_title, R.string.tools_read_file_summary),
        WriteFileTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Edit_document, R.string.tools_write_file_title, R.string.tools_write_file_summary),
        SendFilesTool.NAME to known(ToolCategory.FILES, MaterialSymbols.Rounded.Perm_media, R.string.tools_send_files_title, R.string.tools_send_files_summary),
        SetReminderTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Notification_add, R.string.tools_set_reminder_title, R.string.tools_set_reminder_summary),
        ListRemindersTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Event_upcoming, R.string.tools_list_reminders_title, R.string.tools_list_reminders_summary),
        CancelReminderTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Notifications_off, R.string.tools_cancel_reminder_title, R.string.tools_cancel_reminder_summary),
        ScheduleTaskTool.NAME to known(ToolCategory.AUTOMATION, MaterialSymbols.Rounded.Event_repeat, R.string.tools_schedule_task_title, R.string.tools_schedule_task_summary),
        ListTasksTool.NAME to known(ToolCategory.AUTOMATION, MaterialSymbols.Rounded.Pending_actions, R.string.tools_list_tasks_title, R.string.tools_list_tasks_summary),
        CancelTaskTool.NAME to known(ToolCategory.AUTOMATION, MaterialSymbols.Rounded.Event_busy, R.string.tools_cancel_task_title, R.string.tools_cancel_task_summary),
        AddWatchTool.NAME to known(ToolCategory.AUTOMATION, MaterialSymbols.Rounded.Visibility, R.string.tools_add_watch_title, R.string.tools_add_watch_summary),
        ListWatchesTool.NAME to known(ToolCategory.AUTOMATION, MaterialSymbols.Rounded.Radar, R.string.tools_list_watches_title, R.string.tools_list_watches_summary),
        RemoveWatchTool.NAME to known(ToolCategory.AUTOMATION, MaterialSymbols.Rounded.Visibility_off, R.string.tools_remove_watch_title, R.string.tools_remove_watch_summary),
        ReadNotificationsTool.NAME to known(ToolCategory.DEVICE, MaterialSymbols.Rounded.Notifications_unread, R.string.tools_read_notifications_title, R.string.tools_read_notifications_summary),
        SetAlarmTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Alarm_add, R.string.tools_set_alarm_title, R.string.tools_set_alarm_summary),
        SetTimerTool.NAME to known(ToolCategory.TIME, MaterialSymbols.Rounded.Timer, R.string.tools_set_timer_title, R.string.tools_set_timer_summary),
    )

    fun of(toolName: String): ToolInfo = known[toolName] ?: when {
        McpTool.isMcpName(toolName) -> ToolInfo(MaterialSymbols.Rounded.Hub, UiText.Raw(McpTool.shortName(toolName)), UiText.Raw(""), ToolCategory.MCP)
        else -> ToolInfo(MaterialSymbols.Rounded.Build, UiText.Raw(toolName), UiText.Raw(""), ToolCategory.OTHER)
    }

    fun of(tool: Tool): ToolInfo = when (tool) {
        is McpTool -> ToolInfo(
            MaterialSymbols.Rounded.Hub,
            UiText.Raw("${tool.serverName} · ${tool.displayName}"),
            UiText.Raw(tool.remote.description.lineSequence().firstOrNull().orEmpty().take(MAX_SUMMARY_CHARS)),
            ToolCategory.MCP,
        )
        else -> of(tool.spec.name)
    }

    private fun known(category: ToolCategory, icon: ImageVector, @StringRes title: Int, @StringRes summary: Int): ToolInfo =
        ToolInfo(icon, UiText.Resource(title), UiText.Resource(summary), category)

    private const val MAX_SUMMARY_CHARS = 160
}
