package com.whiplash.music.ui.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/*
 * Real Liquid Glass.
 *
 * The screen content is recorded once per frame into a GraphicsLayer (a
 * RenderNode) by [glassSource]. Each glass element ([liquidGlass]) draws a
 * copy of the part of that layer lying behind it, through a RenderEffect
 * chain, then its tint and rim light on top:
 *
 *   saturation boost (vibrancy) -> blur -> lens refraction (AGSL)
 *
 * The lens shader bends the backdrop near the element's rounded edges the
 * way a thick piece of glass does, so content visibly warps as it scrolls
 * under the tab bar. Because the copy references the source RenderNode,
 * it stays live with no extra recording work.
 *
 * Levels of support:
 *   Android 13+  refraction + blur + vibrancy
 *   Android 12   blur + vibrancy (no RuntimeShader)
 *   older        no RenderEffect: an opaque-enough tint over the content
 *
 * The rounded-rect SDF and refraction maths follow the open-source
 * Backdrop library by Kyant (github.com/Kyant0/AndroidLiquidGlass,
 * Apache License 2.0), re-implemented here because its current releases
 * need a newer Compose and Kotlin than this app builds with.
 */

/** Where the content that glass refracts is recorded. */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
}

/** The backdrop glass elements sample; null outside the Liquid Glass theme. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * The glass background alone (no page content), for glass *inside* pages:
 * cards, buttons and fields refract the backdrop behind them without ever
 * sampling the page they belong to.
 */
val LocalContentGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * Fills a control or card: real glass over the Liquid Glass background in
 * that theme, a plain [fallback] fill in every other theme. Use in place of
 * .clip(shape).background(color) on surfaces that sit on the page.
 */
@Composable
fun Modifier.glassFill(shape: CornerBasedShape, fallback: Color): Modifier {
    val source = LocalContentGlassBackdrop.current
    if (source == null || !WhiplashColors.isGlass) return this.clip(shape).background(fallback)
    return this.clip(shape).liquidGlass(
        shape = shape,
        fallback = fallback,
        tint = WhiplashColors.surfaceElevated,
        refractionHeight = 12.dp,
        refractionAmount = 16.dp,
        source = source,
    )
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * Records this node's content into [backdrop] and draws it normally.
 * Glass elements must not be inside this node (they'd sample themselves).
 */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.coordinates = it }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/** Whether this device can show the blur (API 31) and the refraction (API 33). */
val glassBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
val glassRefractionSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Tint alpha for a glass surface at user opacity [opacity] (0..1). Never
 * fully clear: a minimum tint keeps text on the glass readable over any
 * content. Without blur support the glass must hide sharp content, so the
 * floor is much higher there.
 */
fun glassTintAlpha(opacity: Float): Float {
    // Clear glass (Apple's "Clear" style) is almost untinted: the backdrop is
    // only slightly dimmed inside the effect chain (see [buildGlassEffect]),
    // which is what keeps labels readable, not a milky fill.
    // At 0 the glass is fully clear: no tint at all, only the lens and rim.
    val floor = if (glassBlurSupported) 0f else 0.78f
    return (floor + (0.85f - floor) * opacity.coerceIn(0f, 1f)).coerceAtMost(0.85f)
}

/** Blur behind glass: none when fully clear, heavier as it frosts. */
fun glassBlurDp(opacity: Float): Float = 14f * opacity.coerceIn(0f, 1f)

private class GlassState {
    var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
    var effectKey: Any? = null
    var effect: androidx.compose.ui.graphics.RenderEffect? = null
    var shader: Any? = null
    var material: Any? = null
}

/**
 * Draws real liquid glass behind this element's content, refracting and
 * blurring whatever [LocalGlassBackdrop] holds behind it. Outside the Liquid
 * Glass theme (no backdrop provided) it draws [fallback] instead, so it's
 * safe to use unconditionally.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    fallback: Color,
    tint: Color = WhiplashColors.surfaceGlass,
    /**
     * Minimum softening (0..1) for glass carrying text: blurs and dims what's
     * behind just enough that labels never clash with content under them,
     * while the tint stays at the user's setting (fully clear at 0).
     */
    legibility: Float = 0f,
    blurRadius: Dp = glassBlurDp(maxOf(WhiplashColors.glassOpacity, legibility)).dp,
    refractionHeight: Dp = 24.dp,
    refractionAmount: Dp = 30.dp,
    source: GlassBackdrop? = LocalGlassBackdrop.current,
    /** Lens strength 0..1 (the "Lens bending" setting); 0.5 is the designed look. */
    lens: Float = WhiplashColors.glassLens,
    /** Fixed tint strength instead of the opacity setting (e.g. accent-tinted glass). */
    tintAlpha: Float? = null,
): Modifier {
    val backdrop = source
    if (backdrop == null) {
        return this.drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutlineFill(outline, fallback)
            drawContent()
        }
    }
    val layer = rememberGraphicsLayer()
    val state = remember { GlassState() }
    return this
        .onGloballyPositioned { state.coordinates = it }
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = outline.toPath()
            val src = backdrop.coordinates
            val me = state.coordinates
            // Only sample a backdrop in this same window: inside a dialog or
            // sheet (another window) the page's layer must never be drawn.
            val sameWindow = src != null && me != null && src.isAttached && me.isAttached &&
                src.findRootCoordinates() === me.findRootCoordinates()
            if (!sameWindow) {
                drawPath(path, fallback)
                drawContent()
                return@drawWithContent
            }
            if (src != null && me != null) {
                val offset = runCatching { src.localPositionOf(me, Offset.Zero) }
                    .getOrElse { me.positionInWindow() - src.positionInWindow() }
                val blurPx = blurRadius.toPx()
                val pad = if (glassBlurSupported) ceil(blurPx * 2f).toInt() else 0
                layer.record(IntSize(size.width.toInt() + pad * 2, size.height.toInt() + pad * 2)) {
                    translate(pad - offset.x, pad - offset.y) { drawLayer(backdrop.layer) }
                }
                layer.topLeft = IntOffset(-pad, -pad)
                val dim = WhiplashColors.isLight
                val opacity = maxOf(WhiplashColors.glassOpacity, legibility)
                val key = listOf(size, blurPx, pad, shape.hashCode(), refractionHeight, refractionAmount, dim, opacity, lens)
                if (state.effectKey != key && glassBlurSupported) {
                    state.effectKey = key
                    state.effect = buildGlassEffect(
                        state = state,
                        size = size,
                        pad = pad.toFloat(),
                        blurPx = blurPx,
                        radii = cornerRadii(shape, size, this),
                        refractionHeight = refractionHeight.toPx() * (0.4f + 1.2f * lens),
                        refractionAmount = refractionAmount.toPx() * lens * 2f,
                        lightGlass = dim,
                        opacity = opacity,
                    )
                }
                layer.renderEffect = state.effect
                clipPath(path) { drawLayer(layer) }
            }
            // Tint: how "frosted" the glass is, from the opacity setting.
            val alpha = tintAlpha ?: glassTintAlpha(WhiplashColors.glassOpacity)
            drawPath(path, tint.copy(alpha = alpha))
            // 3D glass material: specular rim, bevelled thickness and gloss.
            drawGlassMaterial(state, path, shape, light = WhiplashColors.isLight)
            drawContent()
        }
}

private fun DrawScope.drawOutlineFill(outline: Outline, color: Color) {
    drawPath(outline.toPath(), color)
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

/** Top-left, top-right, bottom-right, bottom-left radii in px, clamped to half the short side. */
private fun cornerRadii(shape: CornerBasedShape, size: Size, density: androidx.compose.ui.unit.Density): FloatArray {
    val max = size.minDimension / 2f
    return floatArrayOf(
        shape.topStart.toPx(size, density).coerceAtMost(max),
        shape.topEnd.toPx(size, density).coerceAtMost(max),
        shape.bottomEnd.toPx(size, density).coerceAtMost(max),
        shape.bottomStart.toPx(size, density).coerceAtMost(max),
    )
}

private fun buildGlassEffect(
    state: GlassState,
    size: Size,
    pad: Float,
    blurPx: Float,
    radii: FloatArray,
    refractionHeight: Float,
    refractionAmount: Float,
    lightGlass: Boolean = false,
    opacity: Float = 0f,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    // Vibrancy: colours seen through glass look richer, not washed out
    // (saturation x1.5), then a gentle tone shift toward the glass's ink
    // side (dark glass dims ~18%, light glass lifts) so white labels stay
    // readable over a white cover without any milky tint.
    val vibrancy = RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(
            ColorMatrix().apply {
                // Clear glass keeps colours as they are (a touch richer);
                // frosting adds saturation and the tone shift.
                setSaturation(1.15f + 0.35f * opacity)
                // Light glass needs a stronger lift: dark ink over a dark
                // cover has to be pulled toward white to stay readable.
                val shift = (if (lightGlass) 0.6f else 0.25f) * opacity
                val k = 1f - shift
                val lift = if (lightGlass) 255f * shift else 0f
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            k, 0f, 0f, 0f, lift,
                            0f, k, 0f, 0f, lift,
                            0f, 0f, k, 0f, lift,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            },
        ),
    )
    val blurred = if (blurPx > 0.5f) {
        RenderEffect.createBlurEffect(blurPx, blurPx, vibrancy, Shader.TileMode.CLAMP)
    } else {
        vibrancy
    }
    val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.minDimension > 0f && refractionAmount > 0.5f) {
        lensEffect(state, size, pad, radii, refractionHeight.coerceAtMost(size.minDimension / 2f), refractionAmount, blurred)
    } else {
        blurred
    }
    return full.asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensEffect(
    state: GlassState,
    size: Size,
    pad: Float,
    radii: FloatArray,
    height: Float,
    amount: Float,
    input: RenderEffect,
): RenderEffect {
    val shader = (state.shader as? RuntimeShader) ?: RuntimeShader(LENS_SHADER).also { state.shader = it }
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("offset", -pad, -pad)
    shader.setFloatUniform("cornerRadii", radii)
    shader.setFloatUniform("refractionHeight", height)
    shader.setFloatUniform("refractionAmount", -amount)
    val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
    return RenderEffect.createChainEffect(lens, input)
}

/**
 * Lens refraction over a rounded rectangle. Inside the rim band of width
 * refractionHeight, each pixel samples the backdrop displaced along the
 * edge normal by a circular profile, so the edge magnifies and bends what's
 * behind it; the flat middle passes the (blurred) backdrop straight through.
 */
private const val LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;

float radiusAt(float2 c, float4 r) {
    if (c.x >= 0.0) { return c.y <= 0.0 ? r.y : r.z; }
    return c.y <= 0.0 ? r.x : r.w;
}

float sdRoundedRect(float2 c, float2 halfSize, float radius) {
    float2 q = abs(c) - (halfSize - float2(radius));
    return length(max(q, 0.0)) - radius + min(max(q.x, q.y), 0.0);
}

float2 gradSdRoundedRect(float2 c, float2 halfSize, float radius) {
    float2 q = abs(c) - (halfSize - float2(radius));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(c) * normalize(max(q, 0.0) + float2(0.0001));
    }
    float gx = step(q.y, q.x);
    return sign(c) * float2(gx, 1.0 - gx);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centered = (coord + offset) - halfSize;
    float radius = radiusAt(centered, cornerRadii);
    float sd = sdRoundedRect(centered, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float x = 1.0 - (-sd / refractionHeight);
    float d = (1.0 - sqrt(max(1.0 - x * x, 0.0))) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centered, halfSize, gradRadius) + float2(0.0001));
    return content.eval(coord + d * grad);
}
"""

/**
 * Liquid Glass wallpaper: the theme background with a few large, soft light
 * pools in the accent's family, so there's always something for glass to
 * bend even over empty space. Drawn behind every screen.
 */
@Composable
fun GlassWallpaper(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.appBackground())
}

/**
 * The page background for the current theme. In every theme but Liquid
 * Glass it's the plain background colour. In Liquid Glass it's the chosen
 * glass background: a calm solid tone, or ("Now playing") the current cover,
 * blurred into soft light and dimmed like Apple Music's backdrop.
 *
 * Drawn in window coordinates, so every page that paints it (the root and
 * each detail page sliding over it) lines up exactly, and glass sampling it
 * sees the same pixels that are on screen. Opaque.
 */
@Composable
fun Modifier.appBackground(): Modifier {
    val base = WhiplashColors.background
    if (!WhiplashColors.isGlass) return this.background(base)
    val choice = WhiplashColors.customColors.glassBackground
    if (choice != GlassBackground.NOW_PLAYING) return this.background(base)
    val art = rememberBlurredArtwork(WhiplashColors.nowPlayingArtwork)
    val view = androidx.compose.ui.platform.LocalView.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin.value = it.positionInWindow() }
        .drawBehind {
            drawRect(base)
            val bmp = art.value ?: return@drawBehind
            val winW = view.rootView.width.toFloat().coerceAtLeast(size.width)
            val winH = view.rootView.height.toFloat().coerceAtLeast(size.height)
            // Cover-crop the square art to the window's tall shape.
            val scale = maxOf(winW / bmp.width, winH / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val left = (winW - dw) / 2f - origin.value.x
            val top = (winH - dh) / 2f - origin.value.y
            // The image is placed in window coordinates, so it extends past
            // this page's own bounds; clip it, or it spills (undimmed) over
            // the header and anything else outside the page.
            clipRect {
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(dw.toInt(), dh.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                )
            }
            // Dim so white text and icons always read (like Apple Music's
            // darkened backdrop), a little deeper at the bottom where the
            // tab bar and mini player sit.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.50f),
                    1f to Color.Black.copy(alpha = 0.66f),
                    startY = -origin.value.y,
                    endY = winH - origin.value.y,
                ),
            )
        }
}

/** Blurred, small copies of covers, shared by every page drawing the backdrop. */
private val blurredArtCache = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?) = size > 6
}

/**
 * The cover at [url] as a 64px, heavily blurred bitmap. Separate small Coil
 * request that stays out of the memory cache, so the full-size artwork shown
 * elsewhere keeps its quality. Keeps the previous image until the next is
 * ready, so track changes don't flash.
 */
@Composable
private fun rememberBlurredArtwork(url: String?): androidx.compose.runtime.State<androidx.compose.ui.graphics.ImageBitmap?> {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = url?.let { synchronized(blurredArtCache) { blurredArtCache[it] } },
        url,
    ) {
        if (url == null) { value = null; return@produceState }
        synchronized(blurredArtCache) { blurredArtCache[url] }?.let { value = it; return@produceState }
        val request = coil.request.ImageRequest.Builder(context)
            .data(url)
            .size(64)
            .allowHardware(false)
            .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            .build()
        val result = runCatching { coil.Coil.imageLoader(context).execute(request) }.getOrNull()
        val bitmap = ((result as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: return@produceState
        val blurred = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { boxBlur(bitmap) }
        synchronized(blurredArtCache) { blurredArtCache[url] = blurred }
        value = blurred
    }
}

/** Three-pass box blur (close to Gaussian) of a small bitmap, done on the CPU. */
private fun boxBlur(src: android.graphics.Bitmap): androidx.compose.ui.graphics.ImageBitmap {
    val w = 48
    val h = 48
    val small = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    val r = 4
    repeat(3) {
        for (pass in 0..1) {
            val from = if (pass == 0) px else tmp
            val to = if (pass == 0) tmp else px
            for (y in 0 until h) for (x in 0 until w) {
                var rs = 0; var gs = 0; var bs = 0; var n = 0
                for (k in -r..r) {
                    val xx = if (pass == 0) (x + k).coerceIn(0, w - 1) else x
                    val yy = if (pass == 1) (y + k).coerceIn(0, h - 1) else y
                    val c = from[yy * w + xx]
                    rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
                }
                to[y * w + x] = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
            }
        }
    }
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out.asImageBitmap()
}

/**
 * Inside a dialog or bottom sheet: turns on the system's cross-window blur
 * behind it (Android 12+), so the app shows through the translucent sheet
 * genuinely blurred. No-op elsewhere or when the device disables it.
 */
@Composable
fun GlassWindowBlur(radius: Dp = 28.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = androidx.compose.ui.platform.LocalView.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { radius.roundToPx() }
    androidx.compose.runtime.DisposableEffect(view, px) {
        var v: android.view.ViewParent? = view.parent
        var window: android.view.Window? = null
        while (v != null && window == null) {
            window = (v as? androidx.compose.ui.window.DialogWindowProvider)?.window
            v = v.parent
        }
        val provider = view as? androidx.compose.ui.window.DialogWindowProvider
        if (window == null) window = provider?.window
        runCatching {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window?.attributes = window?.attributes?.apply { blurBehindRadius = px }
        }
        onDispose { }
    }
}


/**
 * A soft halo behind text sitting on clear glass, so labels stay readable
 * over bright content without tinting the glass. Null (no shadow) outside
 * Liquid Glass and on light glass, where dark text needs no help.
 */
@Composable
fun glassTextShadow(): androidx.compose.ui.graphics.Shadow? =
    if (LocalGlassBackdrop.current != null && !WhiplashColors.isLight) {
        androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)
    } else null


/**
 * Makes glass read as a solid, curved object even over a flat background
 * (where there's nothing for the lens to bend), the way Apple's controls do:
 *
 *  - a thin specular rim, bright where the edge faces the light (top-left)
 *    and a faint bounce light on the opposite edge;
 *  - a bevel: the glass's thickness catching light on the lit side and
 *    falling into shade on the far side, fading toward the flat middle;
 *  - a soft gloss over the upper half.
 *
 * Android 13+: an AGSL shader over the shape's signed distance field, so it
 * follows any rounded shape exactly. Older: gradient strokes approximating it.
 */
private fun DrawScope.drawGlassMaterial(state: GlassState, path: Path, shape: CornerBasedShape, light: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.minDimension > 0f) {
        val shader = (state.material as? RuntimeShader) ?: RuntimeShader(MATERIAL_SHADER).also { state.material = it }
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("cornerRadii", cornerRadii(shape, size, this))
        // Big surfaces (cards, bars) get a narrower bevel than small buttons,
        // or their whole border turns into a glowing band.
        val big = size.minDimension > 96.dp.toPx()
        shader.setFloatUniform("rim", (if (light) 1.4.dp else 1.0.dp).toPx())
        shader.setFloatUniform("bevel", minOf(if (big) 8.dp.toPx() else 14.dp.toPx(), size.minDimension * 0.25f))
        shader.setFloatUniform("lightDir", -0.55f, -0.83f)
        // Dark glass: Apple-style crisp, thin highlight that only catches at
        // the edges facing the light, a faint bounce opposite, and depth from
        // a slightly darker far edge; never a white glow round the border.
        // Light glass: more light (white on white is subtle) and more shade.
        shader.setFloatUniform("lightAmount", if (light) 0.95f else 0.60f)
        shader.setFloatUniform("bevelLight", if (light) 0.45f else 0.07f)
        shader.setFloatUniform("bounce", if (light) 0.35f else 0.14f)
        shader.setFloatUniform("specPower", if (light) 1.4f else 3.0f)
        shader.setFloatUniform("glossAmount", if (light) 0.12f else 0.035f)
        shader.setFloatUniform("shadeAmount", if (light) 0.22f else 0.32f)
        clipPath(path) { drawRect(androidx.compose.ui.graphics.ShaderBrush(shader)) }
    } else {
        drawPath(
            path,
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.75f),
                0.3f to Color.White.copy(alpha = 0.12f),
                0.7f to Color.White.copy(alpha = 0.05f),
                1f to Color.White.copy(alpha = 0.40f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width = 1.3.dp.toPx()),
        )
        clipPath(path) {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.45f to Color.Transparent))
            drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.10f)))
        }
    }
}

private const val MATERIAL_SHADER = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float rim;
uniform float bevel;
uniform float2 lightDir;
uniform float lightAmount;
uniform float shadeAmount;
uniform float bevelLight;
uniform float bounce;
uniform float specPower;
uniform float glossAmount;

float radiusAt(float2 c, float4 r) {
    if (c.x >= 0.0) { return c.y <= 0.0 ? r.y : r.z; }
    return c.y <= 0.0 ? r.x : r.w;
}

float sdRoundedRect(float2 c, float2 halfSize, float radius) {
    float2 q = abs(c) - (halfSize - float2(radius));
    return length(max(q, 0.0)) - radius + min(max(q.x, q.y), 0.0);
}

float2 gradSdRoundedRect(float2 c, float2 halfSize, float radius) {
    float2 q = abs(c) - (halfSize - float2(radius));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(c) * normalize(max(q, 0.0) + float2(0.0001));
    }
    float gx = step(q.y, q.x);
    return sign(c) * float2(gx, 1.0 - gx);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 c = coord - halfSize;
    float radius = radiusAt(c, cornerRadii);
    float d = -sdRoundedRect(c, halfSize, radius);
    if (d < 0.0) { return half4(0.0); }
    float2 n = gradSdRoundedRect(c, halfSize, min(radius * 1.5, min(halfSize.x, halfSize.y)));
    // How much this bit of edge faces the light (outward normal vs light).
    float facing = dot(normalize(n), -normalize(lightDir));
    float lit = max(facing, 0.0);
    float away = max(-facing, 0.0);

    // Specular rim, plus a softer bounce on the far edge.
    float rimMask = 1.0 - smoothstep(0.0, rim, d);
    float spec = rimMask * (0.12 + 0.88 * pow(lit, specPower)) + rimMask * bounce * pow(away, 3.0);

    // Bevel: thickness lit on the near side, shaded on the far side.
    float b = 1.0 - smoothstep(0.0, bevel, d);
    b = b * b;
    float bevelLit = b * pow(lit, 1.2) * bevelLight;
    float bevelShade = b * pow(away, 1.2) * shadeAmount;

    // Gloss: a soft sheen over the upper half, fading to nothing by the middle.
    float t = (c.y + halfSize.y) / size.y;
    float gloss = (1.0 - smoothstep(0.0, 0.55, t)) * glossAmount * (1.0 - rimMask);

    float aLight = clamp((spec * 0.85 + bevelLit + gloss) * lightAmount, 0.0, 1.0);
    float aShade = clamp(bevelShade, 0.0, 1.0);
    // Premultiplied: white light over black shade.
    float a = aLight + aShade * (1.0 - aLight);
    return half4(aLight, aLight, aLight, a);
}
"""

/**
 * A soft shadow under a glass control, drawn only *outside* its shape (a
 * normal elevation shadow would show through clear glass as a dark smudge).
 * Lifts the control off the page so it reads as a 3D object.
 */
fun Modifier.glassShadow(shape: CornerBasedShape, elevation: Dp = 10.dp, strength: Float = 0.22f): Modifier =
    this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val body = outline.toPath()
        val e = elevation.toPx()
        clipPath(body, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            val steps = 8
            for (i in steps downTo 1) {
                val f = i / steps.toFloat()
                val grow = e * f
                val r = cornerRadii(shape, size, this)
                val rr = androidx.compose.ui.geometry.RoundRect(
                    left = -grow, top = -grow + e * 0.45f, right = size.width + grow, bottom = size.height + grow + e * 0.45f,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(r[0] + grow),
                    topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(r[1] + grow),
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(r[2] + grow),
                    bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(r[3] + grow),
                )
                drawPath(Path().apply { addRoundRect(rr) }, Color.Black.copy(alpha = strength / steps * (1.2f - f)))
            }
        }
    }

/**
 * Just the 3D glass shading (rim, bevel, gloss) over this element, for
 * surfaces that move within an already-glass container, e.g. the selected
 * segment of a glass tab bar.
 */
@Composable
fun Modifier.glassMaterial(shape: CornerBasedShape): Modifier {
    val state = remember { GlassState() }
    val light = WhiplashColors.isLight
    return this.drawWithContent {
        val path = shape.createOutline(size, layoutDirection, this).toPath()
        drawGlassMaterial(state, path, shape, light)
        drawContent()
    }
}
