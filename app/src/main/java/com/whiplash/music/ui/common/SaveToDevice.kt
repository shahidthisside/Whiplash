package com.whiplash.music.ui.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.whiplash.music.WhiplashApplication

/**
 * Returns an action that saves downloaded songs (by id) to Download/Whiplash
 * on the phone. On Android 8–9 it asks for storage permission first, and only
 * saves once it's granted; newer Android needs no permission.
 */
@Composable
fun rememberSaveToDevice(): (List<String>) -> Unit {
    val app = LocalContext.current.applicationContext as? WhiplashApplication ?: return {}
    val exporter = app.deviceExporter
    var pending by remember { mutableStateOf<List<String>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ids = pending
        pending = null
        if (granted && ids != null) {
            exporter.save(ids)
        } else if (!granted) {
            ToastController.show("Storage permission is needed to save songs to the device")
        }
    }
    return { ids ->
        if (ids.isNotEmpty()) {
            if (exporter.needsLegacyPermission()) {
                pending = ids
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                exporter.save(ids)
            }
        }
    }
}
