package com.whiplash.music.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.LocalGlassBackdrop
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.liquidGlass
import com.whiplash.music.ui.theme.glassShadow
import com.whiplash.music.ui.theme.glassMaterial
import androidx.compose.material.icons.filled.Check

/** One action for the selected songs. */
data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Shorthand for building a [SelectionAction] with a trailing lambda. */
fun action(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) =
    SelectionAction(label, icon, destructive, onClick)

/** What the app chrome shows while a list is in selection mode. */
class SelectionSession(
    val owner: Any,
    val selectedCount: Int,
    val totalCount: Int,
    /** Up to three actions shown in the bottom bar, followed by More. */
    val primary: List<SelectionAction>,
    val onMore: () -> Unit,
    val onClose: () -> Unit,
    val onSelectAll: () -> Unit,
    val onDeselectAll: () -> Unit,
)

/**
 * The one list currently selecting songs publishes its session here;
 * MainActivity swaps the tab bar for [SelectionActionBar] and lays
 * [SelectionTopBar] over the page title while it's set.
 */
object SelectionController {
    var session by mutableStateOf<SelectionSession?>(null)
        private set

    fun publish(value: SelectionSession) {
        session = value
    }

    /** Drops any session, e.g. when the app's screen is torn down. */
    fun reset() {
        session = null
    }

    /** Clears only if [owner] still holds the session (another list may have taken over). */
    fun clear(owner: Any) {
        if (session?.owner === owner) session = null
    }
}

/** "✕  N selected  ☐ All", drawn over the page's own title. */
@Composable
fun SelectionTopBar(session: SelectionSession, modifier: Modifier = Modifier) {
    val allSelected = session.selectedCount >= session.totalCount
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WhiplashColors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            // Covers only the page-title row (its centre matches the title's),
            // leaving a clear gap above a search field or tabs below it.
            .padding(top = 6.dp)
            .height(52.dp)
            .padding(horizontal = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainIconButton(contentDescription = "Cancel selection", onClick = session.onClose) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = WhiplashColors.textPrimary)
        }
        Text(
            text = "${session.selectedCount} selected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = WhiplashColors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = GlassTokens.spaceXs)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .clickable(role = Role.Checkbox) { if (allSelected) session.onDeselectAll() else session.onSelectAll() }
                .semantics { contentDescription = if (allSelected) "Deselect all" else "Select all" }
                .padding(horizontal = GlassTokens.spaceSm, vertical = GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("All", style = MaterialTheme.typography.labelLarge, color = WhiplashColors.textPrimary)
            Spacer(Modifier.size(6.dp))
            Icon(
                if (allSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (allSelected) WhiplashColors.accent else WhiplashColors.textSecondary,
            )
        }
    }
}

/**
 * Takes the tab bar's place (same container: a flat bar, or the floating
 * glass capsule in Liquid Glass) with the primary actions plus More.
 */
@Composable
fun SelectionActionBar(session: SelectionSession, moreIcon: ImageVector, modifier: Modifier = Modifier) {
    val floating = LocalGlassBackdrop.current != null
    val capsule = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
    val row: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (session.primary + SelectionAction("More", moreIcon, onClick = session.onMore)).forEach { action ->
                ActionItem(action, Modifier.weight(1f))
            }
        }
    }
    if (!floating) {
        Box(
            modifier
                .fillMaxWidth()
                .background(WhiplashColors.background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 6.dp, bottom = 4.dp),
        ) { row() }
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                .clip(capsule)
                .liquidGlass(shape = capsule, fallback = WhiplashColors.surfaceElevated, legibility = 0.4f)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) { row() }
    }
}

@Composable
private fun ActionItem(action: SelectionAction, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (action.destructive) WhiplashColors.error else WhiplashColors.textPrimary
    Column(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = action.onClick)
            .padding(vertical = 6.dp)
            .graphicsLayer { val s = if (pressed) 0.9f else 1f; scaleX = s; scaleY = s }
            .semantics { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Row in the More sheet, matching the song menu's rows. */
@Composable
fun SelectionSheetRow(action: SelectionAction) {
    val tint = if (action.destructive) WhiplashColors.error else WhiplashColors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(vertical = GlassTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = if (action.destructive) tint else WhiplashColors.textSecondary)
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.padding(start = GlassTokens.spaceMd),
        )
    }
}

/**
 * Swallows every touch on this element (and its children) while [block] is
 * true, e.g. a bar that is fading out and must not act on a stale selection.
 */
fun Modifier.blockTouches(block: Boolean): Modifier =
    if (!block) this else this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    .changes.forEach { it.consume() }
            }
        }
    }

// ---- Reusable selection for any list or grid ------------------------------

/** Selection state for a list of [T], keyed by [key]; survives rotation. */
class ItemSelection<T>(
    private val keysState: androidx.compose.runtime.MutableState<Set<String>>,
    private val items: () -> List<T>,
    private val key: (T) -> String,
) {
    val selecting: Boolean get() = keysState.value.isNotEmpty()
    fun isSelected(item: T): Boolean = key(item) in keysState.value
    fun toggle(item: T) {
        val k = key(item)
        keysState.value = if (k in keysState.value) keysState.value - k else keysState.value + k
    }
    fun start(item: T) { keysState.value = setOf(key(item)) }
    fun selectAll() { keysState.value = items().mapTo(HashSet()) { key(it) } }
    fun clear() { keysState.value = emptySet() }
    fun selected(): List<T> = items().filter { key(it) in keysState.value }.distinctBy(key)
    val total: Int get() = items().distinctBy(key).size
}

@Composable
fun <T> rememberItemSelection(items: List<T>, key: (T) -> String): ItemSelection<T> {
    val keys = androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.MutableState<Set<String>>, String>(
            save = { it.value.toList() },
            restore = { mutableStateOf(it.toSet()) },
        ),
    ) { mutableStateOf(emptySet()) }
    val latest = androidx.compose.runtime.rememberUpdatedState(items)
    // Items that disappear (deleted, refreshed) drop out; an empty list is
    // skipped because lists are briefly empty while reloading or rotating.
    androidx.compose.runtime.LaunchedEffect(items) {
        if (keys.value.isNotEmpty() && items.isNotEmpty()) {
            val present = items.mapTo(HashSet()) { key(it) }
            keys.value = keys.value.filterTo(HashSet()) { it in present }
        }
    }
    return remember(keys) { ItemSelection(keys, { latest.value }, key) }
}

/**
 * Hooks [selection] up to the app chrome (top bar + action bar), Back, and
 * a More sheet listing [more]. [noun] names the items ("playlist").
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectionChrome(
    selection: ItemSelection<T>,
    noun: String,
    primary: List<SelectionAction>,
    more: List<SelectionAction>,
) {
    val owner = remember { Any() }
    var showMore by remember { mutableStateOf(false) }
    val count = selection.selected().size
    androidx.activity.compose.BackHandler(enabled = selection.selecting) { selection.clear() }
    if (selection.selecting && count > 0) {
        androidx.compose.runtime.SideEffect {
            SelectionController.publish(
                SelectionSession(
                    owner = owner,
                    selectedCount = count,
                    totalCount = selection.total,
                    primary = primary,
                    onMore = { showMore = true },
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll() },
                    onDeselectAll = { selection.clear() },
                ),
            )
        }
    } else {
        androidx.compose.runtime.SideEffect { SelectionController.clear(owner) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SelectionController.clear(owner) } }
    if (showMore && selection.selecting) {
        com.whiplash.music.ui.theme.GlassSheet(onDismissRequest = { showMore = false }) {
            Column {
                Text(
                    text = "$count ${if (count == 1) noun else noun + "s"} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(vertical = GlassTokens.spaceSm),
                )
                more.forEach { a -> SelectionSheetRow(a.copy(onClick = { showMore = false; a.onClick() })) }
            }
        }
    }
}

/**
 * Selected look for a row or tile: a soft accent tint, or in Liquid Glass
 * the tab bar's raised 3D glass puck.
 */
@Composable
fun Modifier.selectedHighlight(
    selected: Boolean,
    shape: androidx.compose.foundation.shape.CornerBasedShape =
        androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.medium),
): Modifier {
    if (!selected) return this
    if (!WhiplashColors.isGlass) return this.background(WhiplashColors.accent.copy(alpha = 0.14f), shape)
    // Same recipe as FadeBottomBar's tab puck.
    return this
        .glassShadow(shape, elevation = 6.dp, strength = if (WhiplashColors.isLight) 0.16f else 0.28f)
        .clip(shape)
        .background(
            if (WhiplashColors.isLight) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)
            else WhiplashColors.textPrimary.copy(alpha = 0.14f),
        )
        .glassMaterial(shape)
}

/** Round accent checkmark shown over artwork while an item is selected. */
@Composable
fun SelectionCheck(visible: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 30.dp) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
    ) {
        Box(
            Modifier.size(size).background(WhiplashColors.accent, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.Check,
                contentDescription = null,
                tint = WhiplashColors.onAccent,
                modifier = Modifier.size(size * 0.66f),
            )
        }
    }
}
