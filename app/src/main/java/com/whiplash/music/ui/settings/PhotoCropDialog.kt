// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.WhiplashColors

/** Largest zoom past "photo just fills the circle". */
private const val MAX_ZOOM = 5f

/**
 * Adjust photo: drag to move and pinch (or use the slider) to zoom, with a
 * circle showing exactly what the profile picture will be. [onSave] gets the
 * chosen square in [bitmap]'s own pixels (left, top, size).
 */
@Composable
internal fun PhotoCropDialog(
    bitmap: Bitmap,
    onSave: (left: Int, top: Int, size: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    // Image centre relative to the frame centre, in screen pixels.
    var offset by remember { mutableStateOf(Offset.Zero) }
    var framePx by remember { mutableFloatStateOf(0f) }

    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    fun scaleFor(z: Float) = if (framePx <= 0f) 1f else framePx / minOf(w, h) * z

    // Keeps the photo covering the whole frame, so there are never empty edges.
    fun clamp(o: Offset, z: Float): Offset {
        val s = scaleFor(z)
        val maxX = ((w * s - framePx) / 2f).coerceAtLeast(0f)
        val maxY = ((h * s - framePx) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080809))
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Adjust photo",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Drag to move, pinch to zoom.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(24.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clipToBounds()
                    .semantics { contentDescription = "Photo framing area. Drag to move, pinch to zoom." },
            ) {
                val density = LocalDensity.current
                framePx = with(density) { maxWidth.toPx() }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                zoom = newZoom
                                offset = clamp(offset + pan, newZoom)
                            }
                        },
                ) {
                    val s = scaleFor(zoom)
                    val dw = w * s
                    val dh = h * s
                    val left = size.width / 2f + offset.x - dw / 2f
                    val top = size.height / 2f + offset.y - dh / 2f
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.Medium,
                    )
                    // Dim everything outside the circle.
                    val radius = size.minDimension / 2f
                    val scrim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(center = center, radius = radius))
                    }
                    drawPath(scrim, Color.Black.copy(alpha = 0.6f))
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = radius - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
                    // Rule-of-thirds guides inside the frame.
                    val guide = Color.White.copy(alpha = 0.18f)
                    for (i in 1..2) {
                        val p = size.width * i / 3f
                        drawLine(guide, Offset(p, 0f), Offset(p, size.height), strokeWidth = 1f)
                        drawLine(guide, Offset(0f, p), Offset(size.width, p), strokeWidth = 1f)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.ZoomOut, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = zoom,
                    onValueChange = { z ->
                        zoom = z
                        offset = clamp(offset, z)
                    },
                    valueRange = 1f..MAX_ZOOM,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Zoom" },
                    colors = SliderDefaults.colors(
                        thumbColor = WhiplashColors.accent,
                        activeTrackColor = WhiplashColors.accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ZoomIn, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                GlassButton(
                    text = "Save",
                    modifier = Modifier.weight(1f),
                    // Disabled until the frame is laid out, so there's always something framed to save.
                    enabled = framePx > 0f,
                    onClick = {
                        val s = scaleFor(zoom)
                        val side = framePx / s
                        val left = w / 2f - (framePx / 2f + offset.x) / s
                        val top = h / 2f - (framePx / 2f + offset.y) / s
                        onSave(left.toInt(), top.toInt(), side.toInt())
                    },
                )
            }
        }
    }
}
