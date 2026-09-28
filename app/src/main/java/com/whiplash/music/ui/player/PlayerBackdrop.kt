package com.whiplash.music.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.whiplash.music.domain.model.ArtworkPalette
import com.whiplash.music.domain.model.extractArtworkPalette
import com.whiplash.music.domain.model.relativeLuminance
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.appBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Colours the player resolves from the current cover, already animated so
 * a track change eases between palettes instead of cutting.
 */
data class PlayerColors(
    /** Play button fill and active-toggle tint. */
    val accent: Color,
    /** Icon/text colour on top of [accent]. */
    val onAccent: Color,
    /** Top-left, top-right, bottom-left, bottom-right backdrop colours. */
    val mesh: List<Color>,
    /** False when the colours are just the theme (artwork colours off or not loaded yet). */
    val fromArtwork: Boolean = true,
)

/**
 * Small in-process cache so going back to a recent track, or re-opening
 * the player, reuses the palette instead of decoding the cover again.
 */
private val paletteCache = object : LinkedHashMap<String, ArtworkPalette>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkPalette>?) = size > 40
}

/**
 * Loads a tiny 48px, software-backed copy of [artworkUri] purely to read
 * its colours. This is a separate Coil request from the one that displays
 * the artwork and bypasses the memory cache, so the artwork on screen keeps
 * exactly the size and quality it had before; this request normally hits
 * Coil's disk cache for the same URL and costs no extra network.
 * Returns null while loading and for tracks without artwork.
 */
@Composable
fun rememberArtworkPalette(artworkUri: String?, enabled: Boolean): State<ArtworkPalette?> {
    val context = LocalContext.current
    if (!enabled || artworkUri == null) return remember { mutableStateOf(null) }
    return produceState(initialValue = synchronized(paletteCache) { paletteCache[artworkUri] }, artworkUri) {
        // produceState keeps the previous value when the key changes, so it
        // still holds the *last* song's palette here. Checking it (as this
        // used to) skipped the new track entirely on auto-advance. Look up
        // the new URL instead; until its palette is ready the old colours
        // stay up and then animate across, rather than flashing to theme.
        val cached = synchronized(paletteCache) { paletteCache[artworkUri] }
        if (cached != null) {
            value = cached
            return@produceState
        }
        val request = ImageRequest.Builder(context)
            .data(artworkUri)
            .size(PALETTE_SAMPLE_PX)
            .precision(Precision.INEXACT)
            // getPixels() cannot read a hardware bitmap.
            .allowHardware(false)
            // Keep this tiny decode out of the memory cache entirely. Coil's
            // memory key for a plain request is just the URL, so writing it
            // would let the 48px bitmap replace (or be served in place of)
            // the full-size artwork the player and lists display, visibly
            // softening the cover. The disk cache holds the original
            // downloaded bytes, not decoded pixels, so reading it is safe.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@produceState
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@produceState
        val palette = withContext(Dispatchers.Default) { paletteFrom(bitmap) } ?: return@produceState
        synchronized(paletteCache) { paletteCache[artworkUri] = palette }
        value = palette
    }
}

private fun paletteFrom(bitmap: Bitmap): ArtworkPalette? {
    val source = if (bitmap.width > PALETTE_SAMPLE_PX * 2 || bitmap.height > PALETTE_SAMPLE_PX * 2) {
        Bitmap.createScaledBitmap(bitmap, PALETTE_SAMPLE_PX, PALETTE_SAMPLE_PX, true)
    } else {
        bitmap
    }
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    return extractArtworkPalette(pixels, source.width, source.height)
}

/**
 * Turns a (possibly null) palette into animated [PlayerColors]. With no
 * palette, or with the feature off, everything falls back to the theme so
 * the player looks exactly as it did before this feature existed.
 */
@Composable
fun animatedPlayerColors(palette: ArtworkPalette?): PlayerColors {
    val themeBackground = WhiplashColors.background
    val targetAccent = palette?.accent?.let { Color(it) } ?: WhiplashColors.accent
    val targetOnAccent = palette?.accent?.let {
        if (relativeLuminance(it) > 0.35f) Color(0xFF111111) else Color.White
    } ?: WhiplashColors.onAccent
    val targetMesh = palette?.mesh?.map { Color(it) } ?: List(4) { themeBackground }

    val spec = tween<Color>(COLOR_EASE_MS)
    val accent by animateColorAsState(targetAccent, spec, label = "playerAccent")
    val onAccent by animateColorAsState(targetOnAccent, spec, label = "playerOnAccent")
    val m0 by animateColorAsState(targetMesh[0], spec, label = "mesh0")
    val m1 by animateColorAsState(targetMesh[1], spec, label = "mesh1")
    val m2 by animateColorAsState(targetMesh[2], spec, label = "mesh2")
    val m3 by animateColorAsState(targetMesh[3], spec, label = "mesh3")
    return PlayerColors(accent, onAccent, listOf(m0, m1, m2, m3), fromArtwork = palette != null)
}

/**
 * Soft mesh-gradient backdrop: four large radial blobs, one per cover
 * quadrant, over the theme background, with a darkening scrim toward the
 * bottom where the controls sit. While music plays the blobs drift slowly
 * on small circular paths (one full loop per ~24s) so the screen feels
 * alive without distracting; paused, or with reduced motion, they rest.
 *
 * The drift value is only read inside the Canvas draw lambda, so the
 * animation redraws this one layer each frame without recomposing the
 * player.
 */
@Composable
fun PlayerMeshBackdrop(
    colors: PlayerColors,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = WhiplashColors.background
    val driftState: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "meshDrift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(DRIFT_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "meshDriftPhase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Without artwork colours the player is simply the theme's own page
    // (Nord blue-grey, Light white, the Liquid Glass background...), with no
    // darkening scrim, rather than a near-black slab.
    if (!colors.fromArtwork) {
        androidx.compose.foundation.layout.Box(modifier.fillMaxSize().then(Modifier.appBackground()))
        return
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(base)
        val phase = driftState.value * 2f * PI.toFloat()
        val radius = max(size.width, size.height) * 0.62f
        // Anchors roughly match where each quadrant sits on the cover,
        // stretched to the screen; each blob orbits its anchor out of phase.
        val anchors = listOf(
            Offset(0.15f, 0.12f), Offset(0.85f, 0.18f),
            Offset(0.2f, 0.62f), Offset(0.82f, 0.7f),
        )
        anchors.forEachIndexed { i, anchor ->
            val p = phase + i * (PI.toFloat() / 2f)
            val center = Offset(
                x = (anchor.x + cos(p) * 0.06f) * size.width,
                y = (anchor.y + sin(p) * 0.04f) * size.height,
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(colors.mesh[i].copy(alpha = 0.9f), colors.mesh[i].copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
            )
        }
        // Legibility scrim: controls and time labels sit on the lower half.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.10f),
                0.55f to Color.Black.copy(alpha = 0.25f),
                1f to Color.Black.copy(alpha = 0.55f),
            ),
        )
    }
}

private const val PALETTE_SAMPLE_PX = 48
private const val COLOR_EASE_MS = 700
private const val DRIFT_PERIOD_MS = 24_000
