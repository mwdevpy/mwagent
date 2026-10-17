package com.mwagent.system.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(val name: String, val phones: List<String>, val emails: List<String>)

enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED, OTHER }

data class CallRecord(val name: String?, val number: String, val type: CallType, val at: Long, val seconds: Long)

class PhoneContacts(private val context: Context) {
    val canReadContacts: Boolean get() = granted(Manifest.permission.READ_CONTACTS)

    val canReadCalls: Boolean get() = granted(Manifest.permission.READ_CALL_LOG)

    suspend fun find(query: String, limit: Int): List<Contact> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val ids = LinkedHashSet<Long>()
        resolver.query(filterUri(ContactsContract.Contacts.CONTENT_FILTER_URI, query), arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && ids.size < limit) ids += cursor.getLong(0)
        }
        if (query.any(Char::isDigit)) {
            resolver.query(filterUri(PhoneLookup.CONTENT_FILTER_URI, query), arrayOf(PhoneLookup.CONTACT_ID), null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && ids.size < limit) ids += cursor.getLong(0)
            }
        }
        ids.mapNotNull { contact(resolver, it) }
    }

    suspend fun calls(query: String?, type: CallType?, since: Long, limit: Int): List<CallRecord> = withContext(Dispatchers.IO) {
        val projection = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        val result = mutableListOf<CallRecord>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, "${CallLog.Calls.DATE} >= ?", arrayOf(since.toString()), "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && result.size < limit) {
                val record = CallRecord(
                    name = cursor.getString(0)?.takeIf { it.isNotBlank() },
                    number = cursor.getString(1).orEmpty(),
                    type = callType(cursor.getInt(2)),
                    at = cursor.getLong(3),
                    seconds = cursor.getLong(4),
                )
                val fits = (type == null || record.type == type) &&
                    (query == null || record.name?.contains(query, ignoreCase = true) == true || digits(record.number).contains(digits(query).ifEmpty { query }))
                if (fits) result += record
            }
        }
        result
    }

    private fun contact(resolver: ContentResolver, id: Long): Contact? {
        val name = resolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), "${ContactsContract.Contacts._ID} = ?", arrayOf(id.toString()), null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
        val phones = values(resolver, Phone.CONTENT_URI, Phone.CONTACT_ID, id, Phone.NUMBER, Phone.TYPE, Phone.LABEL) { type, label ->
            Phone.getTypeLabel(context.resources, type, label).toString()
        }
        val emails = values(resolver, Email.CONTENT_URI, Email.CONTACT_ID, id, Email.ADDRESS, Email.TYPE, Email.LABEL) { type, label ->
            Email.getTypeLabel(context.resources, type, label).toString()
        }
        return Contact(name, phones.distinct(), emails.distinct())
    }

    private fun values(
        resolver: ContentResolver,
        uri: Uri,
        owner: String,
        id: Long,
        value: String,
        type: String,
        label: String,
        describe: (Int, String?) -> String,
    ): List<String> = resolver.query(uri, arrayOf(value, type, label), "$owner = ?", arrayOf(id.toString()), null)?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val text = cursor.getString(0)?.trim().orEmpty()
                if (text.isNotEmpty()) add("$text (${describe(cursor.getInt(1), cursor.getString(2))})")
            }
        }
    }.orEmpty()

    private fun filterUri(base: Uri, query: String): Uri = Uri.withAppendedPath(base, Uri.encode(query))

    private fun callType(raw: Int): CallType = when (raw) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> CallType.REJECTED
        else -> CallType.OTHER
    }

    private fun digits(text: String): String = text.filter(Char::isDigit)

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
