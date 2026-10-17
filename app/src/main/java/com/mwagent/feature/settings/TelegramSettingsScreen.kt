package com.mwagent.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mwagent.R
import com.mwagent.data.telegram.TelegramAccount
import com.mwagent.data.telegram.TelegramAccountStore
import com.mwagent.data.telegram.TelegramStep
import com.mwagent.telegram.TelegramErrors
import com.mwagent.telegram.TelegramReply
import com.mwagent.telegram.TelegramUserApi
import com.mwagent.ui.components.FormField
import com.mwagent.ui.components.SectionHeader
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.appViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A failed step: a Telegram error name from [TelegramErrors], or one of the local codes below. */
data class TelegramError(val code: String, val seconds: Int = 0)

class TelegramSettingsViewModel(
    private val accounts: TelegramAccountStore,
    private val api: TelegramUserApi,
) : ViewModel() {
    val account: StateFlow<TelegramAccount?> = accounts.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<TelegramError?>(null)
    val error: StateFlow<TelegramError?> = _error.asStateFlow()

    fun saveKeys(apiIdText: String, apiHash: String) {
        val apiId = apiIdText.trim().toIntOrNull()
        val hash = apiHash.trim()
        if (apiId == null || hash.isEmpty()) {
            _error.value = TelegramError(LOCAL_KEYS_INVALID)
            return
        }
        attempt {
            accounts.save(accounts.current().copy(apiId = apiId, apiHash = hash))
        }
    }

    fun sendCode(phone: String) = attempt {
        val current = accounts.current()
        val normalized = phone.trim()
        val reply = api.sendCode(current, normalized)
        if (!reply.failed()) {
            accounts.save(
                current.copy(
                    phone = normalized,
                    session = reply.session ?: current.session,
                    pendingCodeHash = reply.phoneCodeHash,
                    needsPassword = false,
                ),
            )
        }
        reply
    }

    fun submitCode(code: String) = attempt {
        val current = accounts.current()
        val reply = api.signIn(current, code.trim(), password = "")
        when {
            reply.failed() -> reply
            reply.needPassword -> {
                accounts.save(current.copy(session = reply.session ?: current.session, needsPassword = true))
                reply
            }
            else -> {
                saveSignedIn(current, reply)
                reply
            }
        }
    }

    fun submitPassword(password: String) = attempt {
        val current = accounts.current()
        val reply = api.signIn(current, code = "", password = password)
        if (!reply.failed()) saveSignedIn(current, reply)
        reply
    }

    fun cancelPending() = attempt {
        accounts.save(accounts.current().copy(pendingCodeHash = null, needsPassword = false))
    }

    fun unlink() = attempt {
        val current = accounts.current()
        val reply = api.logOut(current)
        if (reply.failed()) {
            reply
        } else {
            accounts.save(current.signedOut())
            reply
        }
    }

    private suspend fun saveSignedIn(current: TelegramAccount, reply: TelegramReply) {
        accounts.save(
            current.copy(
                session = reply.session ?: current.session,
                pendingCodeHash = null,
                needsPassword = false,
                userId = reply.userId,
                username = reply.username,
                firstName = reply.firstName,
            ),
        )
    }

    /** Runs one step: shows progress, reports Telegram's error, and never lets an exception escape. */
    private fun attempt(step: suspend () -> Any?) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val result = step()
                if (result is TelegramReply && result.failed()) {
                    _error.value = TelegramError(result.error.orEmpty(), result.seconds ?: 0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = TelegramError(GENERIC)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun TelegramReply.failed(): Boolean = error != null

    companion object {
        const val LOCAL_KEYS_INVALID = "keys_invalid"
        const val GENERIC = "generic"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun TelegramSettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { TelegramSettingsViewModel(it.telegramAccounts, it.telegram) }
    val account by viewModel.account.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_telegram), onBack) { padding ->
        val current = account ?: return@SettingsScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.telegram_intro), style = MaterialTheme.typography.bodyMedium)
            when (current.step()) {
                TelegramStep.CREDENTIALS -> KeysForm(viewModel, current, busy)
                TelegramStep.PHONE -> PhoneForm(viewModel, busy)
                TelegramStep.CODE -> CodeForm(viewModel, current, busy)
                TelegramStep.PASSWORD -> PasswordForm(viewModel, busy)
                TelegramStep.LINKED -> LinkedCard(viewModel, current, busy)
            }
            error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (busy) CircularProgressIndicator()
        }
    }
}

@Composable
private fun KeysForm(viewModel: TelegramSettingsViewModel, current: TelegramAccount, busy: Boolean) {
    var apiId by rememberSaveable { mutableStateOf(current.apiId?.toString().orEmpty()) }
    var apiHash by rememberSaveable { mutableStateOf(current.apiHash) }
    SectionHeader(stringResource(R.string.telegram_keys_title), topPadding = 8.dp)
    FormField(
        value = apiId,
        onValueChange = { apiId = it },
        label = stringResource(R.string.telegram_api_id),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    FormField(
        value = apiHash,
        onValueChange = { apiHash = it },
        label = stringResource(R.string.telegram_api_hash),
    )
    Button(onClick = { viewModel.saveKeys(apiId, apiHash) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.telegram_save_keys))
    }
}

@Composable
private fun PhoneForm(viewModel: TelegramSettingsViewModel, busy: Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }
    SectionHeader(stringResource(R.string.telegram_login_title), topPadding = 8.dp)
    FormField(
        value = phone,
        onValueChange = { phone = it },
        label = stringResource(R.string.telegram_phone),
        placeholder = stringResource(R.string.telegram_phone_hint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Button(onClick = { viewModel.sendCode(phone) }, enabled = !busy && phone.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.telegram_send_code))
    }
}

@Composable
private fun CodeForm(viewModel: TelegramSettingsViewModel, current: TelegramAccount, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    SectionHeader(stringResource(R.string.telegram_login_title), topPadding = 8.dp)
    Text(stringResource(R.string.telegram_code_hint, current.phone), style = MaterialTheme.typography.bodyMedium)
    FormField(
        value = code,
        onValueChange = { code = it },
        label = stringResource(R.string.telegram_code),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Button(onClick = { viewModel.submitCode(code) }, enabled = !busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.telegram_confirm))
    }
    TextButton(onClick = viewModel::cancelPending, enabled = !busy) {
        Text(stringResource(R.string.telegram_other_number))
    }
}

@Composable
private fun PasswordForm(viewModel: TelegramSettingsViewModel, busy: Boolean) {
    var password by rememberSaveable { mutableStateOf("") }
    SectionHeader(stringResource(R.string.telegram_login_title), topPadding = 8.dp)
    FormField(
        value = password,
        onValueChange = { password = it },
        label = stringResource(R.string.telegram_password),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Button(onClick = { viewModel.submitPassword(password) }, enabled = !busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.telegram_confirm))
    }
    TextButton(onClick = viewModel::cancelPending, enabled = !busy) {
        Text(stringResource(R.string.telegram_other_number))
    }
}

@Composable
private fun LinkedCard(viewModel: TelegramSettingsViewModel, current: TelegramAccount, busy: Boolean) {
    SectionHeader(stringResource(R.string.telegram_linked_title), topPadding = 8.dp)
    Text(
        text = when {
            current.username.isNotEmpty() -> stringResource(R.string.telegram_linked_as, current.username)
            current.firstName.isNotEmpty() -> stringResource(R.string.telegram_linked_name, current.firstName)
            else -> stringResource(R.string.telegram_linked_plain)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(stringResource(R.string.telegram_unlink_hint), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = viewModel::unlink, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.telegram_unlink))
    }
}

@Composable
private fun errorText(error: TelegramError): String = when (error.code) {
    LOCAL_KEYS_INVALID_CODE -> stringResource(R.string.telegram_error_keys)
    TelegramErrors.PHONE_INVALID -> stringResource(R.string.telegram_error_phone)
    TelegramErrors.CODE_INVALID -> stringResource(R.string.telegram_error_code)
    TelegramErrors.CODE_EXPIRED -> stringResource(R.string.telegram_error_code_expired)
    TelegramErrors.PASSWORD_INVALID -> stringResource(R.string.telegram_error_password)
    TelegramErrors.FLOOD_WAIT -> stringResource(R.string.telegram_error_flood, error.seconds)
    else -> stringResource(R.string.telegram_error_generic)
}

private const val LOCAL_KEYS_INVALID_CODE = TelegramSettingsViewModel.LOCAL_KEYS_INVALID
