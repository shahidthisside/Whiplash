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
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
    val floor = if (glassBlurSupported) 0.24f else 0.78f
    return (floor + (0.92f - floor) * opacity.coerceIn(0f, 1f)).coerceAtMost(0.92f)
}

private class GlassState {
    var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
    var effectKey: Any? = null
    var effect: androidx.compose.ui.graphics.RenderEffect? = null
    var shader: Any? = null
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
    blurRadius: Dp = 14.dp,
    refractionHeight: Dp = 18.dp,
    refractionAmount: Dp = 26.dp,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) {
        return this.drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutlineFill(outline, fallback)
            drawContent()
        }
    }
    val layer = rememberGraphicsLayer()
    val state = remember { GlassState() }
    val rimTop = Color.White
    return this
        .onGloballyPositioned { state.coordinates = it }
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = outline.toPath()
            val src = backdrop.coordinates
            val me = state.coordinates
            if (src != null && me != null && src.isAttached && me.isAttached) {
                val offset = runCatching { src.localPositionOf(me, Offset.Zero) }
                    .getOrElse { me.positionInWindow() - src.positionInWindow() }
                val blurPx = blurRadius.toPx()
                val pad = if (glassBlurSupported) ceil(blurPx * 2f).toInt() else 0
                layer.record(IntSize(size.width.toInt() + pad * 2, size.height.toInt() + pad * 2)) {
                    translate(pad - offset.x, pad - offset.y) { drawLayer(backdrop.layer) }
                }
                layer.topLeft = IntOffset(-pad, -pad)
                val key = listOf(size, blurPx, pad, shape.hashCode(), refractionHeight, refractionAmount)
                if (state.effectKey != key && glassBlurSupported) {
                    state.effectKey = key
                    state.effect = buildGlassEffect(
                        state = state,
                        size = size,
                        pad = pad.toFloat(),
                        blurPx = blurPx,
                        radii = cornerRadii(shape, size, this),
                        refractionHeight = refractionHeight.toPx(),
                        refractionAmount = refractionAmount.toPx(),
                    )
                }
                layer.renderEffect = state.effect
                clipPath(path) { drawLayer(layer) }
            }
            // Tint: how "frosted" the glass is, from the opacity setting.
            val alpha = glassTintAlpha(WhiplashColors.glassOpacity)
            drawPath(path, tint.copy(alpha = alpha))
            // Rim light: a bright specular edge top-left fading round the
            // shape, fainter again at the bottom-right, like light caught
            // in the thickness of the glass.
            drawPath(
                path,
                brush = Brush.linearGradient(
                    0f to rimTop.copy(alpha = 0.55f),
                    0.35f to rimTop.copy(alpha = 0.10f),
                    0.7f to rimTop.copy(alpha = 0.04f),
                    1f to rimTop.copy(alpha = 0.28f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            // Soft inner glow along the top edge.
            clipPath(path) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.35f to Color.Transparent,
                    ),
                )
            }
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
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    // Vibrancy: colours seen through glass look richer, not washed out.
    val vibrancy = RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) }),
    )
    val blurred = if (blurPx > 0f) {
        RenderEffect.createBlurEffect(blurPx, blurPx, vibrancy, Shader.TileMode.CLAMP)
    } else {
        vibrancy
    }
    val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.minDimension > 0f) {
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
 * The page background for the current theme: the Liquid Glass wallpaper in
 * that theme, the plain background colour in every other one. Opaque.
 */
@Composable
fun Modifier.appBackground(): Modifier {
    val base = WhiplashColors.background
    if (!WhiplashColors.isGlass) return this.background(base)
    val accent = WhiplashColors.accent
    val cool = androidx.compose.ui.graphics.lerp(accent, Color(0xFF3D7BFF), 0.6f)
    val warm = androidx.compose.ui.graphics.lerp(accent, Color(0xFFFF6FA8), 0.55f)
    return this.drawBehind {
        drawRect(base)
        val r = size.maxDimension * 0.7f
        fun pool(c: Color, x: Float, y: Float, a: Float) = drawRect(
            Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(size.width * x, size.height * y), r),
        )
        pool(cool, 0.1f, 0.08f, 0.30f)
        pool(warm, 0.95f, 0.45f, 0.22f)
        pool(accent, 0.2f, 0.95f, 0.20f)
    }
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
