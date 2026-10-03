// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.whiplash.music.data.local.WhiplashDatabase
import com.whiplash.music.data.repository.LyricOffsetStore
import com.whiplash.music.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

// (c) Shahid A. 2026, Whiplash. Do not copy.
/**
 * Optional Google account sync (Settings > Account & sync).
 *
 * The whole library lives in one small file in the user's own Google Drive,
 * inside the hidden app folder. Each sync downloads it (only if another device
 * changed it), merges it with this phone three-way ([SyncMerge]), writes back
 * whatever changed here, and uploads the result if the cloud copy differs.
 * Nothing is stored on any server run by the app.
 *
 * Signing out, or turning the feature off, never deletes anything on the
 * phone or in Drive; [deleteCloudCopy] is the only thing that removes data.
 */
class CloudSyncManager(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val database: WhiplashDatabase,
    private val lyricOffsetStore: LyricOffsetStore,
    private val localStore: LocalSyncStore,
    private val drive: DriveAppDataClient,
    private val auth: GoogleDriveAuth,
    /** The user's own profile name and photo (editable in Account & sync). */
    val profileStore: ProfileStore,
) {
    data class Account(val email: String, val name: String?, val photoUrl: String?)

    data class State(
        val available: Boolean = true,
        val account: Account? = null,
        val autoSync: Boolean = true,
        val signingIn: Boolean = false,
        val syncing: Boolean = false,
        val lastSyncMs: Long? = null,
        /** Why the last sync failed; null after a successful one. */
        val error: String? = null,
        /** Google access was withdrawn outside the app: signing in again fixes it. */
        val needsSignIn: Boolean = false,
        val playlists: Int = 0,
        val favorites: Int = 0,
        /** Changes are waiting and the phone is offline; they sync once it reconnects. */
        val waitingForNetwork: Boolean = false,
        /** "Choose what syncs" is on; otherwise everything syncs. */
        val selective: Boolean = false,
        /** Categories switched off in "Choose what syncs" (only used while [selective]). */
        val excluded: Set<SyncCategory> = emptySet(),
    )

    sealed interface SignInStep {
        data class NeedsUi(val intent: PendingIntent) : SignInStep
        data class Done(val account: Account) : SignInStep
        data class Failed(val message: String) : SignInStep
    }

    sealed interface SyncResult {
        data object Synced : SyncResult
        data class Failed(val message: String) : SyncResult
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val baseFile = File(appContext.filesDir, "cloud_sync/base.json.gz")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Changes our own sync writes cause shouldn't trigger another sync. */
    @Volatile private var ignoreChangesUntil = 0L

    private val connectivity = appContext.getSystemService(android.net.ConnectivityManager::class.java)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun readState(): State {
        val email = prefs.getString(KEY_EMAIL, null)
        return State(
            available = auth.isAvailable(),
            account = email?.let { Account(it, prefs.getString(KEY_NAME, null), prefs.getString(KEY_PHOTO, null)) },
            autoSync = prefs.getBoolean(KEY_AUTO_SYNC, true),
            lastSyncMs = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
            needsSignIn = prefs.getBoolean(KEY_NEEDS_SIGN_IN, false),
            playlists = prefs.getInt(KEY_PLAYLISTS, 0),
            favorites = prefs.getInt(KEY_FAVORITES, 0),
            waitingForNetwork = prefs.getBoolean(KEY_PENDING, false) && !isOnline(),
            selective = prefs.getBoolean(KEY_SELECTIVE, false),
            excluded = readExcluded(),
        )
    }

    /** What sync actually leaves out: nothing unless "Choose what syncs" is on. */
    private fun effectiveExcluded(): Set<SyncCategory> =
        if (prefs.getBoolean(KEY_SELECTIVE, false)) readExcluded() else emptySet()

    /** Off: everything syncs. On: only the chosen categories do (your last choice is kept). */
    fun setSelective(enabled: Boolean) {
        val before = effectiveExcluded()
        prefs.edit().putBoolean(KEY_SELECTIVE, enabled).apply()
        _state.update { it.copy(selective = enabled) }
        if (effectiveExcluded() != before) syncSoon()
    }

    /** The "All" chip: every category on, or every category off. */
    fun setAllCategories(enabled: Boolean) {
        val next = if (enabled) emptySet() else SyncCategory.entries.toSet()
        prefs.edit().putStringSet(KEY_EXCLUDED, next.map { it.name }.toSet()).apply()
        _state.update { it.copy(excluded = next) }
        if (enabled) syncSoon()
    }

    private fun syncSoon() {
        if (prefs.getString(KEY_EMAIL, null) == null) return
        scope.launch { if (isOnline()) sync() else setPending(true) }
    }

    private fun readExcluded(): Set<SyncCategory> =
        prefs.getStringSet(KEY_EXCLUDED, emptySet()).orEmpty().mapNotNull { runCatching { SyncCategory.valueOf(it) }.getOrNull() }.toSet()

    /** Turns one category's syncing on or off; the rest of the library keeps syncing. */
    fun setCategoryEnabled(category: SyncCategory, enabled: Boolean) {
        val next = if (enabled) readExcluded() - category else readExcluded() + category
        prefs.edit().putStringSet(KEY_EXCLUDED, next.map { it.name }.toSet()).apply()
        _state.update { it.copy(excluded = next) }
        if (enabled) syncSoon()
    }

    // ---- Connectivity -----------------------------------------------------


    /** True when the current network actually reaches the internet (not just "connected"). */
    fun isOnline(): Boolean = runCatching {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    /** True on mobile data or another network the system treats as metered. */
    fun isMetered(): Boolean = runCatching { connectivity?.isActiveNetworkMetered ?: false }.getOrDefault(false)

    /** Online/offline as it changes (current value first). Shared by Home's reconnect retry. */
    fun onlineChanges(): Flow<Boolean> = callbackFlow {
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { trySend(isOnline()) }
            override fun onLost(network: android.net.Network) { trySend(isOnline()) }
        }
        trySend(isOnline())
        val registered = runCatching { connectivity?.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose { if (registered) runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    /** Remembers (across restarts) that something changed here and hasn't reached the cloud yet. */
    private fun setPending(pending: Boolean) {
        prefs.edit().putBoolean(KEY_PENDING, pending).apply()
        _state.update { it.copy(waitingForNetwork = pending && !isOnline()) }
    }

    // ---- Sign-in ----------------------------------------------------------

    /** Starts sign-in; the UI launches [SignInStep.NeedsUi] and passes the result to [finishSignIn]. */
    suspend fun beginSignIn(): SignInStep {
        if (!auth.isAvailable()) return SignInStep.Failed("Google Play services isn't available on this device")
        _state.update { it.copy(signingIn = true) }
        return try {
            // Re-authorising the same account after access was withdrawn; otherwise let the user pick.
            val email = _state.value.account?.email?.takeIf { _state.value.needsSignIn }
            complete(auth.authorize(email))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "beginSignIn failed", e)
            _state.update { it.copy(signingIn = false) }
            SignInStep.Failed(GoogleDriveAuth.describe(e))
        }
    }

    suspend fun finishSignIn(data: Intent?): SignInStep = try {
        complete(auth.resultFromIntent(data))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "finishSignIn failed", e)
        _state.update { it.copy(signingIn = false) }
        SignInStep.Failed(GoogleDriveAuth.describe(e))
    }

    /** User closed the Google screen without choosing. */
    fun cancelSignIn() = _state.update { it.copy(signingIn = false) }

    private suspend fun complete(outcome: GoogleDriveAuth.Outcome): SignInStep {
        if (outcome is GoogleDriveAuth.Outcome.NeedsUi) return SignInStep.NeedsUi(outcome.intent)
        val granted = outcome as GoogleDriveAuth.Outcome.Granted
        val profile = if (granted.email == null || granted.name == null) runCatching { drive.profile(granted.token) }.getOrNull() else null
        val email = granted.email ?: profile?.email
            ?: return SignInStep.Failed("Couldn't read your Google account").also { _state.update { it.copy(signingIn = false) } }
        val account = Account(email, granted.name ?: profile?.name, granted.photoUrl ?: profile?.photoUrl)

        val previous = prefs.getString(KEY_EMAIL, null)
        if (previous != null && !previous.equals(email, ignoreCase = true)) forgetSyncState()
        prefs.edit()
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_NAME, account.name)
            .putString(KEY_PHOTO, account.photoUrl)
            .putBoolean(KEY_NEEDS_SIGN_IN, false)
            .apply()
        _state.update { it.copy(account = account, signingIn = false, needsSignIn = false, error = null) }
        scope.launch { sync() }
        return SignInStep.Done(account)
    }

    /** Signs out and withdraws access. Data on this phone and in Drive stays. */
    suspend fun signOut() {
        val email = prefs.getString(KEY_EMAIL, null)
        mutex.withLock {
            prefs.edit().remove(KEY_EMAIL).remove(KEY_NAME).remove(KEY_PHOTO).remove(KEY_NEEDS_SIGN_IN).apply()
            forgetSyncState()
            // Selective-sync choices belong to this account too; clear them so the
            // switch and what actually syncs can't disagree after signing in again.
            prefs.edit().remove(KEY_PENDING).remove(KEY_SELECTIVE).remove(KEY_EXCLUDED).apply()
            // The custom profile belongs to this account; it comes back from Drive on sign-in.
            profileStore.applyFromSync(null)
            _state.update { State(available = it.available, autoSync = it.autoSync) }
        }
        if (email != null) auth.revoke(email)
    }

    /** Removes the synced copy from Drive, then signs out. This phone keeps everything. */
    suspend fun deleteCloudCopy(): SyncResult {
        val result = mutex.withLock {
            try {
                withToken { token -> drive.deleteAll(token) }
                SyncResult.Synced
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "deleteCloudCopy failed", e)
                SyncResult.Failed(describeSyncError(e))
            }
        }
        if (result is SyncResult.Synced) signOut()
        return result
    }

    /** Set once "Reset app" starts; blocks every sync until the process is wiped. */
    @Volatile
    private var resetting = false

    /**
     * Account steps of "Reset app", run just before the phone is wiped.
     *
     * The wipe itself removes the signed-in account, the sync base and all
     * sync state, so the next launch is signed out and can never push an
     * empty library over Drive. Signing in again later is a first sync, which
     * combines both sides and so restores the Drive copy instead of deleting it.
     *
     * Before the wipe, syncs are blocked (including one already queued), and
     * with [alsoDrive] the Drive copy is deleted while holding the sync lock
     * so nothing can re-upload it. If that delete fails, reset is cancelled
     * and nothing is erased. Every wait is capped so reset can't hang.
     */
    suspend fun prepareAppReset(alsoDrive: Boolean): SyncResult {
        val email = prefs.getString(KEY_EMAIL, null) ?: return SyncResult.Synced
        resetting = true
        if (alsoDrive) {
            val deleted = withTimeoutOrNull(RESET_DRIVE_TIMEOUT_MS) {
                mutex.withLock {
                    try {
                        withToken { token -> drive.deleteAll(token) }
                        SyncResult.Synced
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "reset: Drive delete failed", e)
                        SyncResult.Failed(describeSyncError(e))
                    }
                }
            } ?: SyncResult.Failed("Timed out")
            if (deleted is SyncResult.Failed) {
                resetting = false
                return deleted
            }
        }
        // Best effort: withdraw Google access so the next sign-in asks again.
        withTimeoutOrNull(RESET_REVOKE_TIMEOUT_MS) { auth.revoke(email) }
        return SyncResult.Synced
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        _state.update { it.copy(autoSync = enabled) }
    }

    // ---- Sync -------------------------------------------------------------

    /** One full sync. Safe to call any time; concurrent calls run one after another. */
    suspend fun sync(): SyncResult {
        val email = prefs.getString(KEY_EMAIL, null) ?: return SyncResult.Failed("Not signed in")
        if (!isOnline()) {
            setPending(true)
            return SyncResult.Failed("You're offline. Changes will sync when you reconnect")
        }
        if (resetting) return SyncResult.Failed("Resetting")
        return mutex.withLock {
            // A sync queued behind "Reset app" must not run after the Drive copy is gone.
            if (resetting) return@withLock SyncResult.Failed("Resetting")
            _state.update { it.copy(syncing = true) }
            try {
                withToken { token -> syncOnce(token, email) }
                prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).putBoolean(KEY_PENDING, false).apply()
                _state.update {
                    it.copy(
                        syncing = false, error = null, waitingForNetwork = false, lastSyncMs = prefs.getLong(KEY_LAST_SYNC, 0L),
                        playlists = prefs.getInt(KEY_PLAYLISTS, 0), favorites = prefs.getInt(KEY_FAVORITES, 0),
                    )
                }
                SyncResult.Synced
            } catch (e: CancellationException) {
                _state.update { it.copy(syncing = false) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                val message = describeSyncError(e)
                // Keep the changes queued; the next reconnect or change retries.
                prefs.edit().putBoolean(KEY_PENDING, true).apply()
                val offline = !isOnline()
                _state.update { it.copy(syncing = false, error = if (offline) null else message, waitingForNetwork = offline) }
                SyncResult.Failed(message)
            }
        }
    }

    private suspend fun syncOnce(token: String, email: String) {
        repeat(MAX_ATTEMPTS) {
            val knownId = prefs.getString(KEY_FILE_ID, null)
            val knownVersion = prefs.getLong(KEY_VERSION, -1L)
            val remoteFile = knownId?.let { drive.meta(token, it) } ?: drive.find(token)

            var base = readBase(email)
            val remote: SyncSnapshot = when {
                remoteFile == null -> {
                    // No cloud copy (first sync, or it was deleted from another
                    // device). Treat it as a first sync, so nothing on this phone
                    // is mistaken for "deleted elsewhere".
                    base = null
                    SyncSnapshot()
                }
                // Unchanged since our last sync, so the base is an exact copy of
                // it; not when categories are off, since the base leaves them empty.
                base != null && remoteFile.id == knownId && remoteFile.version == knownVersion && effectiveExcluded().isEmpty() -> base
                else -> SyncCodec.decode(drive.download(token, remoteFile.id))
            }

            val local = localStore.read()
            val plan = SyncMerge.plan(SyncMerge.merge(base, local.snapshot, remote), local.snapshot, remote, effectiveExcluded())
            val toApply = plan.toApply
            val merged = plan.toUpload

            if (!toApply.sameContentAs(local.snapshot) || toApply.songs.keys.any { it !in local.snapshot.songs }) {
                ignoreChangesUntil = System.currentTimeMillis() + IGNORE_OWN_CHANGES_MS
                localStore.apply(local, toApply)
                ignoreChangesUntil = System.currentTimeMillis() + IGNORE_OWN_CHANGES_MS
            }

            val saved: DriveAppDataClient.RemoteFile = if (remoteFile == null || !merged.sameContentAs(remote)) {
                // Another device may have uploaded while we merged: start over
                // with its version rather than overwrite it.
                if (remoteFile != null) {
                    val latest = drive.meta(token, remoteFile.id)
                    if (latest != null && latest.version != remoteFile.version) return@repeat
                }
                val bytes = SyncCodec.encode(merged, Build.MODEL ?: "Android", System.currentTimeMillis())
                if (remoteFile == null) drive.create(token, bytes) else drive.update(token, remoteFile.id, bytes)
            } else {
                remoteFile
            }

            writeBase(email, plan.newBase)
            prefs.edit()
                .putString(KEY_FILE_ID, saved.id)
                .putLong(KEY_VERSION, saved.version)
                .putInt(KEY_PLAYLISTS, merged.playlists.size)
                .putInt(KEY_FAVORITES, merged.favorites.size)
                .apply()
            return
        }
        throw IllegalStateException("Library kept changing on another device")
    }

    /** Runs [block] with a token, refreshing it once if Drive rejects it. */
    private suspend fun <T> withToken(block: suspend (String) -> T): T {
        val email = prefs.getString(KEY_EMAIL, null) ?: throw IllegalStateException("Not signed in")
        var token = freshToken(email)
        return try {
            block(token)
        } catch (e: DriveAppDataClient.Unauthorized) {
            auth.clearToken(token)
            token = freshToken(email)
            block(token)
        }
    }

    private suspend fun freshToken(email: String): String =
        when (val outcome = auth.authorize(email)) {
            is GoogleDriveAuth.Outcome.Granted -> outcome.token
            is GoogleDriveAuth.Outcome.NeedsUi -> {
                prefs.edit().putBoolean(KEY_NEEDS_SIGN_IN, true).apply()
                _state.update { it.copy(needsSignIn = true) }
                throw NeedsSignIn()
            }
        }

    private class NeedsSignIn : Exception("Google access needs to be granted again")

    private fun describeSyncError(e: Throwable): String = when (e) {
        is NeedsSignIn -> "Sign in again to keep syncing"
        is SyncCodec.UnsupportedVersion -> "Update Whiplash to sync with your other devices"
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException -> "No internet connection"
        is DriveAppDataClient.DriveException -> if (e.code == 403 && e.message?.contains("storageQuota") == true) "Your Google Drive is full" else "Google Drive didn't respond"
        is com.google.android.gms.common.api.ApiException -> GoogleDriveAuth.describe(e)
        else -> "Couldn't sync"
    }

    // ---- Base snapshot ----------------------------------------------------

    private fun readBase(email: String): SyncSnapshot? {
        if (prefs.getString(KEY_BASE_OWNER, null) != email) return null
        return runCatching { SyncCodec.decode(baseFile.readBytes()) }.getOrNull()
    }

    private fun writeBase(email: String, snapshot: SyncSnapshot) {
        baseFile.parentFile?.mkdirs()
        val tmp = File(baseFile.path + ".tmp")
        tmp.writeBytes(SyncCodec.encode(snapshot, "base", System.currentTimeMillis()))
        if (!tmp.renameTo(baseFile)) {
            baseFile.delete()
            tmp.renameTo(baseFile)
        }
        prefs.edit().putString(KEY_BASE_OWNER, email).apply()
    }

    private fun forgetSyncState() {
        baseFile.delete()
        prefs.edit()
            .remove(KEY_BASE_OWNER).remove(KEY_FILE_ID).remove(KEY_VERSION)
            .remove(KEY_LAST_SYNC).remove(KEY_PLAYLISTS).remove(KEY_FAVORITES)
            .apply()
    }

    // ---- Automatic sync ---------------------------------------------------

    /**
     * While the feature is on, signed in and Auto-sync is on: syncs shortly
     * after launch, then about 20 s after the library or settings change.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            combine(settingsRepository.cloudSyncEnabled, state) { enabled, s ->
                enabled && s.available && s.account != null && s.autoSync && !s.needsSignIn
            }.distinctUntilChanged().collectLatest { active ->
                if (!active) return@collectLatest
                coroutineScope {
                    // Sync once after launch, then again whenever the phone comes
                    // back online with changes still waiting (made offline, or a
                    // sync that failed part-way).
                    launch {
                        var startupDone = false
                        onlineChanges().collect { online ->
                            _state.update { it.copy(waitingForNetwork = !online && prefs.getBoolean(KEY_PENDING, false)) }
                            if (!online) return@collect
                            if (!startupDone) {
                                delay(STARTUP_DELAY_MS)
                                startupDone = true
                                sync()
                            } else if (prefs.getBoolean(KEY_PENDING, false)) {
                                delay(RECONNECT_DELAY_MS)
                                sync()
                            }
                        }
                    }
                    val changes = merge(
                        database.invalidationTracker.createFlow(*WATCHED_TABLES, emitInitialState = false).map { },
                        settingsRepository.rawChanges.drop(1).map { },
                        lyricOffsetStore.offsets.drop(1).map { },
                        profileStore.profile.drop(1).map { },
                    )
                    changes
                        .filter { System.currentTimeMillis() > ignoreChangesUntil }
                        .onEach { setPending(true) }
                        .debounce(CHANGE_DEBOUNCE_MS)
                        .collect { if (isOnline()) sync() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CloudSync"
        private const val RESET_DRIVE_TIMEOUT_MS = 20_000L
        private const val RESET_REVOKE_TIMEOUT_MS = 3_000L
        private const val PREFS = "whiplash_cloud_sync"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_PHOTO = "photo"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_NEEDS_SIGN_IN = "needs_sign_in"
        private const val KEY_FILE_ID = "file_id"
        private const val KEY_VERSION = "file_version"
        private const val KEY_BASE_OWNER = "base_owner"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_PLAYLISTS = "count_playlists"
        private const val KEY_FAVORITES = "count_favorites"
        private const val KEY_PENDING = "pending_changes"
        private const val KEY_EXCLUDED = "excluded_categories"
        private const val KEY_SELECTIVE = "selective_sync"
        private const val RECONNECT_DELAY_MS = 2_000L

        private const val MAX_ATTEMPTS = 3
        private const val STARTUP_DELAY_MS = 4_000L
        private const val CHANGE_DEBOUNCE_MS = 20_000L
        private const val IGNORE_OWN_CHANGES_MS = 8_000L

        // Replay tallies change every few seconds while music plays; they ride
        // along with the history change each new song makes.
        private val WATCHED_TABLES = arrayOf("favorites", "pinned_speed_dial", "playlists", "playlist_tracks", "history")
    }
}
