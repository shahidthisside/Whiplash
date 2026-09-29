package com.whiplash.music.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.R
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.data.sync.CloudSyncManager
import com.whiplash.music.ui.common.ToastController
import com.whiplash.music.ui.common.artworkAtSize
import com.whiplash.music.ui.common.isReducedMotionEnabled
import com.whiplash.music.ui.theme.GlassSearchField
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import kotlinx.coroutines.launch

/** Onboarding screens, in order. */
enum class OnboardingStep { WELCOME, ACCOUNT, LANGUAGES, GENRES, ARTISTS, DONE }

/** The steps with a progress segment (the ones you can skip). */
private val PROGRESS_STEPS = listOf(OnboardingStep.ACCOUNT, OnboardingStep.LANGUAGES, OnboardingStep.GENRES, OnboardingStep.ARTISTS)

private val stringListSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

/**
 * First-run onboarding: Welcome → Sign in → Languages → Genres → Artists →
 * All set. Every step after Welcome can be skipped; whatever is picked
 * personalises Home (see HomeViewModel.personalizedQuickPicksQueries).
 *
 * [startAt] = LANGUAGES reopens just the taste steps from Settings, with the
 * saved picks preselected; Back from there calls [onClose] without saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
    startAt: OnboardingStep = OnboardingStep.WELCOME,
    onClose: () -> Unit = onFinished,
    // Called when the last step starts: the app composes Home underneath so it
    // loads the personalised feed while the "Personalising" animation plays.
    onPrepareHome: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory(app.youtubeSearchRepository, app.settingsRepository))
    val cloud = app.cloudSyncManager
    val cloudState by cloud.state.collectAsState()
    val cloudEnabled by app.settingsRepository.cloudSyncEnabled.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduceMotion = isReducedMotionEnabled()

    var step by rememberSaveable { mutableStateOf(startAt) }
    var forward by rememberSaveable { mutableStateOf(true) }
    var languages by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    var genres by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    var artists by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    var loadedSaved by rememberSaveable { mutableStateOf(startAt == OnboardingStep.WELCOME) }
    if (!loadedSaved) {
        LaunchedEffect(Unit) {
            val (l, g, a) = vm.savedTaste()
            languages = l; genres = g; artists = a
            loadedSaved = true
        }
    }

    // Sign-in is only offered when Google sign-in works on this phone and sync is on.
    val accountStep = cloudState.available && cloudEnabled != false
    fun order(): List<OnboardingStep> = OnboardingStep.entries.filter {
        (it != OnboardingStep.ACCOUNT || accountStep) && it.ordinal >= startAt.ordinal
    }
    fun go(to: OnboardingStep) {
        forward = to.ordinal > step.ordinal
        step = to
    }
    fun next() {
        val o = order()
        o.getOrNull(o.indexOf(step) + 1)?.let { go(it) }
    }
    fun back() {
        val o = order()
        val i = o.indexOf(step)
        if (i <= 0) onClose() else go(o[i - 1])
    }
    BackHandler(enabled = step != OnboardingStep.WELCOME && step != OnboardingStep.DONE || startAt != OnboardingStep.WELCOME) { back() }

    // Saved the moment the last step is left, so closing the app on "All set" keeps the picks.
    LaunchedEffect(step) {
        if (step == OnboardingStep.DONE) {
            OnboardingController.homeReady.value = false
            vm.finish(languages, genres, artists)
            onPrepareHome()
        }
    }

    // ---- Google sign-in (same flow as Settings › Account & sync) ----------
    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch { signInToast(cloud.finishSignIn(result.data)) }
        } else {
            cloud.cancelSignIn()
        }
    }
    val startSignIn: () -> Unit = {
        scope.launch {
            when (val s = cloud.beginSignIn()) {
                is CloudSyncManager.SignInStep.NeedsUi -> runCatching {
                    signInLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(s.intent).build())
                }.onFailure {
                    cloud.cancelSignIn()
                    ToastController.show("Couldn't sign in")
                }
                else -> signInToast(s)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(WhiplashColors.background),
    ) {
        // Steps draw edge to edge (the welcome collage runs under the status
        // bar); the others pad themselves below the floating top bar.
        Box(Modifier.fillMaxSize()) {
            val showChrome = step in PROGRESS_STEPS
            val topBar: @Composable () -> Unit = { TopBar(
                visible = showChrome,
                progressIndex = PROGRESS_STEPS.filter { it in order() }.indexOf(step),
                progressCount = PROGRESS_STEPS.count { it in order() },
                onBack = ::back,
                onSkip = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (step) {
                        OnboardingStep.LANGUAGES -> languages = emptyList()
                        OnboardingStep.GENRES -> genres = emptyList()
                        OnboardingStep.ARTISTS -> artists = emptyList()
                        else -> Unit
                    }
                    next()
                },
            ) }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    } else {
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(STEP_MS, easing = FastOutSlowInEasing)) { w -> dir * w / 4 } + fadeIn(tween(STEP_MS))) togetherWith
                            (slideOutHorizontally(tween(STEP_MS, easing = FastOutSlowInEasing)) { w -> -dir * w / 4 } + fadeOut(tween(STEP_MS / 2)))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "onboardingStep",
            ) { s ->
              if (s == OnboardingStep.WELCOME) {
                  val covers by vm.welcomeCovers.collectAsState()
                  WelcomeStep(covers, animate = !reduceMotion, onStart = ::next)
              } else Column(
                  Modifier
                      .fillMaxSize()
                      .windowInsetsPadding(WindowInsets.systemBars)
                      .imePadding()
                      .padding(top = TOP_BAR_HEIGHT),
              ) {
                when (s) {
                    OnboardingStep.WELCOME -> Unit
                    OnboardingStep.ACCOUNT -> {
                        val covers by vm.welcomeCovers.collectAsState()
                        AccountStep(cloudState, covers, animate = !reduceMotion, onSignIn = startSignIn, onContinue = ::next)
                    }
                    OnboardingStep.LANGUAGES -> PickStep(
                        title = "Which languages do you listen to?",
                        subtitle = "Pick as many as you like.",
                        count = languages.size,
                        onContinue = ::next,
                    ) {
                        LanguageChips(languages) { name -> languages = languages.toggle(name) }
                    }
                    OnboardingStep.GENRES -> PickStep(
                        title = "What's your vibe?",
                        subtitle = "Choose the genres you love.",
                        count = genres.size,
                        onContinue = ::next,
                        scrollable = false,
                    ) {
                        val art by vm.genreArt.collectAsState()
                        LaunchedEffect(Unit) { vm.loadGenreArt() } // retries any that failed
                        GenreGrid(genres, art) { name -> genres = genres.toggle(name) }
                    }
                    OnboardingStep.ARTISTS -> PickStep(
                        title = "Pick artists you love",
                        subtitle = "We'll fill your Home with music like theirs.",
                        count = artists.size,
                        onContinue = ::next,
                        scrollable = false,
                    ) {
                        ArtistPicker(vm, languages, genres, artists) { name -> artists = artists.toggle(name) }
                    }
                    OnboardingStep.DONE -> {
                        val photos by vm.photos.collectAsState()
                        val homeReady by OnboardingController.homeReady.collectAsState()
                        // From Settings (taste only) Home is already running, so no wait.
                        PersonalisingStep(
                            languages, genres, artists, photos,
                            homeReady = homeReady || startAt != OnboardingStep.WELCOME,
                            animate = !reduceMotion,
                            onStart = onFinished,
                        )
                    }
                }
              }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { topBar() }
        }
    }
}

private fun List<String>.toggle(value: String) = if (value in this) this - value else this + value

private fun signInToast(step: CloudSyncManager.SignInStep) {
    when (step) {
        is CloudSyncManager.SignInStep.Done -> ToastController.show("Signed in as ${step.account.email}")
        is CloudSyncManager.SignInStep.Failed -> ToastController.show(step.message)
        is CloudSyncManager.SignInStep.NeedsUi -> ToastController.show("Couldn't sign in")
    }
}

private const val STEP_MS = 420
private val TOP_BAR_HEIGHT = 64.dp

// ---- Chrome -------------------------------------------------------------------

@Composable
private fun TopBar(visible: Boolean, progressIndex: Int, progressCount: Int, onBack: () -> Unit, onSkip: () -> Unit) {
    // Fixed height so the content below doesn't jump as the bar fades in or out.
    Box(Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT)) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(STEP_MS)), exit = fadeOut(tween(STEP_MS / 2))) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlainIconButton(contentDescription = "Back", onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
                }
                Row(
                    Modifier.weight(1f).padding(horizontal = GlassTokens.spaceSm),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(progressCount) { i ->
                        val fill by animateFloatAsState(
                            if (i <= progressIndex) 1f else 0f,
                            tween(STEP_MS, easing = FastOutSlowInEasing),
                            label = "progress$i",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(WhiplashColors.textPrimary.copy(alpha = 0.14f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fill)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(WhiplashColors.accent),
                            )
                        }
                    }
                }
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = WhiplashColors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(WhiplashRadius.pill))
                        .clickable(role = Role.Button, onClick = onSkip)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "btnScale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, tween(200), label = "btnAlpha")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.accent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = WhiplashColors.onAccent)
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = WhiplashColors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = WhiplashColors.textSecondary)
    }
}

/** Fades and lifts [content] in after [delayMs], for staggered entrances. */
@Composable
private fun Rise(delayMs: Int, animate: Boolean = true, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            kotlinx.coroutines.delay(delayMs.toLong())
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 40f }) { content() }
}

// ---- Steps ----------------------------------------------------------------------

@Composable
private fun WelcomeStep(covers: List<String>, animate: Boolean, onStart: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CoverCollage(covers, animate, Modifier.fillMaxWidth().fillMaxHeight(0.72f))
        // Fade the collage into the page so the text sits on a calm background.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        // Light scrim under the status bar so its icons stay readable.
                        0f to WhiplashColors.background.copy(alpha = 0.55f),
                        0.08f to WhiplashColors.background.copy(alpha = 0.10f),
                        0.30f to WhiplashColors.background.copy(alpha = 0.10f),
                        0.56f to WhiplashColors.background.copy(alpha = 0.92f),
                        0.68f to WhiplashColors.background,
                    ),
                ),
        )
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.weight(1f))
            Rise(150, animate) { GlowLogo(animate) }
            Spacer(Modifier.height(22.dp))
            Rise(260, animate) {
                Text(
                    "Your music,\nyour way.",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, lineHeight = MaterialTheme.typography.displaySmall.lineHeight * 1.05f),
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(10.dp))
            Rise(360, animate) {
                Text(
                    "Millions of songs, offline downloads and synced lyrics, all in one beautiful player.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WhiplashColors.textSecondary,
                )
            }
            Spacer(Modifier.height(22.dp))
            Rise(460, animate) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeaturePill(Icons.Filled.GraphicEq, "Stream")
                    FeaturePill(Icons.Filled.DownloadForOffline, "Offline")
                    FeaturePill(Icons.Filled.Lyrics, "Lyrics")
                }
            }
            Spacer(Modifier.height(32.dp))
            Rise(580, animate) { PrimaryButton("Get started", onClick = onStart) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** App logo on a softly pulsing accent glow. */
@Composable
private fun GlowLogo(animate: Boolean) {
    val pulse: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "logoGlow").animateFloat(
            0.35f, 0.7f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "logoGlowA",
        )
    } else remember { mutableStateOf(0.5f) }
    val accent = WhiplashColors.accent
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = pulse.value * 0.55f), Color.Transparent)), radius = size.minDimension / 2)
        }
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF0A0A0C))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Whiplash", modifier = Modifier.size(72.dp))
        }
    }
}

@Composable
private fun FeaturePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.textPrimary.copy(alpha = 0.08f))
            .border(1.dp, WhiplashColors.glassBorder, RoundedCornerShape(WhiplashRadius.pill))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = WhiplashColors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = WhiplashColors.textPrimary)
    }
}

/**
 * Three tilted columns of album covers drifting slowly in opposite
 * directions (Apple Music / Spotify welcome style). Offline, gradient
 * squares stand in so the page never looks empty. Moves in the draw phase.
 */
@Composable
private fun CoverCollage(covers: List<String>, animate: Boolean, modifier: Modifier) {
    val drift: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "collage").animateFloat(
            0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "collageDrift",
        )
    } else remember { mutableStateOf(0.5f) }
    val tiles = if (covers.size >= 6) covers else emptyList()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clip(RoundedCornerShape(0.dp))) {
        val tile = maxWidth / 2.6f
        val shiftPx = with(androidx.compose.ui.platform.LocalDensity.current) { (tile * 0.9f).toPx() }
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = -12f; scaleX = 1.35f; scaleY = 1.35f },
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            repeat(3) { col ->
                Column(
                    Modifier.graphicsLayer {
                        val dir = if (col % 2 == 0) 1f else -1f
                        translationY = dir * (drift.value - 0.5f) * shiftPx - col * shiftPx * 0.35f
                    },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(5) { row ->
                        val i = col * 5 + row
                        Box(
                            Modifier
                                .size(tile)
                                .clip(RoundedCornerShape(18.dp))
                                .background(WhiplashColors.textPrimary.copy(alpha = 0.05f + 0.03f * (i % 3))),
                        ) {
                            if (tiles.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(artworkAtSize(tiles[i % tiles.size], 300)).crossfade(600).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountStep(state: CloudSyncManager.State, covers: List<String>, animate: Boolean, onSignIn: () -> Unit, onContinue: () -> Unit) {
    val account = state.account
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        StepTitle(
            if (account == null) "Take your library\neverywhere" else "You're signed in",
            if (account == null) "Optional. Sign in to back up and sync across your phones." else "Your library now stays in sync on every phone you sign in on.",
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = account,
                transitionSpec = { (scaleIn(tween(500), initialScale = 0.85f) + fadeIn(tween(500))) togetherWith (scaleOut(tween(300), targetScale = 0.92f) + fadeOut(tween(300))) },
                label = "accountArt",
            ) { acc ->
                if (acc == null) CoverFan(covers, animate) else SignedInCard(acc)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 24.dp)) {
            if (account == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BenefitChip(Icons.Filled.Lock, "Private", Modifier.weight(1f))
                    BenefitChip(Icons.Filled.Devices, "All phones", Modifier.weight(1f))
                    BenefitChip(Icons.Filled.Restore, "Restore", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                GoogleButton(signingIn = state.signingIn, onClick = onSignIn)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Whiplash can only see its own hidden folder in your Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WhiplashColors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton("Continue", onClick = onContinue)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Three album covers fanned like a stack of records, the middle one lifted,
 * with a small glass "synced" badge; the fan breathes open and closed very
 * slowly. Same covers as the welcome collage, so the flow feels continuous.
 */
@Composable
private fun CoverFan(covers: List<String>, animate: Boolean) {
    val open: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "fan").animateFloat(
            0.85f, 1f, infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fanOpen",
        )
    } else remember { mutableStateOf(1f) }
    val size = 150.dp
    Box(Modifier.size(width = 300.dp, height = 240.dp), contentAlignment = Alignment.Center) {
        listOf(-1, 1, 0).forEach { pos ->
            val url = covers.getOrNull(pos + 1)
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        rotationZ = pos * 12f * open.value
                        translationX = pos * 70.dp.toPx() * open.value
                        translationY = if (pos == 0) -10.dp.toPx() else 8.dp.toPx()
                        val s = if (pos == 0) 1f else 0.9f
                        scaleX = s; scaleY = s
                        shadowElevation = if (pos == 0) 24f else 10f
                        shape = RoundedCornerShape(22.dp)
                        clip = true
                    }
                    .background(WhiplashColors.surfaceElevated)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artworkAtSize(url, 360)).crossfade(400).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = WhiplashColors.textTertiary, modifier = Modifier.size(40.dp))
                }
            }
        }
        // Synced badge, bottom-right of the front cover.
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationX = 58.dp.toPx(); translationY = 58.dp.toPx() }
                .size(52.dp)
                .clip(CircleShape)
                .background(WhiplashColors.surfaceSheet)
                .border(1.dp, WhiplashColors.glassBorderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = WhiplashColors.textPrimary, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun SignedInCard(acc: CloudSyncManager.Account) {
    Column(
        Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(WhiplashColors.textPrimary.copy(alpha = 0.06f))
            .border(1.dp, WhiplashColors.glassBorder, RoundedCornerShape(28.dp))
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            com.whiplash.music.ui.settings.AccountAvatar(acc, 104.dp)
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(WhiplashColors.accent).border(3.dp, WhiplashColors.surfaceSheet, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = WhiplashColors.onAccent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(acc.name ?: acc.email.substringBefore('@'), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = WhiplashColors.textPrimary)
        Text(acc.email, style = MaterialTheme.typography.bodyMedium, color = WhiplashColors.textSecondary)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clip(RoundedCornerShape(WhiplashRadius.pill)).background(WhiplashColors.accent.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = WhiplashColors.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sync is on", style = MaterialTheme.typography.labelLarge, color = WhiplashColors.accent)
        }
    }
}

@Composable
private fun BenefitChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(WhiplashColors.textPrimary.copy(alpha = 0.06f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = WhiplashColors.textPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
    }
}

/** White pill "Continue with Google" with the four-colour G. */
@Composable
private fun GoogleButton(signingIn: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "gScale")
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(Color.White)
            .clickable(interactionSource = interaction, indication = null, enabled = !signingIn, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (signingIn) {
            CircularProgressIndicator(color = Color(0xFF4285F4), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            GoogleG(Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (signingIn) "Signing in…" else "Continue with Google",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF1F1F1F),
        )
    }
}

/** Google's "G" drawn as four arcs plus the bar (no bitmap asset needed). */
@Composable
private fun GoogleG(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val tl = Offset(inset, inset)
        val s = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        drawArc(Color(0xFFEA4335), 200f, 100f, false, tl, arcSize, style = s)
        drawArc(Color(0xFFFBBC05), 140f, 60f, false, tl, arcSize, style = s)
        drawArc(Color(0xFF34A853), 45f, 95f, false, tl, arcSize, style = s)
        drawArc(Color(0xFF4285F4), -15f, 60f, false, tl, arcSize, style = s)
        drawRect(Color(0xFF4285F4), topLeft = Offset(size.width / 2, size.height / 2 - stroke / 2), size = androidx.compose.ui.geometry.Size(size.width / 2 - inset / 2, stroke))
    }
}

/** Shared layout for the three pick steps: title, content, then the count and Continue. */
@Composable
private fun PickStep(
    title: String,
    subtitle: String,
    count: Int,
    onContinue: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        StepTitle(title, subtitle)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) { content() }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (count == 0) "Pick at least one, or skip" else "Continue · $count selected",
                onClick = onContinue,
                enabled = count > 0,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Language cards in a fixed two-column grid: every card is the same size and
 * the tick sits in a reserved corner, so selecting never changes any card's
 * width (the old chips grew by the tick's width and made the rows reflow).
 */
@Composable
private fun LanguageChips(selected: List<String>, onToggle: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val rows = OnboardingCatalog.languages.chunked(2)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, lang ->
                    Box(Modifier.weight(1f)) {
                        Rise(35 * (r * 2 + c)) {
                            LanguageCard(lang, on = lang.name in selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggle(lang.name)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LanguageCard(lang: TasteLanguage, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val accent = WhiplashColors.accent
    val bg by animateColorAsState(if (on) accent.copy(alpha = 0.18f) else WhiplashColors.textPrimary.copy(alpha = 0.06f), tween(260), label = "langBg")
    val border by animateColorAsState(if (on) accent else WhiplashColors.glassBorder, tween(260), label = "langBorder")
    val scale by animateFloatAsState(if (on) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "langScale")
    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(if (on) 2.dp else 1.dp, border, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = on }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Big native script as a soft watermark in the corner.
        Text(
            lang.native,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = (if (on) accent else WhiplashColors.textPrimary).copy(alpha = if (on) 0.30f else 0.10f),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
        Column(Modifier.align(Alignment.CenterStart)) {
            Text(lang.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = WhiplashColors.textPrimary)
            if (lang.native != lang.name) {
                Text(lang.native, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary, maxLines = 1)
            }
        }
        // Reserved corner: an empty ring that fills when picked, so nothing moves.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (on) Color.Transparent else WhiplashColors.textTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(on, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = WhiplashColors.onAccent, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun GenreGrid(selected: List<String>, art: Map<String, String>, onToggle: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(OnboardingCatalog.genres, key = { it.name }) { g ->
            val on = g.name in selected
            val index = OnboardingCatalog.genres.indexOf(g)
            Rise(30 * index.coerceAtMost(10)) {
                GenreTile(g.name, art[g.name], on) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(g.name)
                }
            }
        }
    }
}

/**
 * A genre as a real cover: the top playlist's artwork full bleed, a dark
 * fade at the bottom for the name, and a clean accent ring + tick when
 * picked. Before the artwork loads it's a quiet shimmer, not a colour block.
 */
@Composable
private fun GenreTile(name: String, artwork: String?, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(WhiplashRadius.medium)
    val scale by animateFloatAsState(if (on) 0.95f else 1f, spring(dampingRatio = 0.6f), label = "genreScale")
    val ring by animateColorAsState(if (on) WhiplashColors.accent else Color.Transparent, tween(220), label = "genreRing")
    val dim by animateFloatAsState(if (on) 0.35f else 0f, tween(220), label = "genreDim")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(2.5.dp, ring, shape)
            .padding(if (on) 4.dp else 0.dp)
            .clip(if (on) RoundedCornerShape(WhiplashRadius.medium - 3.dp) else shape)
            .background(WhiplashColors.surfaceElevated)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = on; contentDescription = name },
    ) {
        if (artwork != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(artworkAtSize(artwork, 400)).crossfade(400).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            com.whiplash.music.ui.theme.ShimmerBox(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)))
                .background(Color.Black.copy(alpha = dim)),
        )
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
        androidx.compose.animation.AnimatedVisibility(
            on,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(WhiplashColors.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = WhiplashColors.onAccent, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ArtistPicker(
    vm: OnboardingViewModel,
    languages: List<String>,
    genres: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
) {
    val suggestions = remember(languages, genres) { OnboardingCatalog.suggestedArtists(languages, genres) }
    val photos by vm.photos.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(suggestions) { vm.loadPhotos(suggestions) }
    // Picks from search stay visible (first) after the search is cleared.
    val names = if (query.isBlank()) {
        (selected.filter { it !in suggestions } + suggestions).distinct()
    } else {
        results.map { it.name }.distinct()
    }
    Column(Modifier.fillMaxSize()) {
        GlassSearchField(
            query = query,
            onQueryChange = { query = it; vm.searchArtists(it) },
            placeholder = "Search artists",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (query.isNotBlank() && names.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        if (searching) {
                            CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else {
                            Text("No artists found", color = WhiplashColors.textSecondary)
                        }
                    }
                }
            }
            items(names, key = { it }) { name ->
                ArtistBubble(name, photos[name], loaded = name in photos, on = name in selected) { onToggle(name) }
            }
        }
    }
}

@Composable
private fun ArtistBubble(name: String, photo: String?, loaded: Boolean, on: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (on) 0.92f else 1f, spring(dampingRatio = 0.55f), label = "artistScale")
    val ring by animateColorAsState(if (on) WhiplashColors.accent else Color.Transparent, tween(220), label = "artistRing")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.medium))
            .clickable(role = Role.Checkbox) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) { this.selected = on }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .size(92.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .border(3.dp, ring, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(com.whiplash.music.ui.theme.tintForName(name)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                if (photo != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artworkAtSize(photo, 240)).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (!loaded) {
                    com.whiplash.music.ui.theme.ShimmerBox(Modifier.fillMaxSize(), CircleShape)
                }
            }
            androidx.compose.animation.AnimatedVisibility(on, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WhiplashColors.accent)
                        .border(2.dp, WhiplashColors.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = WhiplashColors.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (on) WhiplashColors.textPrimary else WhiplashColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * The last step: "Personalising your Home" with a gentle animation for a
 * few seconds while Home loads underneath, then "You're all set" and
 * Continue. The button appears once Home is ready (at least
 * [MIN_PERSONALISE_MS] so it reads as a real step), or after
 * [MAX_PERSONALISE_MS] regardless, e.g. offline; Home then fills in by
 * itself when the connection returns.
 */
@Composable
private fun PersonalisingStep(
    languages: List<String>,
    genres: List<String>,
    artists: List<String>,
    photos: Map<String, String?>,
    homeReady: Boolean,
    animate: Boolean,
    onStart: () -> Unit,
) {
    var minElapsed by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(if (animate) MIN_PERSONALISE_MS else 600L)
        minElapsed = true
        kotlinx.coroutines.delay(MAX_PERSONALISE_MS - MIN_PERSONALISE_MS)
        timedOut = true
    }
    val done = minElapsed && (homeReady || timedOut)
    val picks = artists + genres + languages
    // Messages cycle while working so it feels like real progress.
    val messages = buildList {
        add("Personalising your Home")
        if (artists.isNotEmpty()) add("Finding music like ${artists.first()}")
        if (genres.isNotEmpty()) add("Mixing in ${genres.take(2).joinToString(" and ")}")
        if (languages.isNotEmpty()) add("Picking the best ${languages.first()} songs")
        add("Putting it all together")
    }
    var msgIndex by remember { mutableStateOf(0) }
    LaunchedEffect(done) {
        while (!done) {
            kotlinx.coroutines.delay(1100)
            msgIndex = (msgIndex + 1) % messages.size
        }
    }
    val summary = when {
        !homeReady && timedOut -> "Your Home will fill in as soon as you're back online."
        picks.isEmpty() -> "Your Home will learn from what you play. You can set your taste any time in Settings."
        picks.size <= 2 -> "Home is tuned to ${picks.joinToString(" and ")}."
        else -> "Home is tuned to ${picks.take(2).joinToString(", ")} and ${picks.size - 2} more."
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        PersonaliseOrb(artists.take(6), photos, done = done, animate = animate)
        Spacer(Modifier.height(36.dp))
        AnimatedContent(
            targetState = if (done) "You're all set" else messages[msgIndex % messages.size],
            transitionSpec = {
                (fadeIn(tween(450)) + androidx.compose.animation.slideInVertically(tween(450)) { it / 3 }) togetherWith
                    (fadeOut(tween(300)) + androidx.compose.animation.slideOutVertically(tween(300)) { -it / 3 })
            },
            label = "personaliseTitle",
        ) { title ->
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = WhiplashColors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        }
        Spacer(Modifier.height(12.dp))
        // Fixed height so the title doesn't move when the summary appears.
        Box(Modifier.height(56.dp), contentAlignment = Alignment.TopCenter) {
            androidx.compose.animation.AnimatedVisibility(done, enter = fadeIn(tween(500)), exit = fadeOut()) {
                Text(summary, style = MaterialTheme.typography.bodyLarge, color = WhiplashColors.textSecondary, textAlign = TextAlign.Center)
            }
            androidx.compose.animation.AnimatedVisibility(!done, enter = fadeIn(), exit = fadeOut(tween(250))) {
                Text("This only takes a moment", style = MaterialTheme.typography.bodyMedium, color = WhiplashColors.textTertiary)
            }
        }
        Spacer(Modifier.weight(1.2f))
        // Space is kept for the button so nothing shifts when it appears.
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                done,
                enter = fadeIn(tween(500)) + androidx.compose.animation.slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 2 },
            ) {
                PrimaryButton("Start listening", onClick = onStart)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private const val MIN_PERSONALISE_MS = 3_200L
private const val MAX_PERSONALISE_MS = 8_000L

/**
 * A slowly turning ring of the picked artists around a glowing orb with a
 * sweeping progress arc; on completion the orb pops into a tick.
 */
@Composable
private fun PersonaliseOrb(artists: List<String>, photos: Map<String, String?>, done: Boolean, animate: Boolean) {
    val inf = rememberInfiniteTransition(label = "orb")
    val spin: State<Float> = if (animate) inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "orbSpin")
    else remember { mutableStateOf(0f) }
    val sweep: State<Float> = if (animate) inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "orbSweep")
    else remember { mutableStateOf(0f) }
    val breathe: State<Float> = if (animate) inf.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orbBreathe")
    else remember { mutableStateOf(1f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(done) { if (done) tick.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    val accent = WhiplashColors.accent
    val arcColor = WhiplashColors.textPrimary
    val halo = WhiplashColors.textPrimary.copy(alpha = 0.06f)
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            // Soft neutral halo (no colour wash), breathing slowly.
            drawCircle(Brush.radialGradient(listOf(halo, Color.Transparent)), radius = r * breathe.value)
            drawCircle(Color.White.copy(alpha = 0.07f), radius = r * 0.78f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            if (!done) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, arcColor)),
                    startAngle = sweep.value,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width / 2 - r * 0.46f, size.height / 2 - r * 0.46f),
                    size = androidx.compose.ui.geometry.Size(r * 0.92f, r * 0.92f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(6f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        // Centre: breathing orb that turns into a tick.
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer { val s = if (done) 1f + 0.08f * tick.value else breathe.value * 0.96f; scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(if (done) accent else WhiplashColors.surfaceElevated)
                .border(1.dp, if (done) Color.Transparent else WhiplashColors.glassBorderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = WhiplashColors.onAccent,
                    modifier = Modifier.size(52.dp).graphicsLayer { scaleX = tick.value; scaleY = tick.value },
                )
            } else {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = WhiplashColors.textPrimary, modifier = Modifier.size(44.dp))
            }
        }
        // Picked artists orbiting (or music notes if none were picked).
        val count = artists.size.coerceAtLeast(4)
        repeat(count) { i ->
            val name = artists.getOrNull(i)
            Box(
                Modifier
                    .size(if (name != null) 50.dp else 36.dp)
                    .graphicsLayer {
                        val a = Math.toRadians((spin.value + i * 360f / count).toDouble())
                        val rad = 104.dp.toPx()
                        translationX = (rad * kotlin.math.cos(a)).toFloat()
                        translationY = (rad * kotlin.math.sin(a)).toFloat()
                    }
                    .clip(CircleShape)
                    .background(if (name != null) com.whiplash.music.ui.theme.tintForName(name) else WhiplashColors.surfaceElevated)
                    .border(2.dp, WhiplashColors.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (name != null) {
                    Text(name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    photos[name]?.let { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(artworkAtSize(url, 200)).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
