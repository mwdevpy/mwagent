package com.mwagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Send
import com.mwagent.R
import com.mwagent.data.telegram.TelegramAccount
import com.mwagent.data.telegram.TelegramAccountStore
import com.mwagent.data.telegram.TelegramStep
import com.mwagent.telegram.TelegramErrors
import com.mwagent.telegram.TelegramReply
import com.mwagent.telegram.TelegramUserApi
import com.mwagent.ui.components.FormField
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
        val step = current.step()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard(step)
            if (step != TelegramStep.LINKED) {
                LinearProgressIndicator(
                    progress = { step.progress() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
            }
            when (step) {
                TelegramStep.CREDENTIALS -> KeysCard(viewModel, current, busy)
                TelegramStep.PHONE -> PhoneCard(viewModel, busy)
                TelegramStep.CODE -> CodeCard(viewModel, current, busy)
                TelegramStep.PASSWORD -> PasswordCard(viewModel, busy)
                TelegramStep.LINKED -> LinkedCard(viewModel, current, busy)
            }
            error?.let { ErrorCard(errorText(it)) }
        }
    }
}

@Composable
private fun HeroCard(step: TelegramStep) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MaterialSymbols.Rounded.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(step.title()),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.telegram_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun KeysCard(viewModel: TelegramSettingsViewModel, current: TelegramAccount, busy: Boolean) {
    var apiId by rememberSaveable { mutableStateOf(current.apiId?.toString().orEmpty()) }
    var apiHash by rememberSaveable { mutableStateOf(current.apiHash) }
    FormCard {
        FormField(
            value = apiId,
            onValueChange = { apiId = it },
            label = stringResource(R.string.telegram_api_id),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        FormField(
            value = apiHash,
            onValueChange = { apiHash = it },
            label = stringResource(R.string.telegram_api_hash),
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(stringResource(R.string.telegram_save_keys), enabled = !busy) {
            viewModel.saveKeys(apiId, apiHash)
        }
    }
}

@Composable
private fun PhoneCard(viewModel: TelegramSettingsViewModel, busy: Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }
    FormCard {
        FormField(
            value = phone,
            onValueChange = { phone = it },
            label = stringResource(R.string.telegram_phone),
            placeholder = stringResource(R.string.telegram_phone_hint),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        PrimaryButton(stringResource(R.string.telegram_send_code), enabled = !busy && phone.isNotBlank()) {
            viewModel.sendCode(phone)
        }
    }
}

@Composable
private fun CodeCard(viewModel: TelegramSettingsViewModel, current: TelegramAccount, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    FormCard {
        Text(
            stringResource(R.string.telegram_code_hint, current.phone),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(
            value = code,
            onValueChange = { code = it },
            label = stringResource(R.string.telegram_code),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        PrimaryButton(stringResource(R.string.telegram_confirm), enabled = !busy && code.isNotBlank()) {
            viewModel.submitCode(code)
        }
        SecondaryAction(stringResource(R.string.telegram_other_number), enabled = !busy, onClick = viewModel::cancelPending)
    }
}

@Composable
private fun PasswordCard(viewModel: TelegramSettingsViewModel, busy: Boolean) {
    var password by rememberSaveable { mutableStateOf("") }
    FormCard {
        Text(
            stringResource(R.string.telegram_password),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.telegram_password),
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        PrimaryButton(stringResource(R.string.telegram_confirm), enabled = !busy && password.isNotEmpty()) {
            viewModel.submitPassword(password)
        }
        SecondaryAction(stringResource(R.string.telegram_other_number), enabled = !busy, onClick = viewModel::cancelPending)
    }
}

@Composable
private fun LinkedCard(viewModel: TelegramSettingsViewModel, current: TelegramAccount, busy: Boolean) {
    val name = when {
        current.username.isNotEmpty() -> "@${current.username}"
        current.firstName.isNotEmpty() -> current.firstName
        else -> stringResource(R.string.telegram_linked_plain)
    }
    val initial = (current.firstName.ifEmpty { current.username }.firstOrNull() ?: '?').uppercaseChar().toString()
    FormCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.telegram_linked_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.telegram_unlink_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = viewModel::unlink,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text(stringResource(R.string.telegram_unlink))
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SecondaryAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Start,
        )
    }
}

private fun TelegramStep.progress(): Float = when (this) {
    TelegramStep.CREDENTIALS -> 0.2f
    TelegramStep.PHONE -> 0.4f
    TelegramStep.CODE -> 0.7f
    TelegramStep.PASSWORD -> 0.85f
    TelegramStep.LINKED -> 1f
}

private fun TelegramStep.title(): Int = when (this) {
    TelegramStep.CREDENTIALS -> R.string.telegram_step_keys
    TelegramStep.PHONE -> R.string.telegram_step_phone
    TelegramStep.CODE -> R.string.telegram_step_code
    TelegramStep.PASSWORD -> R.string.telegram_step_password
    TelegramStep.LINKED -> R.string.telegram_step_linked
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
