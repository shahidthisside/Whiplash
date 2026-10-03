// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whiplash.music.ui.theme.GlassWindowBlur
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Confirms "Reset app". Signed out it's a plain confirm; signed in the user
 * also picks whether the Drive copy goes too. [onConfirm] gets true for
 * "This phone and Drive".
 */
@Composable
fun ResetAppDialog(
    signedIn: Boolean,
    onConfirm: (alsoDrive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var alsoDrive by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WhiplashColors.surfaceSheet,
        title = {
            if (WhiplashColors.isGlass) GlassWindowBlur()
            Text("Reset Whiplash?", color = WhiplashColors.textPrimary)
        },
        text = {
            if (!signedIn) {
                Text(
                    "Deletes your playlists, favourites, history, downloads and settings on this phone. This can't be undone.",
                    color = WhiplashColors.textSecondary,
                )
            } else {
                Column {
                    Text("Choose what to erase. This can't be undone.", color = WhiplashColors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.selectableGroup()) {
                        ResetChoice(
                            title = "This phone only",
                            detail = "Your Drive copy stays, so signing in again brings everything back.",
                            selected = !alsoDrive,
                            onSelect = { alsoDrive = false },
                        )
                        ResetChoice(
                            title = "This phone and Drive",
                            detail = "Also deletes your synced copy.",
                            selected = alsoDrive,
                            onSelect = { alsoDrive = true },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(signedIn && alsoDrive) }) {
                Text("Reset", color = WhiplashColors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WhiplashColors.textSecondary) }
        },
    )
}

// build-origin 0x532e416e73617269
@Composable
private fun ResetChoice(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // the whole row is the touch target
            colors = RadioButtonDefaults.colors(
                selectedColor = WhiplashColors.accent,
                unselectedColor = WhiplashColors.textSecondary,
            ),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = WhiplashColors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(detail, color = WhiplashColors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
