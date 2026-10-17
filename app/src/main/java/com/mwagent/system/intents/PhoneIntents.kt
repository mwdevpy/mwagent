package com.mwagent.system.intents

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.mwagent.data.files.ViewableFile
import com.mwagent.ui.files.commonMimeType

/**
 * Opens other apps with everything filled in: the dialer, a message, a calendar event, navigation.
 * The user finishes the action in that app, so nothing is called, sent or saved without them.
 */
class PhoneIntents(private val context: Context, private val isAppVisible: () -> Boolean) {

    fun dial(number: String): IntentResult = launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    fun sms(number: String, text: String): IntentResult =
        launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).putExtra("sms_body", text))

    fun email(to: String, subject: String, body: String): IntentResult =
        launch(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, to.split(',', ';').map(String::trim).filter(String::isNotEmpty).toTypedArray())
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body),
        )

    fun navigate(destination: String, mode: TravelMode): IntentResult {
        val uri = Uri.Builder().scheme("https").authority("www.google.com").path("maps/dir/")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", destination)
            .appendQueryParameter("travelmode", mode.value)
            .build()
        val maps = launch(Intent(Intent.ACTION_VIEW, uri))
        return if (maps == IntentResult.NO_APP) launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))) else maps
    }

    fun calendarEvent(title: String, startMillis: Long, endMillis: Long, allDay: Boolean, location: String?, description: String?): IntentResult {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        description?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        return launch(intent)
    }

    fun openLink(url: Uri): IntentResult = launch(Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE))

    /** The system share sheet with text and/or files; files must sit in a folder the app's FileProvider serves. */
    fun share(text: String?, files: List<ViewableFile>): IntentResult {
        val uris = try {
            files.map { FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, it.file, it.name) }
        } catch (e: IllegalArgumentException) {
            return IntentResult.BLOCKED
        }
        val send = when {
            uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            uris.size == 1 -> Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
            else -> Intent(Intent.ACTION_SEND)
        }
        send.type = if (uris.isEmpty()) "text/plain" else commonMimeType(files.map { it.mimeType })
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        if (uris.isNotEmpty()) {
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        }
        return launch(Intent.createChooser(send, null))
    }

    private fun launch(intent: Intent): IntentResult {
        if (!isAppVisible()) return IntentResult.APP_HIDDEN
        if (intent.resolveActivity(context.packageManager) == null) return IntentResult.NO_APP
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            IntentResult.OPENED
        } catch (e: ActivityNotFoundException) {
            IntentResult.NO_APP
        } catch (e: SecurityException) {
            IntentResult.BLOCKED
        }
    }
}

private const val FILE_PROVIDER_SUFFIX = ".files"

enum class IntentResult { OPENED, APP_HIDDEN, NO_APP, BLOCKED }

enum class TravelMode(val value: String) { DRIVING("driving"), WALKING("walking"), TRANSIT("transit"), BICYCLING("bicycling") }
