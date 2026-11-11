package com.bungbungi.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.bungbungi.app.logic.AvatarPart
import com.bungbungi.app.logic.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 아바타의 순간 기분 (주행 중 위반 직후 등) */
enum class Mood { CALM, HAPPY, DIZZY, SLEEPY }

/** 단계별 색 팔레트 */
internal data class Palette(
    val base: Color,
    val light: Color,
    val dark: Color,
    val glow: Color,
    val lamp: Color,
    val bumperTop: Color,
    val bumperBottom: Color,
) {
    val line: Color get() = lerp(dark, Color.Black, 0.45f)
}

object AvatarColors {
    fun body(stage: Stage): Color = paletteOf(stage).base

    internal fun paletteOf(stage: Stage): Palette = when (stage) {
        Stage.ANGEL -> Palette(
            Color(0xFFFFBFDB), Color(0xFFFFE6F1), Color(0xFFEE8FBB), Color(0xFFFFD9F0), Color(0xFFFF5C9A),
            Color(0xFFFFFFFF), Color(0xFFE6DDF2),
        )
        Stage.GENTLE -> Palette(
            Color(0xFF7FE0A8), Color(0xFFC4F6D8), Color(0xFF45B97C), Color(0xFFB5F5CF), Color(0xFFFFF2A8),
            Color(0xFFFFFFFF), Color(0xFFD5E2DB),
        )
        Stage.NORMAL -> Palette(
            Color(0xFF7FB2EE), Color(0xFFC3DDFA), Color(0xFF4A82CC), Color(0xFFB4D2F7), Color(0xFFFFF2A8),
            Color(0xFFF5F7FA), Color(0xFFC9D1DC),
        )
        Stage.GRUMPY -> Palette(
            Color(0xFFA79C8C), Color(0xFFC9C0B2), Color(0xFF766C5E), Color(0xFF9A9082), Color(0xFFE6D58F),
            Color(0xFFC9C6C0), Color(0xFF8E8A84),
        )
        Stage.WILD -> Palette(
            Color(0xFF8E3232), Color(0xFFC45A4A), Color(0xFF4F1717), Color(0xFFFF5A2A), Color(0xFFFF3B1F),
            Color(0xFF6B6B70), Color(0xFF3B3B40),
        )
    }

    /** 점수(0~100) → 알 색: 낮으면 회색빛 붉은색, 높으면 파스텔 분홍 */
    fun egg(score: Double?): Color {
        val s = ((score ?: 70.0) / 100.0).toFloat().coerceIn(0f, 1f)
        return lerp(Color(0xFFB07A7A), Color(0xFFFFE3EC), s)
    }
}

private val Ink = Color(0xFF2A2433)
private val GlassTop = Color(0xFFF7FCFF)
private val GlassBottom = Color(0xFFCFE6F7)
private const val TAU = (2.0 * PI).toFloat()

/** 차 모양 아바타(정면). 단계와 부위(주된 위반)에 따라 얼굴·장식이 바뀐다. */
@Composable
fun CarAvatar(stage: Stage, part: AvatarPart, mood: Mood, modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "avatar")
    val bob by anim.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val cycle by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "cycle")
    Canvas(modifier) {
        val u = minOf(size.width / 12f, size.height / 9f) // 기준 단위
        val shake = if (mood == Mood.DIZZY) sin(cycle * TAU * 7f) * u * 0.07f else 0f
        val lift = (bob - 0.5f) * u * 0.24f
        translate(size.width / 2f, size.height / 2f + u * 0.7f) {
            drawBackGlow(u, AvatarColors.paletteOf(stage), stage)
            drawGroundShadow(u, bob)
            translate(shake, lift) { drawCar(u, stage, part, mood, cycle) }
        }
    }
}

// ───────────────────────────── 차 ─────────────────────────────

private fun DrawScope.drawBackGlow(u: Float, p: Palette, stage: Stage) {
    val alpha = when (stage) {
        Stage.ANGEL -> 0.34f
        Stage.WILD -> 0.30f
        Stage.GRUMPY -> 0.08f
        else -> 0.18f
    }
    val c = Offset(0f, -u * 0.4f)
    drawCircle(
        Brush.radialGradient(listOf(p.glow.copy(alpha = alpha), p.glow.copy(alpha = 0f)), center = c, radius = u * 5.6f),
        radius = u * 5.6f, center = c,
    )
}

private fun DrawScope.drawGroundShadow(u: Float, bob: Float) {
    val c = Offset(0f, u * 3.05f)
    val r = u * (3.7f - 0.25f * bob)
    scale(1f, 0.17f, pivot = c) {
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r),
            radius = r, center = c,
        )
    }
}

private fun DrawScope.drawCar(u: Float, stage: Stage, part: AvatarPart, mood: Mood, cycle: Float) {
    val p = AvatarColors.paletteOf(stage)
    val line = p.line
    val outline = Stroke(width = u * 0.085f, join = StrokeJoin.Round, cap = StrokeCap.Round)
    val wave = sin(cycle * TAU)
    val wild = stage == Stage.WILD
    val smoky = part == AvatarPart.EXHAUST_SMOKE || wild

    // ── 뒤쪽 장식 ──
    if (smoky) drawSmoke(u, cycle)
    if (wild) drawBackFlames(u, cycle)
    if (stage == Stage.ANGEL) {
        drawWing(u, -1f, wave)
        drawWing(u, 1f, wave)
    }

    // ── 타이어 ──
    listOf(-1f, 1f).forEach { s ->
        val left = if (s < 0) -u * 3.05f else u * 1.95f
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF4A4A52), Color(0xFF17171B)), startY = u * 1.5f, endY = u * 3.0f),
            Offset(left, u * 1.5f), Size(u * 1.1f, u * 1.5f), CornerRadius(u * 0.38f),
        )
        for (i in 0 until 3) { // 트레드
            val y = u * (2.45f + i * 0.17f)
            drawLine(Color(0xFF05050A).copy(alpha = 0.6f), Offset(left + u * 0.18f, y), Offset(left + u * 0.92f, y), u * 0.05f)
        }
    }

    // ── 사이드미러 ──
    listOf(-1f, 1f).forEach { s ->
        val c = Offset(s * u * 3.02f, -u * 0.78f)
        drawLine(line, Offset(s * u * 2.5f, -u * 0.5f), c, u * 0.16f, StrokeCap.Round)
        val tl = c - Offset(u * 0.42f, u * 0.3f)
        drawOval(Brush.verticalGradient(listOf(p.light, p.dark), startY = tl.y, endY = tl.y + u * 0.6f), tl, Size(u * 0.84f, u * 0.6f))
        drawOval(line, tl, Size(u * 0.84f, u * 0.6f), style = outline)
    }

    // ── 지붕(캐빈) ──
    val cabin = Path().apply {
        moveTo(-u * 2.8f, -u * 0.2f)
        cubicTo(-u * 2.65f, -u * 2.2f, -u * 2.25f, -u * 3.02f, -u * 1.3f, -u * 3.08f)
        lineTo(u * 1.3f, -u * 3.08f)
        cubicTo(u * 2.25f, -u * 3.02f, u * 2.65f, -u * 2.2f, u * 2.8f, -u * 0.2f)
        close()
    }
    drawPath(cabin, Brush.verticalGradient(listOf(p.light, p.base, p.dark), startY = -u * 3.1f, endY = -u * 0.2f))
    drawPath(cabin, line, style = outline)
    drawLine(Color.White.copy(alpha = 0.55f), Offset(-u * 1.15f, -u * 2.9f), Offset(u * 0.2f, -u * 2.9f), u * 0.09f, StrokeCap.Round)

    // ── 앞유리 ──
    val glass = Path().apply {
        moveTo(-u * 2.32f, -u * 0.42f)
        cubicTo(-u * 2.22f, -u * 1.9f, -u * 1.95f, -u * 2.6f, -u * 1.15f, -u * 2.66f)
        lineTo(u * 1.15f, -u * 2.66f)
        cubicTo(u * 1.95f, -u * 2.6f, u * 2.22f, -u * 1.9f, u * 2.32f, -u * 0.42f)
        close()
    }
    val glassColors = if (wild) listOf(Color(0xFFFFE3D6), Color(0xFFE8A994)) else listOf(GlassTop, GlassBottom)
    drawPath(glass, Brush.verticalGradient(glassColors, startY = -u * 2.7f, endY = -u * 0.4f))
    clipPath(glass) {
        // 유리 반사
        val streak = Path().apply {
            moveTo(-u * 2.4f, -u * 1.2f); lineTo(-u * 1.2f, -u * 2.8f); lineTo(-u * 0.75f, -u * 2.8f); lineTo(-u * 2.4f, -u * 0.6f); close()
        }
        drawPath(streak, Color.White.copy(alpha = 0.55f))
        val streak2 = Path().apply {
            moveTo(-u * 2.4f, -u * 0.3f); lineTo(-u * 0.5f, -u * 2.8f); lineTo(-u * 0.32f, -u * 2.8f); lineTo(-u * 2.4f, -u * 0.05f); close()
        }
        drawPath(streak2, Color.White.copy(alpha = 0.35f))
        if (wild || stage == Stage.GRUMPY) { // 금 간 유리
            val crack = Path().apply {
                moveTo(u * 2.3f, -u * 2.2f); lineTo(u * 1.75f, -u * 1.95f); lineTo(u * 1.95f, -u * 1.6f); lineTo(u * 1.5f, -u * 1.35f)
                moveTo(u * 1.75f, -u * 1.95f); lineTo(u * 1.45f, -u * 2.25f)
            }
            drawPath(crack, Color.White.copy(alpha = 0.9f), style = Stroke(u * 0.05f, join = StrokeJoin.Round))
            drawPath(crack, line.copy(alpha = 0.35f), style = Stroke(u * 0.02f))
        }
    }
    drawPath(glass, line, style = Stroke(u * 0.06f, join = StrokeJoin.Round))

    // ── 눈 ──
    drawEyes(u, stage, part, mood, cycle)

    // ── 차체 ──
    val bodyTop = -u * 0.55f
    val bodySize = Size(u * 6.9f, u * 3.0f)
    val bodyTl = Offset(-u * 3.45f, bodyTop)
    drawRoundRect(
        Brush.verticalGradient(listOf(p.light, p.base, p.base, p.dark), startY = bodyTop, endY = bodyTop + bodySize.height),
        bodyTl, bodySize, CornerRadius(u * 1.15f),
    )
    // 보닛 하이라이트
    drawRoundRect(Color.White.copy(alpha = 0.42f), Offset(-u * 2.5f, bodyTop + u * 0.16f), Size(u * 5.0f, u * 0.24f), CornerRadius(u * 0.12f))
    rotate(-28f, Offset(-u * 2.95f, u * 0.05f)) {
        drawOval(Color.White.copy(alpha = 0.5f), Offset(-u * 3.13f, -u * 0.2f), Size(u * 0.36f, u * 0.7f))
    }
    drawRoundRect(line, bodyTl, bodySize, CornerRadius(u * 1.15f), style = outline)

    // 볼터치
    if (stage == Stage.ANGEL || stage == Stage.GENTLE) {
        listOf(-1f, 1f).forEach { s ->
            val c = Offset(s * u * 1.45f, u * 1.02f)
            val pink = Color(0xFFFF7FAE)
            scale(1f, 0.6f, pivot = c) {
                drawCircle(Brush.radialGradient(listOf(pink.copy(alpha = 0.75f), pink.copy(alpha = 0f)), center = c, radius = u * 0.5f), u * 0.5f, c)
            }
        }
    }

    // ── 헤드라이트 ──
    drawHeadlight(u, -1f, stage, p, wave)
    drawHeadlight(u, 1f, stage, p, wave)

    // ── 입 ──
    drawMouth(u, stage, mood)

    // ── 범퍼 ──
    val dented = part == AvatarPart.DENTED_BUMPER || wild
    val bumperBrush = Brush.verticalGradient(listOf(p.bumperTop, p.bumperBottom), startY = u * 1.72f, endY = u * 2.3f)
    if (dented) {
        val bp = Path().apply {
            moveTo(-u * 3.0f, u * 1.9f)
            lineTo(-u * 1.7f, u * 1.74f); lineTo(-u * 1.25f, u * 2.0f); lineTo(-u * 0.75f, u * 1.72f)
            lineTo(u * 2.85f, u * 1.72f)
            cubicTo(u * 3.1f, u * 1.72f, u * 3.1f, u * 2.3f, u * 2.85f, u * 2.3f)
            lineTo(-u * 2.8f, u * 2.36f)
            cubicTo(-u * 3.1f, u * 2.36f, -u * 3.15f, u * 1.95f, -u * 3.0f, u * 1.9f)
            close()
        }
        drawPath(bp, bumperBrush)
        drawPath(bp, line, style = Stroke(u * 0.06f, join = StrokeJoin.Round))
        val cracks = Path().apply {
            moveTo(-u * 1.25f, u * 2.0f); lineTo(-u * 1.4f, u * 2.2f); lineTo(-u * 1.2f, u * 2.32f)
            moveTo(-u * 1.25f, u * 2.0f); lineTo(-u * 1.0f, u * 2.15f)
        }
        drawPath(cracks, line, style = Stroke(u * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    } else {
        drawRoundRect(bumperBrush, Offset(-u * 3.02f, u * 1.72f), Size(u * 6.04f, u * 0.58f), CornerRadius(u * 0.29f))
        drawRoundRect(line, Offset(-u * 3.02f, u * 1.72f), Size(u * 6.04f, u * 0.58f), CornerRadius(u * 0.29f), style = Stroke(u * 0.06f))
        drawLine(Color.White.copy(alpha = 0.7f), Offset(-u * 2.6f, u * 1.84f), Offset(u * 2.6f, u * 1.84f), u * 0.06f, StrokeCap.Round)
    }
    // 번호판
    val plateTl = Offset(-u * 0.6f, u * 1.82f)
    drawRoundRect(Color.White, plateTl, Size(u * 1.2f, u * 0.4f), CornerRadius(u * 0.08f))
    drawRoundRect(line.copy(alpha = 0.7f), plateTl, Size(u * 1.2f, u * 0.4f), CornerRadius(u * 0.08f), style = Stroke(u * 0.04f))
    when (stage) {
        Stage.ANGEL, Stage.GENTLE -> drawHeart(Offset(0f, u * 2.03f), u * 0.13f, Color(0xFFFF5C9A))
        Stage.WILD -> {
            drawLine(Color(0xFFD92B1E), Offset(-u * 0.13f, u * 1.92f), Offset(u * 0.13f, u * 2.12f), u * 0.06f, StrokeCap.Round)
            drawLine(Color(0xFFD92B1E), Offset(u * 0.13f, u * 1.92f), Offset(-u * 0.13f, u * 2.12f), u * 0.06f, StrokeCap.Round)
        }
        else -> for (i in -1..1) drawCircle(line.copy(alpha = 0.55f), u * 0.055f, Offset(i * u * 0.26f, u * 2.02f))
    }

    // ── 앞쪽 장식·부위 ──
    if (part == AvatarPart.SIDE_SCRATCH || stage == Stage.GRUMPY || wild) {
        drawScratches(u, line, if (part == AvatarPart.SIDE_SCRATCH) 4 else 2)
    }
    if (stage == Stage.GRUMPY || wild) drawBandage(u)
    if (part == AvatarPart.LOOSE_BELT) drawLooseBelt(u, wave)
    when (stage) {
        Stage.ANGEL -> {
            drawHalo(u, wave)
            val spots = listOf(
                Triple(-4.4f, -2.9f, 0.42f), Triple(4.3f, -3.3f, 0.5f), Triple(2.9f, -4.1f, 0.3f),
                Triple(-3.2f, -4.0f, 0.26f), Triple(4.9f, 1.9f, 0.3f), Triple(-5.0f, 1.6f, 0.34f),
            )
            spots.forEachIndexed { i, (x, y, r) ->
                val a = (sin((cycle + i / 6f) * TAU * 2f) + 1f) / 2f
                drawSparkle(Offset(x * u, y * u), u * r * (0.55f + 0.45f * a), Color(0xFFFFE892).copy(alpha = 0.35f + 0.65f * a))
            }
        }
        Stage.GENTLE -> drawFlower(u, Offset(u * 1.55f, -u * 3.45f), wave)
        Stage.NORMAL -> {
            drawLine(line, Offset(u * 1.6f, -u * 3.0f), Offset(u * 1.95f, -u * 3.85f), u * 0.07f, StrokeCap.Round)
            drawCircle(Color(0xFFFFC94D), u * 0.17f, Offset(u * 1.95f, -u * 3.85f))
            drawCircle(line, u * 0.17f, Offset(u * 1.95f, -u * 3.85f), style = Stroke(u * 0.05f))
        }
        Stage.GRUMPY -> drawAngerMark(u, Offset(u * 2.75f, -u * 3.0f), 0.9f + 0.1f * wave)
        Stage.WILD -> {
            listOf(-1f, 1f).forEach { s -> // 뿔
                val horn = Path().apply {
                    moveTo(s * u * 1.0f, -u * 3.05f); lineTo(s * u * 1.75f, -u * 4.05f); lineTo(s * u * 1.9f, -u * 2.95f); close()
                }
                drawPath(horn, Brush.verticalGradient(listOf(Color(0xFFFFE0A3), Color(0xFFB8451F)), startY = -u * 4.05f, endY = -u * 2.95f))
                drawPath(horn, line, style = Stroke(u * 0.07f, join = StrokeJoin.Round))
            }
            drawAngerMark(u, Offset(u * 3.3f, -u * 2.5f), 1f + 0.12f * wave)
        }
    }
    if (mood == Mood.SLEEPY) drawZzz(u, cycle)
}

private fun DrawScope.drawHeadlight(u: Float, s: Float, stage: Stage, p: Palette, wave: Float) {
    val c = Offset(s * u * 2.42f, u * 0.72f)
    val r = u * 0.52f
    val baseAlpha = when (stage) {
        Stage.GRUMPY -> 0.15f
        Stage.WILD -> 0.55f
        else -> 0.4f
    }
    val glowAlpha = baseAlpha * (0.85f + 0.15f * wave)
    drawCircle(Brush.radialGradient(listOf(p.lamp.copy(alpha = glowAlpha), p.lamp.copy(alpha = 0f)), center = c, radius = r * 2.2f), r * 2.2f, c)
    drawCircle(Color.White, r, c)
    when (stage) {
        Stage.ANGEL -> {
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFE3EE)), center = c, radius = r), r, c)
            drawHeart(c + Offset(0f, u * 0.02f), r * 0.72f, Color(0xFFFF5C9A))
        }
        Stage.WILD -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF5A1F), Color(0xFFB81B10)), center = c, radius = r), r, c)
            drawCircle(Color(0xFF2A0606), r * 0.22f, c + Offset(0f, r * 0.15f))
        }
        Stage.GRUMPY -> drawCircle(Brush.radialGradient(listOf(Color(0xFFF3ECD0), Color(0xFFBFB07A)), center = c, radius = r), r, c)
        else -> {
            drawCircle(
                Brush.radialGradient(listOf(Color.White, Color(0xFFFFF4B8), Color(0xFFFFD766)), center = c + Offset(-r * 0.25f, -r * 0.25f), radius = r * 1.3f),
                r, c,
            )
            drawCircle(Color.White.copy(alpha = 0.95f), r * 0.22f, c + Offset(-r * 0.32f, -r * 0.32f))
        }
    }
    drawCircle(p.line, r, c, style = Stroke(u * 0.07f))
    // 화난·졸린 눈꺼풀
    if (stage == Stage.WILD || stage == Stage.GRUMPY) {
        val tilt = if (stage == Stage.WILD) 0.55f else 0.12f
        val lid = Path().apply {
            moveTo(c.x - s * r * 1.25f, c.y - r * 1.25f)
            lineTo(c.x + s * r * 1.25f, c.y - r * 1.25f)
            lineTo(c.x + s * r * 1.25f, c.y - r * (0.25f + tilt))
            lineTo(c.x - s * r * 1.25f, c.y - r * (0.25f - tilt))
            close()
        }
        drawPath(lid, p.base)
        drawLine(
            p.line, Offset(c.x - s * r * 1.2f, c.y - r * (0.25f - tilt)), Offset(c.x + s * r * 1.2f, c.y - r * (0.25f + tilt)),
            u * 0.09f, StrokeCap.Round,
        )
    }
}

// ───────────────────────────── 얼굴 ─────────────────────────────

private fun DrawScope.drawEyes(u: Float, stage: Stage, part: AvatarPart, mood: Mood, cycle: Float) {
    val eyeY = -u * 1.5f
    val eyes = listOf(Offset(-u * 0.95f, eyeY) to -1f, Offset(u * 0.95f, eyeY) to 1f)
    // 깜빡임: 4초 주기의 마지막 0.24초
    val blinkT = ((cycle - 0.94f) / 0.06f).coerceIn(0f, 1f)
    val open = 1f - sin(blinkT * PI.toFloat()) * 0.92f
    val flame = stage == Stage.WILD || part == AvatarPart.FLAME_EYES

    when {
        mood == Mood.DIZZY -> eyes.forEach { (c, s) ->
            rotate(cycle * 720f * s, c) { drawSpiral(c, u * 0.42f, Ink) }
        }
        mood == Mood.SLEEPY -> eyes.forEach { (c, _) ->
            drawArc(Ink, 20f, 140f, false, c - Offset(u * 0.36f, u * 0.3f), Size(u * 0.72f, u * 0.5f), style = Stroke(u * 0.11f, cap = StrokeCap.Round))
        }
        flame -> eyes.forEach { (c, s) ->
            val flick = 1f + 0.12f * sin((cycle * 9f + s) * TAU)
            drawFlame(c + Offset(0f, u * 0.12f), u * 0.5f, flick)
            drawLine(Ink, c + Offset(-s * u * 0.62f, -u * 0.3f), c + Offset(s * u * 0.42f, -u * 0.72f), u * 0.17f, StrokeCap.Round)
        }
        stage == Stage.ANGEL -> eyes.forEach { (c, _) ->
            scale(1f, open, pivot = c) {
                val tl = c - Offset(u * 0.46f, u * 0.56f)
                drawOval(
                    Brush.verticalGradient(listOf(Color(0xFF2B2140), Color(0xFF6B3FA0), Color(0xFFFF8FC0)), startY = tl.y, endY = tl.y + u * 1.12f),
                    tl, Size(u * 0.92f, u * 1.12f),
                )
                drawOval(Ink, tl, Size(u * 0.92f, u * 1.12f), style = Stroke(u * 0.06f))
                drawCircle(Color.White, u * 0.2f, c + Offset(-u * 0.14f, -u * 0.2f))
                drawCircle(Color.White, u * 0.09f, c + Offset(u * 0.17f, u * 0.2f))
            }
            // 속눈썹
            drawLine(Ink, c + Offset(u * 0.36f, -u * 0.42f * open), c + Offset(u * 0.6f, -u * 0.6f * open), u * 0.07f, StrokeCap.Round)
            drawLine(Ink, c + Offset(-u * 0.36f, -u * 0.42f * open), c + Offset(-u * 0.6f, -u * 0.6f * open), u * 0.07f, StrokeCap.Round)
        }
        stage == Stage.GENTLE && mood == Mood.HAPPY -> eyes.forEach { (c, _) ->
            drawArc(Ink, 200f, 140f, false, c - Offset(u * 0.42f, u * 0.2f), Size(u * 0.84f, u * 0.8f), style = Stroke(u * 0.14f, cap = StrokeCap.Round))
        }
        stage == Stage.GENTLE -> eyes.forEach { (c, _) ->
            scale(1f, open, pivot = c) {
                drawCircle(Ink, u * 0.4f, c)
                drawCircle(Color.White, u * 0.15f, c + Offset(-u * 0.12f, -u * 0.14f))
                drawCircle(Color.White, u * 0.07f, c + Offset(u * 0.14f, u * 0.14f))
            }
        }
        stage == Stage.NORMAL -> eyes.forEach { (c, _) ->
            scale(1f, open, pivot = c) {
                drawOval(Ink, c - Offset(u * 0.24f, u * 0.32f), Size(u * 0.48f, u * 0.64f))
                drawCircle(Color.White, u * 0.08f, c + Offset(-u * 0.07f, -u * 0.12f))
            }
        }
        else -> eyes.forEach { (c, s) -> // 까칠: 반쯤 감긴 눈 + 찡그린 눈썹
            drawCircle(Color.White, u * 0.4f, c)
            drawCircle(Ink, u * 0.21f, c + Offset(-s * u * 0.06f, u * 0.12f))
            drawCircle(Ink, u * 0.4f, c, style = Stroke(u * 0.06f))
            val lidY = c.y - u * 0.02f + (1f - open) * u * 0.4f
            val lid = Path().apply { // 유리색 눈꺼풀
                moveTo(c.x - u * 0.5f, c.y - u * 0.5f); lineTo(c.x + u * 0.5f, c.y - u * 0.5f)
                lineTo(c.x + u * 0.5f, lidY - s * u * 0.08f); lineTo(c.x - u * 0.5f, lidY + s * u * 0.08f); close()
            }
            drawPath(lid, Color(0xFFE2F0FA))
            drawLine(Ink, Offset(c.x - u * 0.46f, lidY + s * u * 0.08f), Offset(c.x + u * 0.46f, lidY - s * u * 0.08f), u * 0.08f, StrokeCap.Round)
            drawLine(Ink, c + Offset(-s * u * 0.55f, -u * 0.36f), c + Offset(s * u * 0.35f, -u * 0.62f), u * 0.14f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawMouth(u: Float, stage: Stage, mood: Mood) {
    val y = u * 0.98f
    when {
        mood == Mood.DIZZY -> {
            val p = Path().apply {
                moveTo(-u * 0.6f, y)
                for (i in 0 until 4) {
                    val x = -u * 0.6f + i * u * 0.3f
                    quadraticTo(x + u * 0.15f, y + if (i % 2 == 0) -u * 0.2f else u * 0.2f, x + u * 0.3f, y)
                }
            }
            drawPath(p, Ink, style = Stroke(u * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        mood == Mood.SLEEPY -> {
            drawOval(Color(0xFF7A2B3A), Offset(-u * 0.16f, y - u * 0.14f), Size(u * 0.32f, u * 0.36f))
            drawOval(Ink, Offset(-u * 0.16f, y - u * 0.14f), Size(u * 0.32f, u * 0.36f), style = Stroke(u * 0.06f))
        }
        stage == Stage.WILD -> { // 이빨
            val m = Path().apply {
                moveTo(-u * 0.95f, y - u * 0.22f); lineTo(u * 0.95f, y - u * 0.22f)
                cubicTo(u * 0.9f, y + u * 0.45f, -u * 0.9f, y + u * 0.45f, -u * 0.95f, y - u * 0.22f); close()
            }
            drawPath(m, Color(0xFF2A0606))
            clipPath(m) {
                for (i in 0 until 5) {
                    val x = -u * 0.95f + i * u * 0.38f
                    val tooth = Path().apply { moveTo(x, y - u * 0.24f); lineTo(x + u * 0.19f, y + u * 0.1f); lineTo(x + u * 0.38f, y - u * 0.24f); close() }
                    drawPath(tooth, Color.White)
                }
                for (i in 0 until 3) {
                    val x = -u * 0.55f + i * u * 0.38f
                    val tooth = Path().apply { moveTo(x, y + u * 0.5f); lineTo(x + u * 0.17f, y + u * 0.12f); lineTo(x + u * 0.34f, y + u * 0.5f); close() }
                    drawPath(tooth, Color.White)
                }
            }
            drawPath(m, Ink, style = Stroke(u * 0.07f, join = StrokeJoin.Round))
        }
        stage == Stage.GRUMPY ->
            drawArc(Ink, 205f, 130f, false, Offset(-u * 0.5f, y - u * 0.05f), Size(u * 1.0f, u * 0.55f), style = Stroke(u * 0.11f, cap = StrokeCap.Round))
        stage == Stage.NORMAL && mood != Mood.HAPPY ->
            drawArc(Ink, 30f, 120f, false, Offset(-u * 0.4f, y - u * 0.32f), Size(u * 0.8f, u * 0.5f), style = Stroke(u * 0.1f, cap = StrokeCap.Round))
        stage == Stage.ANGEL || mood == Mood.HAPPY -> { // 활짝 웃는 입
            val m = Path().apply {
                moveTo(-u * 0.6f, y - u * 0.16f); lineTo(u * 0.6f, y - u * 0.16f)
                cubicTo(u * 0.58f, y + u * 0.62f, -u * 0.58f, y + u * 0.62f, -u * 0.6f, y - u * 0.16f); close()
            }
            drawPath(m, Color(0xFF7A2B3A))
            clipPath(m) { drawOval(Color(0xFFFF8FA8), Offset(-u * 0.38f, y + u * 0.14f), Size(u * 0.76f, u * 0.6f)) }
            drawPath(m, Ink, style = Stroke(u * 0.07f, join = StrokeJoin.Round))
        }
        else -> // 순둥: 미소
            drawArc(Ink, 20f, 140f, false, Offset(-u * 0.5f, y - u * 0.42f), Size(u * 1.0f, u * 0.7f), style = Stroke(u * 0.11f, cap = StrokeCap.Round))
    }
}

// ───────────────────────────── 장식 ─────────────────────────────

private fun DrawScope.drawWing(u: Float, s: Float, wave: Float) {
    val pivot = Offset(s * u * 3.1f, u * 0.5f)
    rotate(s * wave * 7f, pivot) {
        val wing = Path().apply {
            moveTo(s * u * 3.0f, u * 0.2f)
            cubicTo(s * u * 4.0f, -u * 1.2f, s * u * 4.9f, -u * 2.7f, s * u * 5.7f, -u * 2.45f)
            cubicTo(s * u * 6.05f, -u * 1.6f, s * u * 5.8f, -u * 0.8f, s * u * 5.35f, -u * 0.3f)
            cubicTo(s * u * 5.7f, u * 0.2f, s * u * 5.2f, u * 0.75f, s * u * 4.7f, u * 0.6f)
            cubicTo(s * u * 4.85f, u * 1.05f, s * u * 4.3f, u * 1.4f, s * u * 3.85f, u * 1.15f)
            cubicTo(s * u * 3.7f, u * 1.5f, s * u * 3.2f, u * 1.45f, s * u * 3.0f, u * 1.1f)
            close()
        }
        drawPath(wing, Brush.verticalGradient(listOf(Color.White, Color(0xFFF1E9FF), Color(0xFFD9C8FA)), startY = -u * 2.6f, endY = u * 1.5f))
        drawPath(wing, Color(0xFFB9A2EC), style = Stroke(u * 0.07f, join = StrokeJoin.Round))
        val feathers = Path().apply {
            moveTo(s * u * 3.5f, u * 0.4f); cubicTo(s * u * 4.2f, -u * 0.6f, s * u * 4.8f, -u * 1.5f, s * u * 5.3f, -u * 1.8f)
            moveTo(s * u * 3.7f, u * 0.75f); cubicTo(s * u * 4.3f, u * 0.2f, s * u * 4.7f, -u * 0.2f, s * u * 5.1f, -u * 0.4f)
            moveTo(s * u * 3.6f, u * 1.0f); cubicTo(s * u * 3.9f, u * 0.8f, s * u * 4.2f, u * 0.65f, s * u * 4.5f, u * 0.55f)
        }
        drawPath(feathers, Color(0xFFCDBBF5).copy(alpha = 0.8f), style = Stroke(u * 0.05f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawHalo(u: Float, wave: Float) {
    val tl = Offset(-u * 1.4f, -u * 4.35f + wave * u * 0.08f)
    val sz = Size(u * 2.8f, u * 0.72f)
    drawOval(Color(0xFFFFE27A).copy(alpha = 0.3f), tl, sz, style = Stroke(u * 0.5f))
    drawOval(Brush.verticalGradient(listOf(Color(0xFFFFF6C2), Color(0xFFFFC93D)), startY = tl.y, endY = tl.y + sz.height), tl, sz, style = Stroke(u * 0.2f))
    drawArc(Color.White.copy(alpha = 0.9f), 200f, 60f, false, tl, sz, style = Stroke(u * 0.07f, cap = StrokeCap.Round))
}

private fun DrawScope.drawFlower(u: Float, c: Offset, wave: Float) {
    drawLine(Color(0xFF3F9E63), Offset(c.x - u * 0.15f, -u * 3.0f), c, u * 0.08f, StrokeCap.Round)
    rotate(wave * 8f, c) {
        for (i in 0 until 5) {
            val a = i / 5f * TAU
            drawCircle(Color.White, u * 0.2f, c + Offset(cos(a) * u * 0.24f, sin(a) * u * 0.24f))
        }
        drawCircle(Color(0xFFFFC94D), u * 0.16f, c)
    }
}

private fun DrawScope.drawAngerMark(u: Float, c: Offset, k: Float) {
    val r = u * 0.34f * k
    val col = Color(0xFFE5342B)
    val st = Stroke(u * 0.1f, cap = StrokeCap.Round)
    listOf(0f, 90f, 180f, 270f).forEach { deg ->
        rotate(deg, c) {
            drawArc(col, 200f, 50f, false, Offset(c.x + r * 0.25f, c.y + r * 0.25f), Size(r * 1.5f, r * 1.5f), style = st)
        }
    }
}

private fun DrawScope.drawScratches(u: Float, line: Color, n: Int) {
    for (i in 0 until n) {
        val x = -u * (2.05f - i * 0.3f)
        val a = Offset(x, -u * 0.12f)
        val b = Offset(x + u * 0.42f, u * 0.5f)
        drawLine(Color.White.copy(alpha = 0.55f), a + Offset(u * 0.05f, 0f), b + Offset(u * 0.05f, 0f), u * 0.05f, StrokeCap.Round)
        drawLine(line.copy(alpha = 0.8f), a, b, u * 0.06f, StrokeCap.Round)
    }
}

private fun DrawScope.drawBandage(u: Float) {
    val c = Offset(u * 1.25f, u * 0.05f)
    listOf(-32f, 32f).forEach { deg ->
        rotate(deg, c) {
            val tl = c - Offset(u * 0.52f, u * 0.15f)
            drawRoundRect(Color(0xFFF1D3A8), tl, Size(u * 1.04f, u * 0.3f), CornerRadius(u * 0.15f))
            drawRoundRect(Color(0xFF9C7B4F), tl, Size(u * 1.04f, u * 0.3f), CornerRadius(u * 0.15f), style = Stroke(u * 0.04f))
        }
    }
    drawCircle(Color(0xFFE2BC86), u * 0.13f, c)
}

private fun DrawScope.drawLooseBelt(u: Float, wave: Float) {
    val top = Offset(u * 2.55f, -u * 1.0f)
    rotate(wave * 6f, top) {
        val strap = Path().apply {
            moveTo(top.x, top.y)
            cubicTo(u * 3.3f, -u * 0.4f, u * 3.7f, u * 0.6f, u * 3.75f, u * 1.4f)
        }
        drawPath(strap, Color(0xFF2E2E36), style = Stroke(u * 0.3f, cap = StrokeCap.Round))
        drawPath(strap, Color(0xFF6E6E7A), style = Stroke(u * 0.2f, cap = StrokeCap.Round))
        val tl = Offset(u * 3.5f, u * 1.3f)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFF2F2F5), Color(0xFF9FA3AD)), startY = tl.y, endY = tl.y + u * 0.6f),
            tl, Size(u * 0.5f, u * 0.6f), CornerRadius(u * 0.1f),
        )
        drawRoundRect(Ink, tl, Size(u * 0.5f, u * 0.6f), CornerRadius(u * 0.1f), style = Stroke(u * 0.05f))
        drawRoundRect(Color(0xFFD9342B), tl + Offset(u * 0.12f, u * 0.14f), Size(u * 0.26f, u * 0.2f), CornerRadius(u * 0.05f))
    }
}

private fun DrawScope.drawSmoke(u: Float, cycle: Float) {
    for (i in 0 until 5) {
        val ph = (cycle * 2f + i / 5f) % 1f
        val c = Offset(-u * 3.5f - ph * u * 1.7f - sin(ph * 6f + i) * u * 0.2f, u * 2.5f - ph * u * 2.6f)
        val r = u * (0.32f + ph * 0.75f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFB9B9C2).copy(alpha = 0.75f * (1f - ph)), Color(0xFF6E6E78).copy(alpha = 0f)), center = c, radius = r),
            r, c,
        )
    }
}

private fun DrawScope.drawBackFlames(u: Float, cycle: Float) {
    val xs = listOf(-2.6f, -1.5f, -0.4f, 0.7f, 1.8f, 2.7f)
    xs.forEachIndexed { i, x ->
        val flick = 1f + 0.22f * sin((cycle * 6f + i * 0.37f) * TAU)
        val h = (if (i % 2 == 0) 1.15f else 0.85f) * flick
        drawFlame(Offset(x * u, -u * (2.5f - 0.5f * abs(x) / 2.7f)), u * 0.95f, h, alpha = 0.9f)
    }
}

private fun DrawScope.drawZzz(u: Float, cycle: Float) {
    for (i in 0 until 3) {
        val ph = (cycle * 1.5f + i / 3f) % 1f
        val k = u * (0.22f + ph * 0.3f)
        val o = Offset(u * 2.6f + ph * u * 1.6f, -u * 2.6f - ph * u * 1.7f)
        val z = Path().apply {
            moveTo(o.x - k, o.y - k); lineTo(o.x + k, o.y - k); lineTo(o.x - k, o.y + k); lineTo(o.x + k, o.y + k)
        }
        drawPath(z, Color.White.copy(alpha = 0.9f * (1f - ph)), style = Stroke(u * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.95f)
        cubicTo(c.x - r * 1.7f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.45f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.6f, c.y - r * 1.45f, c.x + r * 1.7f, c.y - r * 0.2f, c.x, c.y + r * 0.95f)
        close()
    }
    drawPath(p, color)
    drawCircle(Color.White.copy(alpha = 0.65f), r * 0.16f, c + Offset(-r * 0.45f, -r * 0.4f))
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r); quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawFlame(c: Offset, r: Float, h: Float = 1f, alpha: Float = 1f) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.7f)
        cubicTo(c.x - r * 1.05f, c.y + r * 0.6f, c.x - r * 0.75f, c.y - r * 0.4f * h, c.x - r * 0.1f, c.y - r * 1.35f * h)
        cubicTo(c.x, c.y - r * 0.55f * h, c.x + r * 0.5f, c.y - r * 0.9f * h, c.x + r * 0.42f, c.y - r * 0.3f * h)
        cubicTo(c.x + r * 0.95f, c.y, c.x + r * 0.75f, c.y + r * 0.7f, c.x, c.y + r * 0.7f)
        close()
    }
    drawPath(
        p,
        Brush.verticalGradient(
            listOf(Color(0xFFFFD23F).copy(alpha = alpha), Color(0xFFFF7A1F).copy(alpha = alpha), Color(0xFFE5342B).copy(alpha = alpha)),
            startY = c.y - r * 1.35f * h, endY = c.y + r * 0.7f,
        ),
    )
    drawOval(Color(0xFFFFF3B0).copy(alpha = alpha), Offset(c.x - r * 0.28f, c.y - r * 0.05f), Size(r * 0.56f, r * 0.62f))
}

private fun DrawScope.drawSpiral(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0..48) {
        val a = i / 48f * 5f * PI.toFloat()
        val rr = r * i / 48f
        val pt = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    drawPath(p, color, style = Stroke(r * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ───────────────────────────── 알 ─────────────────────────────

/** 아바타가 태어나기 전의 알. 점수에 따라 색이 바뀌고, 부화가 가까우면 금이 가고 흔들린다. */
@Composable
fun EggAvatar(score: Double?, progress: Float, modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "egg")
    val wobble by anim.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wobble",
    )
    val cycle by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "cycle")
    Canvas(modifier) {
        val u = minOf(size.width / 9f, size.height / 8f)
        val base = AvatarColors.egg(score)
        val light = lerp(base, Color.White, 0.6f)
        val dark = lerp(base, Color(0xFF5A3550), 0.45f)
        val line = lerp(dark, Color.Black, 0.4f)
        translate(size.width / 2f, size.height / 2f) {
            // 뒤 광채
            drawCircle(
                Brush.radialGradient(listOf(light.copy(alpha = 0.3f), light.copy(alpha = 0f)), center = Offset.Zero, radius = u * 4.4f),
                u * 4.4f, Offset.Zero,
            )
            // 그림자
            val sc = Offset(0f, u * 2.95f)
            scale(1f, 0.2f, pivot = sc) {
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), center = sc, radius = u * 2.6f), u * 2.6f, sc)
            }
            val amp = if (progress > 0.7f) 5f else 1.5f
            rotate(wobble * amp, Offset(0f, u * 2.8f)) {
                val egg = Path().apply {
                    moveTo(0f, -u * 2.95f)
                    cubicTo(u * 1.45f, -u * 2.95f, u * 2.25f, -u * 0.6f, u * 2.25f, u * 0.65f)
                    cubicTo(u * 2.25f, u * 2.05f, u * 1.3f, u * 2.85f, 0f, u * 2.85f)
                    cubicTo(-u * 1.3f, u * 2.85f, -u * 2.25f, u * 2.05f, -u * 2.25f, u * 0.65f)
                    cubicTo(-u * 2.25f, -u * 0.6f, -u * 1.45f, -u * 2.95f, 0f, -u * 2.95f)
                    close()
                }
                val hl = Offset(-u * 0.7f, -u * 1.2f)
                drawPath(egg, Brush.radialGradient(listOf(light, base, dark), center = hl, radius = u * 4.3f))
                clipPath(egg) {
                    // 무늬
                    val spots = listOf(
                        Triple(-1.2f, 0.9f, 0.55f), Triple(1.1f, -0.3f, 0.48f), Triple(0.2f, 1.9f, 0.6f),
                        Triple(-0.3f, -1.9f, 0.36f), Triple(1.7f, 1.4f, 0.4f), Triple(-2.0f, -0.4f, 0.42f),
                    )
                    spots.forEach { (x, y, r) ->
                        drawOval(Color.White.copy(alpha = 0.38f), Offset((x - r) * u, (y - r * 0.8f) * u), Size(r * 2f * u, r * 1.6f * u))
                    }
                    // 아래쪽 음영
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, dark.copy(alpha = 0.45f)), startY = u * 0.8f, endY = u * 2.9f),
                        Offset(-u * 2.3f, u * 0.8f), Size(u * 4.6f, u * 2.2f),
                    )
                }
                // 윤기
                rotate(-24f, hl) {
                    drawOval(Color.White.copy(alpha = 0.75f), hl - Offset(u * 0.28f, u * 0.6f), Size(u * 0.56f, u * 1.1f))
                }
                drawCircle(Color.White.copy(alpha = 0.7f), u * 0.13f, Offset(-u * 0.2f, -u * 2.2f))
                drawPath(egg, line, style = Stroke(u * 0.085f))
                if (progress > 0.5f) {
                    val crack = Path().apply {
                        moveTo(-u * 2.1f, -u * 0.1f); lineTo(-u * 1.45f, u * 0.25f); lineTo(-u * 0.95f, -u * 0.35f)
                        lineTo(-u * 0.35f, u * 0.2f); lineTo(u * 0.3f, -u * 0.3f)
                        if (progress > 0.8f) {
                            lineTo(u * 0.9f, u * 0.25f); lineTo(u * 1.5f, -u * 0.25f); lineTo(u * 2.15f, u * 0.1f)
                        }
                    }
                    drawPath(crack, line, style = Stroke(u * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(crack, Color.White.copy(alpha = 0.6f), style = Stroke(u * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (progress > 0.7f) { // 곧 태어날 것 같은 반짝임
                listOf(Offset(-3.1f, -2.2f), Offset(3.0f, -1.6f), Offset(2.5f, 2.2f)).forEachIndexed { i, o ->
                    val a = (sin((cycle + i / 3f) * TAU) + 1f) / 2f
                    drawSparkle(Offset(o.x * u, o.y * u), u * 0.32f * (0.5f + a / 2f), Color(0xFFFFE892).copy(alpha = 0.3f + 0.7f * a))
                }
            }
        }
    }
}
