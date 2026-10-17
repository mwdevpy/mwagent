package com.mwagent.telegram

import com.mwagent.data.telegram.TelegramAccount
import com.mwagent.data.telegram.TelegramAccountStore
import com.mwagent.system.python.PythonRuntime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Telegram's own error names, as returned by the tg_user Python module. */
object TelegramErrors {
    const val PHONE_INVALID = "PhoneNumberInvalidError"
    const val CODE_INVALID = "PhoneCodeInvalidError"
    const val CODE_EXPIRED = "PhoneCodeExpiredError"
    const val PASSWORD_INVALID = "PasswordHashInvalidError"
    const val FLOOD_WAIT = "FloodWaitError"
}

/** One reply from tg_user.py. Only the fields of the step that was called are filled in. */
@Serializable
data class TelegramReply(
    val error: String? = null,
    val seconds: Int? = null,
    val session: String? = null,
    @SerialName("phone_code_hash") val phoneCodeHash: String? = null,
    @SerialName("need_password") val needPassword: Boolean = false,
    @SerialName("user_id") val userId: Long? = null,
    val username: String = "",
    @SerialName("first_name") val firstName: String = "",
    val ok: Boolean = false,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): TelegramReply = json.decodeFromString(serializer(), raw)
    }
}

/** Talks to the user API through Telethon. Every call runs in the bundled Python on a background thread. */
class TelegramUserApi(
    private val python: PythonRuntime,
    private val accounts: TelegramAccountStore,
) {

    /** Hands the linked account to the `tg` module for the next script. Without a linked account it clears it. */
    suspend fun prepareScripts() {
        val account = accounts.current()
        if (account.linked && account.apiId != null) {
            python.call(MODULE, "configure", account.apiId, account.apiHash, account.session)
        } else {
            python.call(MODULE, "configure", null, null, null)
        }
    }

    suspend fun sendCode(account: TelegramAccount, phone: String): TelegramReply =
        call("send_code", account.apiId, account.apiHash, phone, account.session)

    suspend fun signIn(account: TelegramAccount, code: String, password: String): TelegramReply =
        call("sign_in", account.apiId, account.apiHash, account.session, account.phone, code, account.pendingCodeHash, password)

    suspend fun logOut(account: TelegramAccount): TelegramReply =
        call("log_out", account.apiId, account.apiHash, account.session)

    private suspend fun call(function: String, vararg args: Any?): TelegramReply =
        TelegramReply.parse(python.call(MODULE, function, *args))

    private companion object {
        const val MODULE = "tg_user"
    }
}
