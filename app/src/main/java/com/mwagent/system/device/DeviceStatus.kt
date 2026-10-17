package com.mwagent.system.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.util.Locale

/** A snapshot of the phone's state for the device_status tool. Needs no runtime permission. */
class DeviceStatus(private val context: Context) {

    fun report(): String = listOfNotNull(battery(), power(), network(), storage(), memory(), sound(), screen(), uptime()).joinToString("\n")

    private fun battery(): String? {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val full = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
        val parts = mutableListOf("${level * PERCENT / scale}%")
        parts += when {
            full -> "full"
            else -> when (sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "charging from a charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "charging over USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "charging wirelessly"
                else -> "not charging"
            }
        }
        context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining()?.takeIf { it > 0 }
            ?.let { parts += "full in about ${it / MS_PER_MINUTE} min" }
        sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            ?.let { parts += String.format(Locale.ROOT, "%.1f °C", it / TENTHS) }
        when (sticky.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> parts += "overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> parts += "battery worn out"
            BatteryManager.BATTERY_HEALTH_COLD -> parts += "too cold"
        }
        return "Battery: ${parts.joinToString()}"
    }

    private fun power(): String? {
        val power = context.getSystemService(PowerManager::class.java) ?: return null
        return if (power.isPowerSaveMode) "Battery saver: on" else null
    }

    private fun network(): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return "Network: offline"
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth tethering"
            else -> "connected"
        }
        val extras = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) add("metered")
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add("internet not confirmed")
            add("about ${caps.linkDownstreamBandwidthKbps / KBPS_PER_MBPS} Mbit/s down")
        }
        return "Network: $kind (${extras.joinToString()})"
    }

    private fun storage(): String {
        val folder = Environment.getExternalStorageDirectory().takeIf(File::exists) ?: context.filesDir
        val stat = StatFs(folder.path)
        return "Storage: ${gigabytes(stat.availableBytes)} free of ${gigabytes(stat.totalBytes)}"
    }

    private fun memory(): String? {
        val activity = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        return "RAM: ${gigabytes(info.availMem)} free of ${gigabytes(info.totalMem)}${if (info.lowMemory) ", running low" else ""}"
    }

    private fun sound(): String? {
        val audio = context.getSystemService(AudioManager::class.java) ?: return null
        val mode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "sound on"
        }
        val streams = VolumeStream.entries.joinToString { "${it.label} ${volumePercent(audio, it)}%" }
        return "Sound: $mode; volume $streams"
    }

    private fun screen(): String? {
        val resolver = context.contentResolver
        val brightness = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull() ?: return null
        val auto = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) }.getOrNull() ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        return "Screen brightness: ${brightness * PERCENT / MAX_BRIGHTNESS}%${if (auto) ", automatic" else ""}"
    }

    private fun uptime(): String {
        val minutes = SystemClock.elapsedRealtime() / MS_PER_MINUTE
        return "Since restart: ${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR} min"
    }

    private companion object {
        const val PERCENT = 100
        const val TENTHS = 10.0
        const val MS_PER_MINUTE = 60_000L
        const val MINUTES_PER_HOUR = 60
        const val KBPS_PER_MBPS = 1000
        const val MAX_BRIGHTNESS = 255
        const val BYTES_PER_GB = 1024.0 * 1024 * 1024

        fun gigabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f GB", bytes / BYTES_PER_GB)
    }
}

enum class VolumeStream(val stream: Int, val label: String) {
    MEDIA(AudioManager.STREAM_MUSIC, "media"),
    RING(AudioManager.STREAM_RING, "ring"),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, "notifications"),
    ALARM(AudioManager.STREAM_ALARM, "alarm"),
    CALL(AudioManager.STREAM_VOICE_CALL, "calls"),
}

internal fun volumePercent(audio: AudioManager, stream: VolumeStream): Int {
    val max = audio.getStreamMaxVolume(stream.stream).takeIf { it > 0 } ?: return 0
    return audio.getStreamVolume(stream.stream) * 100 / max
}
