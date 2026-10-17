package com.mwagent.system.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The system clipboard. Android only lets an app read it while the app is on screen. */
class DeviceClipboard(private val context: Context) {
    private val manager: ClipboardManager? get() = context.getSystemService(ClipboardManager::class.java)

    suspend fun copy(text: String, sensitive: Boolean) = withContext(Dispatchers.Main) {
        val clip = ClipData.newPlainText(LABEL, text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        val clipboard = manager ?: error("The clipboard is not available")
        clipboard.setPrimaryClip(clip)
    }

    /** The clipboard text, or null when it is empty, not text, or Android hides it because the app is in the background. */
    suspend fun read(): String? = withContext(Dispatchers.Main) {
        val clip = manager?.primaryClip?.takeIf { it.itemCount > 0 } ?: return@withContext null
        clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf(String::isNotEmpty)
    }

    private companion object {
        const val LABEL = "MW AGENT"
    }
}
