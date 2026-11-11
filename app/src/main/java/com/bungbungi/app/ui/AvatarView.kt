package com.bungbungi.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.bungbungi.app.logic.AvatarPart
import com.bungbungi.app.logic.Stage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 아바타의 순간 기분 (주행 중 위반 직후 등) */
enum class Mood { CALM, HAPPY, DIZZY, SLEEPY }

object AvatarColors {
    fun body(stage: Stage): Color = when (stage) {
        Stage.ANGEL -> Color(0xFFFFC8E6)
        Stage.GENTLE -> Color(0xFF8FE3B0)
        Stage.NORMAL -> Color(0xFF8DB7E8)
        Stage.GRUMPY -> Color(0xFF9A8F86)
        Stage.WILD -> Color(0xFF5B2B2B)
    }

    /** 점수(0~100) → 알 색: 낮으면 회색빛 붉은색, 높으면 파스텔 */
    fun egg(score: Double?): Color {
        val s = ((score ?: 70.0) / 100.0).toFloat().coerceIn(0f, 1f)
        return Color(red = 0.55f + 0.45f * s, green = 0.35f + 0.55f * s, blue = 0.40f + 0.50f * s)
    }
}

/** 차 모양 아바타. 단계와 부위(주된 위반)에 따라 얼굴·장식이 바뀐다. */
@Composable
fun CarAvatar(stage: Stage, part: AvatarPart, mood: Mood, modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "avatar")
    val bob by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
    val spin by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = minOf(w / 10f, h / 8f) // 기준 단위
        val shake = if (mood == Mood.DIZZY) sin(spin / 10f) * u * 0.08f else 0f
        translate(w / 2f + shake, h / 2f + (bob - 0.5f) * u * 0.25f) {
            drawCar(u, stage, part, mood, spin)
        }
    }
}

private fun DrawScope.drawCar(u: Float, stage: Stage, part: AvatarPart, mood: Mood, spin: Float) {
    val body = AvatarColors.body(stage)
    val dark = Color(0xFF222222)
    val outline = Stroke(width = u * 0.08f)

    // 날개 (천사)
    if (stage == Stage.ANGEL) {
        listOf(-1f, 1f).forEach { side ->
            val p = Path().apply {
                moveTo(side * u * 3.2f, -u * 0.6f)
                cubicTo(side * u * 5.2f, -u * 2.6f, side * u * 5.4f, -u * 0.2f, side * u * 4.6f, u * 0.6f)
                cubicTo(side * u * 4.2f, u * 0.2f, side * u * 3.6f, u * 0.4f, side * u * 3.2f, u * 0.4f)
                close()
            }
            drawPath(p, Color.White)
            drawPath(p, Color(0xFFE6D6FF), style = Stroke(u * 0.06f))
        }
        // 링
        drawOval(Color(0xFFFFE27A), Offset(-u * 1.2f, -u * 3.9f), Size(u * 2.4f, u * 0.6f), style = Stroke(u * 0.18f))
    }

    // 배기 연기 (급가속 습관 / 난폭)
    if (part == AvatarPart.EXHAUST_SMOKE || stage == Stage.WILD) {
        for (i in 0 until 3) {
            val phase = ((spin / 360f) + i / 3f) % 1f
            drawCircle(Color.Gray.copy(alpha = 0.55f * (1f - phase)), u * (0.4f + phase * 0.6f), Offset(-u * 4.2f - phase * u * 1.5f, u * 0.9f - phase * u * 1.2f))
        }
    }

    // 지붕 + 창
    val roof = Path().apply {
        moveTo(-u * 2.2f, -u * 0.6f)
        cubicTo(-u * 1.8f, -u * 2.8f, u * 1.8f, -u * 2.8f, u * 2.2f, -u * 0.6f)
        close()
    }
    drawPath(roof, body)
    drawPath(roof, dark, style = outline)

    // 몸통
    drawRoundRect(body, Offset(-u * 3.6f, -u * 0.8f), Size(u * 7.2f, u * 2.2f), CornerRadius(u * 0.9f))
    drawRoundRect(dark, Offset(-u * 3.6f, -u * 0.8f), Size(u * 7.2f, u * 2.2f), CornerRadius(u * 0.9f), style = outline)

    // 앞유리 = 얼굴 영역
    val glass = Color(0xFFEAF6FF)
    drawRoundRect(glass, Offset(-u * 1.8f, -u * 2.1f), Size(u * 3.6f, u * 1.4f), CornerRadius(u * 0.6f))

    drawFace(u, stage, mood)

    // 헤드라이트
    listOf(-1f, 1f).forEach { side ->
        val c = Offset(side * u * 2.6f, u * 0.1f)
        if (stage == Stage.ANGEL) drawHeart(c, u * 0.42f, Color(0xFFFF5C9A))
        else drawCircle(if (stage == Stage.WILD) Color(0xFFFF3B1F) else Color(0xFFFFF4B0), u * 0.38f, c)
    }

    // 범퍼
    if (part == AvatarPart.DENTED_BUMPER || stage == Stage.WILD) {
        val dent = Path().apply {
            moveTo(-u * 3.2f, u * 1.15f); lineTo(-u * 1.6f, u * 1.15f); lineTo(-u * 1.1f, u * 0.85f)
            lineTo(-u * 0.5f, u * 1.2f); lineTo(u * 3.2f, u * 1.15f)
        }
        drawPath(dent, Color(0xFF555555), style = Stroke(u * 0.22f, cap = StrokeCap.Round))
    } else {
        drawLine(Color(0xFF666666), Offset(-u * 3.2f, u * 1.15f), Offset(u * 3.2f, u * 1.15f), u * 0.22f, StrokeCap.Round)
    }

    // 옆면 흠집
    if (part == AvatarPart.SIDE_SCRATCH || stage == Stage.GRUMPY || stage == Stage.WILD) {
        val n = if (part == AvatarPart.SIDE_SCRATCH) 4 else 2
        for (i in 0 until n) {
            val x = u * (1.0f + i * 0.45f)
            drawLine(Color(0xFF3A3A3A), Offset(x, -u * 0.5f), Offset(x + u * 0.5f, u * 0.2f), u * 0.07f)
        }
    }

    // 안전벨트 (미착용 습관이면 풀린 벨트)
    if (part == AvatarPart.LOOSE_BELT) {
        drawLine(Color(0xFF444444), Offset(-u * 1.6f, -u * 0.6f), Offset(-u * 0.6f, u * 1.0f), u * 0.18f, StrokeCap.Round)
        drawLine(Color(0xFF444444), Offset(u * 0.2f, u * 0.4f), Offset(u * 1.0f, u * 1.0f), u * 0.18f, StrokeCap.Round)
    }

    // 바퀴
    listOf(-1f, 1f).forEach { side ->
        val c = Offset(side * u * 2.3f, u * 1.55f)
        drawCircle(dark, u * 0.8f, c)
        drawCircle(Color(0xFFBBBBBB), u * 0.35f, c)
        rotate(spin, c) { drawLine(dark, c - Offset(u * 0.3f, 0f), c + Offset(u * 0.3f, 0f), u * 0.1f) }
    }

    // 반짝이 (천사)
    if (stage == Stage.ANGEL) {
        listOf(Offset(-u * 3.6f, -u * 2.4f), Offset(u * 3.8f, -u * 2.0f), Offset(u * 0.2f, -u * 3.2f)).forEachIndexed { i, p ->
            val a = (sin((spin / 360f + i / 3f) * 2 * PI).toFloat() + 1f) / 2f
            drawSparkle(p, u * 0.35f * (0.5f + a / 2f), Color(0xFFFFE27A).copy(alpha = 0.4f + 0.6f * a))
        }
    }
}

private fun DrawScope.drawFace(u: Float, stage: Stage, mood: Mood) {
    val ink = Color(0xFF1E1E1E)
    val eyeY = -u * 1.45f
    val eyes = listOf(Offset(-u * 0.75f, eyeY), Offset(u * 0.75f, eyeY))
    when {
        mood == Mood.DIZZY -> eyes.forEach { c -> drawSpiral(c, u * 0.28f, ink) }
        mood == Mood.SLEEPY -> eyes.forEach { c -> drawLine(ink, c - Offset(u * 0.25f, 0f), c + Offset(u * 0.25f, 0f), u * 0.08f, StrokeCap.Round) }
        stage == Stage.ANGEL -> eyes.forEach { c ->
            drawCircle(ink, u * 0.28f, c); drawCircle(Color.White, u * 0.1f, c + Offset(-u * 0.08f, -u * 0.08f))
            drawCircle(Color.White, u * 0.05f, c + Offset(u * 0.1f, u * 0.08f))
        }
        stage == Stage.GENTLE -> eyes.forEach { c ->
            drawArc(ink, 200f, 140f, false, c - Offset(u * 0.28f, u * 0.1f), Size(u * 0.56f, u * 0.5f), style = Stroke(u * 0.1f, cap = StrokeCap.Round))
        }
        stage == Stage.NORMAL -> eyes.forEach { c -> drawCircle(ink, u * 0.16f, c) }
        stage == Stage.GRUMPY -> eyes.forEachIndexed { i, c ->
            drawCircle(ink, u * 0.15f, c + Offset(0f, u * 0.05f))
            val s = if (i == 0) 1f else -1f
            drawLine(ink, c + Offset(-u * 0.3f * s, -u * 0.32f), c + Offset(u * 0.25f * s, -u * 0.15f), u * 0.09f, StrokeCap.Round)
        }
        else -> eyes.forEachIndexed { i, c -> // 난폭: 불꽃 눈
            drawFlame(c, u * 0.38f)
            val s = if (i == 0) 1f else -1f
            drawLine(ink, c + Offset(-u * 0.35f * s, -u * 0.4f), c + Offset(u * 0.3f * s, -u * 0.12f), u * 0.12f, StrokeCap.Round)
        }
    }
    // 입
    val mouthY = -u * 0.95f
    when {
        mood == Mood.DIZZY -> drawWavy(Offset(-u * 0.4f, mouthY), u * 0.8f, ink, u)
        mood == Mood.HAPPY || stage == Stage.ANGEL || stage == Stage.GENTLE ->
            drawArc(ink, 20f, 140f, false, Offset(-u * 0.4f, mouthY - u * 0.3f), Size(u * 0.8f, u * 0.5f), style = Stroke(u * 0.09f, cap = StrokeCap.Round))
        stage == Stage.NORMAL -> drawLine(ink, Offset(-u * 0.3f, mouthY), Offset(u * 0.3f, mouthY), u * 0.08f, StrokeCap.Round)
        stage == Stage.GRUMPY ->
            drawArc(ink, 200f, 140f, false, Offset(-u * 0.35f, mouthY - u * 0.05f), Size(u * 0.7f, u * 0.4f), style = Stroke(u * 0.08f, cap = StrokeCap.Round))
        else -> { // 이빨
            drawRect(ink, Offset(-u * 0.5f, mouthY - u * 0.15f), Size(u * 1.0f, u * 0.35f))
            for (i in 0 until 4) {
                val x = -u * 0.5f + i * u * 0.25f
                drawPath(Path().apply { moveTo(x, mouthY - u * 0.15f); lineTo(x + u * 0.125f, mouthY + u * 0.08f); lineTo(x + u * 0.25f, mouthY - u * 0.15f); close() }, Color.White)
            }
        }
    }
    if (stage == Stage.ANGEL || stage == Stage.GENTLE) {
        drawCircle(Color(0xFFFF9EC4).copy(alpha = 0.6f), u * 0.18f, Offset(-u * 1.3f, -u * 1.05f))
        drawCircle(Color(0xFFFF9EC4).copy(alpha = 0.6f), u * 0.18f, Offset(u * 1.3f, -u * 1.05f))
    }
}

private fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.4f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.6f, c.y - r * 1.4f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.25f, c.y - r * 0.25f); lineTo(c.x + r, c.y)
        lineTo(c.x + r * 0.25f, c.y + r * 0.25f); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.25f, c.y + r * 0.25f)
        lineTo(c.x - r, c.y); lineTo(c.x - r * 0.25f, c.y - r * 0.25f); close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawFlame(c: Offset, r: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.7f)
        cubicTo(c.x - r, c.y + r * 0.6f, c.x - r * 0.7f, c.y - r * 0.4f, c.x - r * 0.1f, c.y - r * 1.2f)
        cubicTo(c.x, c.y - r * 0.5f, c.x + r * 0.5f, c.y - r * 0.8f, c.x + r * 0.4f, c.y - r * 0.3f)
        cubicTo(c.x + r * 0.9f, c.y, c.x + r * 0.7f, c.y + r * 0.7f, c.x, c.y + r * 0.7f)
        close()
    }
    drawPath(p, Color(0xFFFF5A1F))
    drawCircle(Color(0xFFFFD23F), r * 0.3f, c + Offset(0f, r * 0.2f))
}

private fun DrawScope.drawSpiral(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0..40) {
        val a = i / 40f * 4 * PI.toFloat()
        val rr = r * i / 40f
        val pt = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    drawPath(p, color, style = Stroke(r * 0.18f))
}

private fun DrawScope.drawWavy(start: Offset, width: Float, color: Color, u: Float) {
    val p = Path().apply {
        moveTo(start.x, start.y)
        var x = start.x
        var up = true
        while (x < start.x + width) {
            quadraticTo(x + width / 8f, start.y + if (up) -u * 0.12f else u * 0.12f, x + width / 4f, start.y)
            x += width / 4f; up = !up
        }
    }
    drawPath(p, color, style = Stroke(u * 0.08f, cap = StrokeCap.Round))
}

/** 아바타가 태어나기 전의 알. 점수에 따라 색이 바뀌고, 부화가 가까우면 금이 간다. */
@Composable
fun EggAvatar(score: Double?, progress: Float, modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "egg")
    val wobble by anim.animateFloat(-4f, 4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "wobble")
    Canvas(modifier) {
        val u = minOf(size.width / 10f, size.height / 8f)
        val c = Offset(size.width / 2f, size.height / 2f)
        rotate(if (progress > 0.7f) wobble else wobble / 4f, c) {
            val eggSize = Size(u * 4.4f, u * 5.6f)
            val tl = c - Offset(eggSize.width / 2f, eggSize.height / 2f)
            drawOval(AvatarColors.egg(score), tl, eggSize)
            // 무늬
            listOf(Offset(-0.8f, -1.2f), Offset(0.9f, -0.2f), Offset(-0.4f, 1.2f)).forEach {
                drawCircle(Color.White.copy(alpha = 0.35f), u * 0.45f, c + Offset(it.x * u, it.y * u))
            }
            drawOval(Color(0xFF333333), tl, eggSize, style = Stroke(u * 0.08f))
            if (progress > 0.5f) {
                val crack = Path().apply {
                    moveTo(c.x - u * 1.6f, c.y - u * 0.4f); lineTo(c.x - u * 0.9f, c.y - u * 0.1f); lineTo(c.x - u * 0.4f, c.y - u * 0.6f)
                    lineTo(c.x + u * 0.2f, c.y - u * 0.1f); lineTo(c.x + u * 0.8f, c.y - u * 0.5f)
                    if (progress > 0.8f) { lineTo(c.x + u * 1.3f, c.y - u * 0.1f); lineTo(c.x + u * 1.9f, c.y - u * 0.4f) }
                }
                drawPath(crack, Color(0xFF333333), style = Stroke(u * 0.08f))
            }
        }
    }
}
