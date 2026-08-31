package com.whiplash.music.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A simple single-text-field confirmation dialog, used for playlist
 * create/rename (section 38) — the only two places in the app that need
 * free-text input from the user.
 */
@Composable
fun GlassTextInputDialog(
    title: String,
    initialValue: String = "",
    placeholder: String? = null,
    confirmLabel: String = "Save",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WhiplashColors.surfaceSheet,
        title = { Text(text = title, color = WhiplashColors.textPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = if (placeholder != null) {
                    { Text(text = placeholder, color = WhiplashColors.textTertiary) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WhiplashColors.textPrimary,
                    unfocusedTextColor = WhiplashColors.textPrimary,
                    focusedBorderColor = WhiplashColors.accent,
                    unfocusedBorderColor = WhiplashColors.glassBorderStrong,
                    cursorColor = WhiplashColors.accent,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
            ) {
                Text(text = confirmLabel, color = WhiplashColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = WhiplashColors.textSecondary)
            }
        },
    )
}
