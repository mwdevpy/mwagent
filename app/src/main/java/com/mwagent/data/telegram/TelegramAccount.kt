package com.mwagent.data.telegram

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mwagent.data.settings.TextCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.telegramStore: DataStore<Preferences> by preferencesDataStore(name = "telegram")

/** Where the user is in the login flow. Derived from the saved account, never stored on its own. */
enum class TelegramStep { CREDENTIALS, PHONE, CODE, PASSWORD, LINKED }

/**
 * The user's own Telegram account, linked through the user API (MTProto via Telethon).
 * [apiId] and [apiHash] come from my.telegram.org. [session] is Telethon's StringSession and
 * is what keeps the account signed in. It is saved encrypted on the phone.
 */
@Serializable
data class TelegramAccount(
    val apiId: Int? = null,
    val apiHash: String = "",
    val phone: String = "",
    val session: String = "",
    val pendingCodeHash: String? = null,
    val needsPassword: Boolean = false,
    val userId: Long? = null,
    val username: String = "",
    val firstName: String = "",
) {
    val linked: Boolean get() = userId != null

    fun step(): TelegramStep = when {
        apiId == null || apiHash.isBlank() -> TelegramStep.CREDENTIALS
        linked -> TelegramStep.LINKED
        needsPassword -> TelegramStep.PASSWORD
        pendingCodeHash != null -> TelegramStep.CODE
        else -> TelegramStep.PHONE
    }

    /** Keeps the API keys and drops everything that belongs to a signed-in account. */
    fun signedOut(): TelegramAccount = TelegramAccount(apiId = apiId, apiHash = apiHash)
}

class TelegramAccountStore(private val store: DataStore<Preferences>, private val cipher: TextCipher) {
    constructor(context: Context, cipher: TextCipher) : this(context.telegramStore, cipher)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val account: Flow<TelegramAccount> = store.data
        .map { prefs -> decode(prefs[KEY]) }
        .distinctUntilChanged()

    suspend fun current(): TelegramAccount = account.first()

    suspend fun save(account: TelegramAccount) {
        store.edit { it[KEY] = cipher.encrypt(json.encodeToString(TelegramAccount.serializer(), account)) }
    }

    private fun decode(encrypted: String?): TelegramAccount {
        if (encrypted == null) return TelegramAccount()
        return runCatching { json.decodeFromString(TelegramAccount.serializer(), cipher.decrypt(encrypted)) }
            .getOrDefault(TelegramAccount())
    }

    private companion object {
        val KEY = stringPreferencesKey("telegram_account_encrypted")
    }
}
