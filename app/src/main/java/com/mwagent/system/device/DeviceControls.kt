package com.mwagent.system.device

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import kotlin.math.roundToInt

/** Flashlight and volume. Neither needs a runtime permission. */
class DeviceControls(private val context: Context) {

    /** Turns the flashlight on or off. Returns false when the phone has no flash or the camera is busy. */
    fun setFlashlight(on: Boolean): Boolean {
        val camera = context.getSystemService(CameraManager::class.java) ?: return false
        val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return false
        return runCatching { camera.setTorchMode(id, on) }.isSuccess
    }

    /** Sets a stream to [percent] of its range and returns the percent it actually got. */
    fun setVolume(stream: VolumeStream, percent: Int): Int {
        val audio = context.getSystemService(AudioManager::class.java) ?: error("Audio is not available")
        val max = audio.getStreamMaxVolume(stream.stream)
        val target = (percent.coerceIn(0, PERCENT) * max / PERCENT.toFloat()).roundToInt()
        try {
            audio.setStreamVolume(stream.stream, target, AudioManager.FLAG_SHOW_UI)
        } catch (e: SecurityException) {
            error("Android does not let apps change the ${stream.label} volume while Do Not Disturb is on")
        }
        return volumePercent(audio, stream)
    }

    private companion object {
        const val PERCENT = 100
    }
}
