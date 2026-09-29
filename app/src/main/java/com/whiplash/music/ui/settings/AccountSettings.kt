package com.whiplash.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whiplash.music.data.sync.CloudSyncManager
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import com.whiplash.music.ui.theme.glassFill
import kotlinx.coroutines.delay

/** Tile colour for Account & sync, matching the other section folders. */
internal val ACCOUNT_TINT = Color(0xFF7CC4FF)

/**
 * Top of the Settings start page: the signed-in profile, or an invitation to
 * sign in. Only shown while Account & sync is switched on.
 */
@Composable
internal fun AccountHeaderCard(
    state: CloudSyncManager.State,
    cardColor: Color,
    onOpen: () -> Unit,
    onSignIn: () -> Unit,
    photoVersion: Long = 0,
    showEmail: Boolean = true,
    showStatus: Boolean = true,
) {
    val shape = RoundedCornerShape(WhiplashRadius.large)
    val account = state.account
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassFill(shape, cardColor)
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = "Open Account & sync", onClick = onOpen)
            .padding(16.dp),
    ) {
        if (account != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(account, 56.dp, photoVersion)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name ?: account.email.substringBefore('@'),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = WhiplashColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showEmail) {
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = WhiplashColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Problems (sign in again, sync failed) always show, even when the status is hidden.
                    if (showStatus || state.error != null || state.needsSignIn) {
                        Spacer(Modifier.height(6.dp))
                        SyncStatusLine(state)
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = WhiplashColors.textTertiary,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CloudTile(48.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync your library",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = WhiplashColors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Sign in with Google to keep playlists, favourites and settings on all your devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SignInButton(state, onSignIn)
        }
    }
}

/** The profile at the top of the Account & sync page. */
@Composable
internal fun AccountProfileHero(
    state: CloudSyncManager.State,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onSignInAgain: () -> Unit,
    onEditPhoto: () -> Unit,
    onEditName: () -> Unit,
    photoVersion: Long = 0,
    showEmail: Boolean = true,
) {
    val account = state.account ?: return
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Change profile photo", onClick = onEditPhoto),
        ) {
            AccountAvatar(account, 84.dp, photoVersion)
            // Small camera badge: the photo is tappable.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WhiplashColors.accent)
                    .border(2.dp, WhiplashColors.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = WhiplashColors.onAccent, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = "Edit name", onClick = onEditName)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = account.name ?: account.email.substringBefore('@'),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = WhiplashColors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Edit, contentDescription = null, tint = WhiplashColors.textTertiary, modifier = Modifier.size(16.dp))
        }
        if (showEmail) {
            Text(
                text = account.email,
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.lastSyncMs != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(countLabel(state.playlists, "playlist"))
                StatPill(countLabel(state.favorites, "favourite"))
            }
        }
        Spacer(Modifier.height(12.dp))
        SyncStatusLine(state, centered = true)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd)) {
            if (state.needsSignIn) {
                GlassButton(text = "Sign in again", onClick = onSignInAgain, modifier = Modifier.weight(1f), enabled = !state.signingIn)
            } else {
                GlassButton(text = if (state.syncing) "Syncing…" else "Sync now", onClick = onSyncNow, modifier = Modifier.weight(1f), enabled = !state.syncing)
            }
            GlassButton(text = "Sign out", onClick = onSignOut, modifier = Modifier.weight(1f))
        }
    }
}

/** The sign-in invitation on the Account & sync page. */
@Composable
internal fun AccountSignInPrompt(state: CloudSyncManager.State, onSignIn: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CloudTile(64.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sync your library",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = WhiplashColors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your data is kept in a private folder in your own Google Drive that only Whiplash can open.",
            style = MaterialTheme.typography.bodySmall,
            color = WhiplashColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SignInButton(state, onSignIn)
    }
}

@Composable
private fun SignInButton(state: CloudSyncManager.State, onSignIn: () -> Unit) {
    GlassButton(
        text = if (state.signingIn) "Signing in…" else "Sign in with Google",
        onClick = onSignIn,
        enabled = state.available && !state.signingIn,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!state.available) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Not available on this device: it needs Google Play services.",
            style = MaterialTheme.typography.bodySmall,
            color = WhiplashColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Google profile photo in a circle, or the account's first letter when there's none. */
@Composable
internal fun AccountAvatar(account: CloudSyncManager.Account, size: Dp, photoVersion: Long = 0) {
    val ring = com.whiplash.music.ui.theme.readableTint(ACCOUNT_TINT)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ring.copy(alpha = 0.18f))
            .border(1.5.dp, ring.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (account.name ?: account.email).trim().take(1).uppercase(),
            color = ring,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (account.photoUrl != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(account.photoUrl, photoVersion) {
                coil.request.ImageRequest.Builder(context)
                    .data(if (account.photoUrl.startsWith("/")) java.io.File(account.photoUrl) else account.photoUrl)
                    // A new photo reuses the same file, so key the cache by its version.
                    .memoryCacheKey("${account.photoUrl}#$photoVersion")
                    .diskCacheKey("${account.photoUrl}#$photoVersion")
                    .crossfade(true)
                    .build()
            }
            coil.compose.AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

@Composable
private fun CloudTile(size: Dp) {
    val tint = com.whiplash.music.ui.theme.readableTint(ACCOUNT_TINT)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Filled.CloudSync, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun StatPill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = WhiplashColors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(WhiplashColors.textPrimary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A coloured dot and "Synced 2 min ago" / "Syncing…" / the last error. */
@Composable
private fun SyncStatusLine(state: CloudSyncManager.State, centered: Boolean = false) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lastSyncMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val error = state.error
    val (dot, text) = when {
        state.syncing -> null to "Syncing…"
        state.needsSignIn -> WARNING to "Sign in again to keep syncing"
        state.waitingForNetwork -> WARNING to "Offline · will sync when online"
        error != null -> WARNING to error
        state.lastSyncMs != null -> OK to "Synced ${relativeTime(state.lastSyncMs, now)}"
        else -> null to "Not synced yet"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
    ) {
        if (state.syncing) {
            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = WhiplashColors.textSecondary)
        } else if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        }
        if (state.syncing || dot != null) Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = WhiplashColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val OK = Color(0xFF5BD68A)
private val WARNING = Color(0xFFF2B35B)

internal fun countLabel(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

/** "just now", "5 min ago", "3 h ago", or a date. */
internal fun relativeTime(thenMs: Long, nowMs: Long): String {
    val diff = (nowMs - thenMs).coerceAtLeast(0)
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} h ago"
        else -> "on " + java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(thenMs))
    }
}

/** One-line summary for the Account & sync folder row. */
internal fun accountFolderSummary(enabled: Boolean, state: CloudSyncManager.State, showEmail: Boolean = true): String = when {
    !enabled -> "Off"
    state.account == null -> "Not signed in"
    showEmail -> state.account.email
    else -> "Signed in as ${state.account.name ?: "you"}"
}
