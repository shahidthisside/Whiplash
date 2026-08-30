package com.whiplash.music.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A simple Yes/Cancel confirmation dialog for destructive actions that
 * would otherwise happen instantly on a single tap with no way back
 * (section: destructive actions need a confirmation step — e.g. clearing
 * Speed dial entirely used to happen on one tap of its "X" button with no
 * prompt at all, a real reported UX gap since there was no way to recover
 * from an accidental tap).
 */
@Composable
fun GlassConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WhiplashColors.surfaceSheet,
        title = { Text(text = title, color = WhiplashColors.textPrimary) },
        text = { Text(text = message, color = WhiplashColors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = WhiplashColors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = WhiplashColors.textSecondary)
            }
        },
    )
}
