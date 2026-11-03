package com.alignfit.app.ui

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ─── Exercise demo animation ──────────────────────────────────────────────────
// Looping, GIF-like demo for each exercise, drawn with Compose (no GIF decoder
// dependency, sharp at any size). Keyframes live in assets/exercise_animations.json
// as joint angles (degrees; 0 = facing direction, 90 = down, -90 = up). A frame
// only lists the angles that change; the rest carry over from the previous frame.
// "n" = near-side limbs (drawn in front), "f" = far-side limbs.

private const val SCENE_W = 160f
private const val SCENE_H = 100f
private const val FLOOR_Y = SCENE_H - 8f

private object Len {
    const val TRUNK_LOW = 13f; const val TRUNK_UP = 13f; const val NECK = 6f; const val HEAD = 5.5f
    const val UPPER_ARM = 13f; const val FOREARM = 12f; const val THIGH = 18f; const val SHIN = 17f
    const val FOOT = 6f; const val FOOT_FRONT = 2.5f
    const val SHOULDER_HALF = 8.5f; const val HIP_HALF = 4.5f
}

private val ANGLE_KEYS = listOf(
    "tl", "tu", "nk", "nua", "nfa", "fua", "ffa", "nth", "nsh", "nft", "fth", "fsh", "fft"
)
private val DEFAULT_POSE = mapOf(
    "tl" to -90f, "tu" to -90f, "nk" to -90f, "nua" to 92f, "nfa" to 90f, "fua" to 88f, "ffa" to 90f,
    "nth" to 90f, "nsh" to 90f, "nft" to 0f, "fth" to 90f, "fsh" to 90f, "fft" to 0f
)

class ExerciseAnimationSpec(
    val frames: List<Map<String, Float>>,
    val front: Boolean,
    val anchor: String,
    val props: Set<String>,
    val wall: Float?,
    val focus: List<String>,
    val moveMs: Int,
    val holdMs: Int
)

private var specCache: Map<String, ExerciseAnimationSpec>? = null

fun loadExerciseAnimations(context: Context): Map<String, ExerciseAnimationSpec> {
    specCache?.let { return it }
    val text = context.assets.open("exercise_animations.json").bufferedReader().use { it.readText() }
    val root = JSONObject(text)
    val specs = root.keys().asSequence().associateWith { id ->
        val o = root.getJSONObject(id)
        val framesJson = o.getJSONArray("frames")
        var current = DEFAULT_POSE
        val frames = (0 until framesJson.length()).map { i ->
            val f = framesJson.getJSONObject(i)
            current = current + f.keys().asSequence().associateWith { f.getDouble(it).toFloat() }
            current
        }
        val propsJson = o.optJSONArray("props")
        ExerciseAnimationSpec(
            frames = frames,
            front = o.optString("view") == "front",
            anchor = o.optString("anchor", "hip"),
            props = (0 until (propsJson?.length() ?: 0)).map { propsJson!!.getString(it) }.toSet(),
            wall = if (o.has("wall")) o.getDouble("wall").toFloat() else null,
            focus = o.optString("focus").split("+").filter { it.isNotBlank() },
            moveMs = o.optInt("move_ms", 1100),
            holdMs = o.optInt("hold_ms", 700)
        )
    }
    specCache = specs
    return specs
}

// ─── Forward kinematics ──────────────────────────────────────────────────────

private fun dir(deg: Float, len: Float): Offset {
    val r = Math.toRadians(deg.toDouble())
    return Offset((cos(r) * len).toFloat(), (sin(r) * len).toFloat())
}

private fun jointRadius(name: String) = when (name) {
    "head" -> Len.HEAD
    "hip", "mid", "shoulder" -> 4.5f
    else -> 3f
}

// Joint positions with the anchor joint at x = 0 and the lowest point on y = 0.
private fun placedJoints(spec: ExerciseAnimationSpec, p: Map<String, Float>): Map<String, Offset> {
    fun a(k: String) = p.getValue(k)
    val j = mutableMapOf<String, Offset>()
    j["hip"] = Offset.Zero
    j["mid"] = j.getValue("hip") + dir(a("tl"), Len.TRUNK_LOW)
    j["shoulder"] = j.getValue("mid") + dir(a("tu"), Len.TRUNK_UP)
    j["neck"] = j.getValue("shoulder") + dir(a("nk"), Len.NECK)
    j["head"] = j.getValue("neck") + dir(a("nk"), Len.HEAD)
    val foot = if (spec.front) Len.FOOT_FRONT else Len.FOOT
    for ((s, sign) in listOf("n" to -1f, "f" to 1f)) {
        // Front view: limbs attach at shoulder/hip width, perpendicular to the trunk.
        j["${s}_sho"] = j.getValue("shoulder") +
            if (spec.front) dir(a("tu") + 90f * sign, Len.SHOULDER_HALF) else Offset.Zero
        j["${s}_hipj"] = j.getValue("hip") +
            if (spec.front) dir(a("tl") + 90f * sign, Len.HIP_HALF) else Offset.Zero
        j["${s}_elbow"] = j.getValue("${s}_sho") + dir(a("${s}ua"), Len.UPPER_ARM)
        j["${s}_hand"] = j.getValue("${s}_elbow") + dir(a("${s}fa"), Len.FOREARM)
        j["${s}_knee"] = j.getValue("${s}_hipj") + dir(a("${s}th"), Len.THIGH)
        j["${s}_ankle"] = j.getValue("${s}_knee") + dir(a("${s}sh"), Len.SHIN)
        j["${s}_toe"] = j.getValue("${s}_ankle") + dir(a("${s}ft"), foot)
    }
    val ax = j[spec.anchor]?.x ?: 0f
    val ground = j.filterKeys { it != "neck" }.maxOf { (k, v) -> v.y + jointRadius(k) }
    return j.mapValues { (_, v) -> Offset(v.x - ax, v.y - ground) }
}

// Interpolates each angle along the shorter way round (165° -> -140° turns 55°, not 305°).
private fun lerpPose(a: Map<String, Float>, b: Map<String, Float>, t: Float) =
    ANGLE_KEYS.associateWith { k ->
        val delta = ((b.getValue(k) - a.getValue(k)) % 360f + 540f) % 360f - 180f
        a.getValue(k) + delta * t
    }

// ─── Composable ──────────────────────────────────────────────────────────────

private val SceneBg = Color(0xFFF4F4F4)
private val FloorColor = Color(0xFFD6D6D6)
private val MatColor = Color(0xFFDDDDDD)
private val PropColor = Color(0xFFC2C2C2)
private val WallColor = Color(0xFFE4E4E4)
private val NearColor = Color(0xFF1A1A1A)
private val FarColor = Color(0xFFA8A8A8)
private val FocusColor = Color(0xFFB8B8B8)

// Draws nothing when the exercise has no keyframes (check hasExerciseAnimation first).
@Composable
fun ExerciseAnimation(exerciseId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val spec = remember(exerciseId) {
        runCatching { loadExerciseAnimations(context)[exerciseId] }.getOrNull()
    } ?: return

    // Fit all keyframes into the scene once, so the figure never jumps around.
    val fit = remember(spec) {
        val pts = spec.frames.flatMap { placedJoints(spec, it).values }
        val minX = pts.minOf { it.x } - 8f
        val maxX = pts.maxOf { it.x } + 8f
        val minY = pts.minOf { it.y } - 8f
        val scale = min(1.3f, min(SCENE_W / (maxX - minX), (FLOOR_Y - 2f) / -minY))
        Triple(scale, SCENE_W / 2f - (minX + maxX) / 2f * scale, Pair(minX, maxX))
    }

    val n = spec.frames.size
    val step = spec.moveMs + spec.holdMs
    val transition = rememberInfiniteTransition(label = "exercise")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = (n * step).toFloat(),
        animationSpec = infiniteRepeatable(tween(n * step, easing = LinearEasing)),
        label = "time"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(SCENE_W / SCENE_H)
            .clip(RoundedCornerShape(16.dp))
    ) {
        val i = (time / step).toInt().coerceIn(0, n - 1)
        val local = time - i * step
        val t = if (local < spec.holdMs) 0f
        else FastOutSlowInEasing.transform((local - spec.holdMs) / spec.moveMs)
        val pose = lerpPose(spec.frames[i], spec.frames[(i + 1) % n], t)
        drawExerciseScene(spec, placedJoints(spec, pose), fit.first, fit.second, fit.third, pulse)
    }
}

private fun DrawScope.drawExerciseScene(
    spec: ExerciseAnimationSpec,
    j: Map<String, Offset>,
    scale: Float,
    offsetX: Float,
    xRange: Pair<Float, Float>,
    pulse: Float
) {
    val px = size.width / SCENE_W
    fun T(p: Offset) = Offset((offsetX + p.x * scale) * px, (FLOOR_Y + p.y * scale) * px)
    fun u(v: Float) = v * scale * px

    drawRect(SceneBg)
    if ("backwall" in spec.props) {
        drawRect(WallColor.copy(alpha = 0.6f), size = Size(size.width, FLOOR_Y * px))
    }
    spec.wall?.let { w ->
        val x = T(Offset(w, 0f)).x
        drawRect(WallColor, Offset(x - u(2f), 0f), Size(u(4f), FLOOR_Y * px))
    }
    drawLine(FloorColor, Offset(0f, FLOOR_Y * px), Offset(size.width, FLOOR_Y * px), strokeWidth = px)
    if ("mat" in spec.props) {
        val x0 = T(Offset(xRange.first + 4f, 0f)).x
        val x1 = T(Offset(xRange.second - 4f, 0f)).x
        drawRoundRect(
            MatColor, Offset(x0, FLOOR_Y * px), Size(x1 - x0, 2.5f * px), CornerRadius(px, px)
        )
    }
    if ("chair" in spec.props) {
        val hip = T(j.getValue("hip"))
        val seatY = hip.y + u(5.5f)
        drawRoundRect(
            PropColor, Offset(hip.x - u(10f), seatY), Size(u(18f), u(3f)), CornerRadius(px, px)
        )
        listOf(-9f, 6f).forEach { dx ->
            drawLine(
                PropColor, Offset(hip.x + u(dx), seatY), Offset(hip.x + u(dx), FLOOR_Y * px),
                strokeWidth = u(2f)
            )
        }
    }

    // Soft pulsing glow on the target area.
    if (spec.focus.isNotEmpty()) {
        val pts = spec.focus.mapNotNull { j[it] }
        if (pts.isNotEmpty()) {
            val c = T(Offset(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size,
                pts.sumOf { it.y.toDouble() }.toFloat() / pts.size))
            drawCircle(
                Brush.radialGradient(
                    listOf(FocusColor.copy(alpha = pulse), Color.Transparent), c, u(12f)
                ),
                radius = u(12f), center = c
            )
        }
    }

    fun seg(a: String, b: String, w: Float, color: Color) =
        drawLine(color, T(j.getValue(a)), T(j.getValue(b)), strokeWidth = u(w), cap = StrokeCap.Round)

    fun limbs(s: String, color: Color) {
        seg("${s}_knee", "${s}_ankle", 5.5f, color)
        seg("${s}_hipj", "${s}_knee", 6.5f, color)
        seg("${s}_ankle", "${s}_toe", 4f, color)
        seg("${s}_sho", "${s}_elbow", 5.5f, color)
        seg("${s}_elbow", "${s}_hand", 4.5f, color)
    }

    val torso = if (spec.front) 12f else 9f
    limbs("f", if (spec.front) NearColor else FarColor)
    if (spec.front) {
        seg("n_sho", "f_sho", 8f, NearColor)
        seg("n_hipj", "f_hipj", 8f, NearColor)
    }
    seg("hip", "mid", torso, NearColor)
    seg("mid", "shoulder", torso, NearColor)
    seg("shoulder", "neck", 4.5f, NearColor)
    drawCircle(NearColor, radius = u(Len.HEAD), center = T(j.getValue("head")))
    limbs("n", NearColor)
}

fun hasExerciseAnimation(context: Context, exerciseId: String): Boolean =
    runCatching { exerciseId in loadExerciseAnimations(context) }.getOrDefault(false)
