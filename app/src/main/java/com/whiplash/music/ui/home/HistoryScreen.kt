package com.whiplash.music.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassConfirmDialog
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Full play history (section 31) — everything Speed dial's 3x3 grid is a
 * curated window into, reached via the "History" button next to Speed
 * dial's own "Clear" button.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(app.libraryRepository))
    val history by viewModel.history.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlainIconButton(contentDescription = "Back", onClick = onBack, size = 40.dp) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
                }
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(start = GlassTokens.spaceSm).weight(1f),
                )
                if (history.isNotEmpty()) {
                    PlainIconButton(contentDescription = "Clear history", onClick = { showClearConfirm = true }, size = 40.dp) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = WhiplashColors.textSecondary)
                    }
                }
            }

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Play something to see it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textSecondary,
                    )
                }
            } else {
                PlayableItemsList(items = history, onPlayQueue = onPlayQueue, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (showClearConfirm) {
        GlassConfirmDialog(
            title = "Clear history?",
            message = "This clears your recently played history, including what's shown in Speed dial. Pinned songs will stay. This can't be undone.",
            onConfirm = {
                viewModel.clearHistory()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}
