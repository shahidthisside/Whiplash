// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.whiplash.music.domain.model.AudioOutput
import com.whiplash.music.domain.model.OutputCandidate
import com.whiplash.music.domain.model.pickActiveOutput

/** Current media output, kept live through AudioManager's device callback (no permission needed). */
@Composable
fun rememberAudioOutput(): AudioOutput {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var output by remember { mutableStateOf(readActiveOutput(audioManager)) }
    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                output = readActiveOutput(audioManager)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                output = readActiveOutput(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return output
}

fun AudioOutput.icon(): ImageVector = when (kind) {
    AudioOutput.Kind.BLUETOOTH -> Icons.Filled.Bluetooth
    AudioOutput.Kind.WIRED -> Icons.Filled.Headphones
    AudioOutput.Kind.USB -> Icons.Filled.Usb
    AudioOutput.Kind.HDMI -> Icons.Filled.Tv
    AudioOutput.Kind.SPEAKER -> Icons.Filled.Speaker
}

private fun readActiveOutput(audioManager: AudioManager): AudioOutput {
    val devices = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
    val candidates = devices.mapNotNull { device ->
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioOutput.Kind.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioOutput.Kind.WIRED
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> AudioOutput.Kind.USB
            AudioDeviceInfo.TYPE_HDMI -> AudioOutput.Kind.HDMI
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutput.Kind.SPEAKER
            else -> if (Build.VERSION.SDK_INT >= 31 && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) AudioOutput.Kind.BLUETOOTH else null
        } ?: return@mapNotNull null
        OutputCandidate(kind, device.productName?.toString())
    }
    return pickActiveOutput(candidates)
}

/**
 * Opens the system media output picker. Android 14+ has a public API;
 * Android 11–13 System UI listens for a broadcast; older versions fall
 * back to the Bluetooth settings page.
 */
fun openOutputSwitcher(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        val shown = runCatching { android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
        if (shown) return
    }
    if (Build.VERSION.SDK_INT >= 30) {
        val sent = runCatching {
            context.sendBroadcast(
                Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                    .setPackage("com.android.systemui")
                    .putExtra("package_name", context.packageName),
            )
        }.isSuccess
        if (sent) return
    }
    runCatching {
        context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
