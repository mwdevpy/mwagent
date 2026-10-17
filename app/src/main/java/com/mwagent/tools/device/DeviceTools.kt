package com.mwagent.tools.device

import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.device.DeviceControls
import com.mwagent.system.device.DeviceStatus
import com.mwagent.system.device.QrCodes
import com.mwagent.system.device.VolumeStream
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.booleanProperty
import com.mwagent.tools.enumProperty
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class DeviceStatusTool(private val status: DeviceStatus) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "The phone's current state: battery level, charging, temperature, battery saver, network type and VPN, free storage and RAM, " +
            "ringer mode and volume levels, screen brightness and time since restart. Use it for questions about the phone itself.",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome =
        ToolOutcome(withContext(Dispatchers.IO) { status.report() })

    companion object {
        const val NAME = "device_status"
    }
}

class FlashlightTool(private val controls: DeviceControls) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Turns the phone's flashlight on or off.",
        parameters = objectSchema(required = listOf(ON)) {
            booleanProperty(ON, "True to turn it on, false to turn it off")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val on = arguments.optionalBoolean(ON) ?: throw ToolArgumentException("Say whether to turn it on or off in '$ON'")
        if (!controls.setFlashlight(on)) return ToolOutcome.error("The flashlight is not available: the phone has no flash or the camera is in use")
        return ToolOutcome(if (on) "Flashlight on." else "Flashlight off.")
    }

    companion object {
        const val NAME = "flashlight"
        private const val ON = "on"
    }
}

class SetVolumeTool(private val controls: DeviceControls) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Sets one of the phone's volumes in percent: media (music, video), ring, notifications, alarm or calls. 0 mutes it.",
        parameters = objectSchema(required = listOf(STREAM, PERCENT)) {
            enumProperty(STREAM, "Which volume", VolumeStream.entries.map { it.label })
            integerProperty(PERCENT, "New level from 0 to 100", 0, MAX_PERCENT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val label = arguments.requireString(STREAM)
        val stream = VolumeStream.entries.firstOrNull { it.label == label }
            ?: throw ToolArgumentException("'$STREAM' is one of ${VolumeStream.entries.joinToString { it.label }}")
        val percent = arguments.optionalInt(PERCENT) ?: throw ToolArgumentException("Give the level in '$PERCENT'")
        val result = withContext(Dispatchers.Main) { controls.setVolume(stream, percent) }
        return ToolOutcome("The ${stream.label} volume is now $result%.")
    }

    companion object {
        const val NAME = "set_volume"
        private const val STREAM = "stream"
        private const val PERCENT = "percent"
        private const val MAX_PERCENT = 100
    }
}

class QrCodeTool(private val attachments: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Makes a QR code picture and shows it in the chat. Good for links, Wi-Fi access (WIFI:T:WPA;S:<name>;P:<password>;;), " +
            "contacts (a vCard), phone numbers (tel:...), or any text someone should scan with their camera.",
        parameters = objectSchema(required = listOf(TEXT)) {
            stringProperty(TEXT, "What the QR code holds", maxLength = QrCodes.MAX_CHARS)
            stringProperty(NAME_ARG, "Short file name without extension, default qr")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val text = arguments.requireString(TEXT)
        if (text.length > QrCodes.MAX_CHARS) throw ToolArgumentException("A QR code holds at most ${QrCodes.MAX_CHARS} characters")
        val png = withContext(Dispatchers.Default) { QrCodes.png(text) }
        val name = arguments.optionalString(NAME_ARG)?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }?.take(MAX_NAME)?.ifEmpty { null } ?: "qr"
        val file = attachments.importBytes("$name.png", png, forModel = false)
        return ToolOutcome("The QR code is shown to the user.", attachments = listOf(file))
    }

    companion object {
        const val NAME = "make_qr_code"
        private const val TEXT = "text"
        private const val NAME_ARG = "name"
        private const val MAX_NAME = 40
    }
}
