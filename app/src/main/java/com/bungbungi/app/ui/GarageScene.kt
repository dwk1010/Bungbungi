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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.bungbungi.app.logic.AvatarPart
import com.bungbungi.app.logic.CarModel
import com.bungbungi.app.logic.Stage
import kotlin.math.PI
import kotlin.math.sin

/*
 * 차고 장면: 왼쪽에 운전자 캐릭터, 오른쪽에 내 차(3/4 앞모습), 뒤에 차고.
 * 점수 단계가 내려갈수록 차고가 어두워지고, 차가 더러워지고 망가지고, 운전자 표정이 굳어진다.
 * 장면 좌표: 가로 16 × 세로 12 단위(u). 바닥선은 y = 10.4.
 */

private const val TAU2 = (2.0 * PI).toFloat()

private class Env(
    val wallTop: Color, val wallBottom: Color, val floorNear: Color, val floorFar: Color,
    val light: Color, val lightAlpha: Float, val vignette: Float,
)

private fun envOf(stage: Stage) = when (stage) {
    Stage.ANGEL -> Env(Color(0xFFF6E7D2), Color(0xFFE9D3B6), Color(0xFFD9C3A6), Color(0xFFEBDCC6), Color(0xFFFFD9A0), 0.55f, 0.0f)
    Stage.GENTLE -> Env(Color(0xFFF3F5F0), Color(0xFFE4E9E1), Color(0xFFD9DED6), Color(0xFFEAEEE7), Color(0xFFFFF4DA), 0.35f, 0.0f)
    Stage.NORMAL -> Env(Color(0xFFF4F4F2), Color(0xFFE6E6E3), Color(0xFFDCDCD8), Color(0xFFEDEDEA), Color(0xFFFFFFFF), 0.25f, 0.0f)
    Stage.GRUMPY -> Env(Color(0xFFBDB8AE), Color(0xFFA29C90), Color(0xFF8E887C), Color(0xFFA8A296), Color(0xFFE8D9A8), 0.12f, 0.25f)
    Stage.WILD -> Env(Color(0xFF3C2C2C), Color(0xFF2A1E1E), Color(0xFF2C2222), Color(0xFF3A2C2C), Color(0xFFFF4A2A), 0.35f, 0.45f)
}

/** 차체색: 단계에 따라 광택과 때가 달라진다 */
private class Paint(val top: Color, val mid: Color, val bottom: Color, val front: Color, val line: Color)

private fun paintOf(stage: Stage, model: CarModel): Paint {
    val base = when (model) {
        CarModel.AVANTE -> Color(0xFFE9E9E6)   // 화이트
        CarModel.SONATA -> Color(0xFFC9CCD0)   // 실버
        CarModel.TUCSON -> Color(0xFFC8BCA9)   // 베이지
        CarModel.SANTAFE -> Color(0xFF9DA79A)  // 올리브 그레이
    }
    val dirt = when (stage) {
        Stage.ANGEL, Stage.GENTLE, Stage.NORMAL -> 0f
        Stage.GRUMPY -> 0.28f
        Stage.WILD -> 0.5f
    }
    val b = lerp(base, Color(0xFF7A6A55), dirt)
    val shine = if (stage == Stage.ANGEL) 0.18f else 0f
    return Paint(
        top = lerp(lerp(b, Color.White, 0.45f), Color(0xFFFFE3F0), shine),
        mid = b,
        bottom = lerp(b, Color(0xFF3A3A40), 0.38f),
        front = lerp(b, Color.White, 0.18f),
        line = lerp(b, Color(0xFF1E1E24), 0.7f),
    )
}

@Composable
fun GarageScene(stage: Stage, part: AvatarPart, mood: Mood, modifier: Modifier = Modifier, model: CarModel = CarModel.AVANTE) {
    val anim = rememberInfiniteTransition(label = "garage")
    val breath by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val cycle by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "cycle")
    Canvas(modifier) {
        val u = minOf(size.width / 16f, size.height / 12f)
        val ox = (size.width - u * 16f) / 2f
        val oy = (size.height - u * 12f) / 2f
        clipRect(ox, oy, ox + u * 16f, oy + u * 12f) {
            translate(ox, oy) {
                drawRoom(u, stage, cycle)
                drawCar3q(u, stage, part, model, cycle)
                drawPerson(u, stage, part, mood, breath, cycle)
                drawForeground(u, stage, part, cycle)
            }
        }
    }
}

// ───────────────────────────── 차고 ─────────────────────────────

private fun DrawScope.drawRoom(u: Float, stage: Stage, cycle: Float) {
    val e = envOf(stage)
    val floorY = u * 8.2f
    // 벽
    drawRect(Brush.verticalGradient(listOf(e.wallTop, e.wallBottom), startY = 0f, endY = floorY), Offset.Zero, Size(u * 16, floorY))
    // 천장 조명 줄
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f * (if (stage == Stage.WILD) 0.2f else 1f)), Color.Transparent), startY = 0f, endY = u * 1.2f), Offset.Zero, Size(u * 16, u * 1.2f))
    // 바닥 (멀리 밝고 가까이 어둡게) + 벽과 만나는 곳은 둥글게
    drawRect(Brush.verticalGradient(listOf(e.floorFar, e.floorNear), startY = floorY, endY = u * 12f), Offset(0f, floorY), Size(u * 16, u * 12f - floorY))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Transparent), startY = floorY, endY = floorY + u * 0.6f), Offset(0f, floorY), Size(u * 16, u * 0.6f))

    when (stage) {
        Stage.ANGEL -> {
            // 따뜻한 작업실: 창, 선반과 화분, 액자, 펜던트 조명, 트로피
            drawWindow(u, Offset(u * 0.3f, u * 0.9f), Size(u * 2.6f, u * 6.6f), warm = true)
            drawShelf(u, Offset(u * 3.6f, u * 2.5f), u * 4.2f)
            drawPlant(u, Offset(u * 4.4f, u * 2.5f), 0.9f)
            drawTrophy(u, Offset(u * 6.6f, u * 2.5f), cycle)
            drawFrame(u, Rect(u * 9.2f, u * 1.3f, u * 11.4f, u * 3.0f), Color(0xFF8BA6B8))
            drawFrame(u, Rect(u * 11.8f, u * 1.6f, u * 13.2f, u * 3.3f), Color(0xFFD9A77A))
            drawPendant(u, Offset(u * 8.0f, 0f), e.light)
            drawPlant(u, Offset(u * 15.3f, u * 8.4f), 1.4f)
        }
        Stage.GENTLE -> {
            drawWindow(u, Offset(u * 0.3f, u * 0.9f), Size(u * 2.6f, u * 6.6f), warm = false)
            drawShelf(u, Offset(u * 9.6f, u * 2.2f), u * 3.4f)
            drawPlant(u, Offset(u * 10.3f, u * 2.2f), 0.8f)
            drawFrame(u, Rect(u * 11.6f, u * 0.9f, u * 12.9f, u * 2.0f), Color(0xFF9CC5A1))
            drawPlant(u, Offset(u * 15.3f, u * 8.4f), 1.2f)
        }
        Stage.NORMAL -> {
            // 시안 Lv.1처럼 비어 있는 밝은 공간
        }
        Stage.GRUMPY -> {
            // 얼룩진 벽, 깜빡이는 형광등, 기름 얼룩, 굴러다니는 타이어
            drawStain(u, Offset(u * 11.5f, u * 3.0f), u * 1.3f, Color(0xFF6E6658))
            drawStain(u, Offset(u * 4.2f, u * 5.6f), u * 0.9f, Color(0xFF6E6658))
            val flicker = if (sin(cycle * TAU2 * 3f) > 0.85f) 0.15f else 0.55f
            drawRoundRect(Color(0xFFF2EBC8).copy(alpha = flicker), Offset(u * 6.5f, u * 0.25f), Size(u * 3.2f, u * 0.2f), CornerRadius(u * 0.1f))
            drawTire(u, Offset(u * 15.0f, u * 9.0f), 0.8f)
        }
        Stage.WILD -> {
            // 어두운 차고, 빨간 경광등, 금 간 벽, 꼬깔콘
            val swing = (sin(cycle * TAU2 * 2f) + 1f) / 2f
            val beam = Offset(u * (4f + swing * 8f), u * 4f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF3B1F).copy(alpha = 0.32f), Color.Transparent), center = beam, radius = u * 5f), u * 5f, beam)
            drawCircle(Color(0xFFFF3B1F), u * 0.28f, Offset(u * 8f, u * 0.5f))
            drawCircle(Color(0xFFFF3B1F).copy(alpha = 0.4f), u * 0.6f, Offset(u * 8f, u * 0.5f))
            val crack = Path().apply {
                moveTo(u * 12.5f, u * 0.6f); lineTo(u * 12.9f, u * 1.7f); lineTo(u * 12.4f, u * 2.4f); lineTo(u * 13.1f, u * 3.6f)
                moveTo(u * 12.9f, u * 1.7f); lineTo(u * 13.6f, u * 2.0f)
            }
            drawPath(crack, Color(0xFF120A0A), style = Stroke(u * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCone(u, Offset(u * 15.2f, u * 10.6f))
            drawCone(u, Offset(u * 0.6f, u * 10.9f))
        }
    }
    // 바닥 그림자 (차·사람)
    val sc = Offset(u * 10.2f, u * 10.45f)
    scale(1f, 0.16f, pivot = sc) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center = sc, radius = u * 5.6f), u * 5.6f, sc)
    }
    val pc = Offset(u * 2.9f, u * 10.55f)
    scale(1f, 0.2f, pivot = pc) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent), center = pc, radius = u * 1.7f), u * 1.7f, pc)
    }
    // 기름 웅덩이
    if (stage == Stage.GRUMPY || stage == Stage.WILD) {
        val c = Offset(u * 12.6f, u * 11.1f)
        scale(1f, 0.28f, pivot = c) { drawCircle(Color(0xFF15120E).copy(alpha = 0.55f), u * 1.3f, c) }
        scale(1f, 0.28f, pivot = c + Offset(-u * 0.3f, -u * 0.1f)) { drawCircle(Color.White.copy(alpha = 0.12f), u * 0.4f, c + Offset(-u * 0.3f, -u * 0.1f)) }
    }
}

private fun DrawScope.drawWindow(u: Float, tl: Offset, sz: Size, warm: Boolean) {
    val sky = if (warm) listOf(Color(0xFFFFD9A8), Color(0xFFB9C98E)) else listOf(Color(0xFFD7ECF7), Color(0xFFBFD8B0))
    drawRect(Brush.verticalGradient(sky, startY = tl.y, endY = tl.y + sz.height), tl, sz)
    // 나무
    drawCircle(Color(0xFF8FA66A).copy(alpha = 0.8f), sz.width * 0.35f, tl + Offset(sz.width * 0.3f, sz.height * 0.55f))
    drawCircle(Color(0xFFA3B97A).copy(alpha = 0.8f), sz.width * 0.3f, tl + Offset(sz.width * 0.75f, sz.height * 0.4f))
    val frame = Color(0xFF2E2A27)
    drawRect(frame, tl, sz, style = Stroke(u * 0.14f))
    drawLine(frame, tl + Offset(sz.width / 2f, 0f), tl + Offset(sz.width / 2f, sz.height), u * 0.08f)
    for (i in 1..3) drawLine(frame, tl + Offset(0f, sz.height * i / 4f), tl + Offset(sz.width, sz.height * i / 4f), u * 0.06f)
}

private fun DrawScope.drawShelf(u: Float, left: Offset, w: Float) {
    drawRoundRect(Color(0xFFA07850), left, Size(w, u * 0.18f), CornerRadius(u * 0.05f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFD9A0).copy(alpha = 0.35f), Color.Transparent), startY = left.y + u * 0.18f, endY = left.y + u * 0.8f), left + Offset(0f, u * 0.18f), Size(w, u * 0.6f))
}

private fun DrawScope.drawPlant(u: Float, base: Offset, k: Float) {
    val pot = Path().apply {
        moveTo(base.x - u * 0.38f * k, base.y - u * 0.6f * k); lineTo(base.x + u * 0.38f * k, base.y - u * 0.6f * k)
        lineTo(base.x + u * 0.28f * k, base.y); lineTo(base.x - u * 0.28f * k, base.y); close()
    }
    listOf(-50f, -20f, 10f, 40f, -75f, 70f).forEachIndexed { i, deg ->
        val c = base + Offset(0f, -u * 0.6f * k)
        rotate(deg, c) {
            drawOval(if (i % 2 == 0) Color(0xFF6E8A6A) else Color(0xFF86A47F), c + Offset(-u * 0.13f * k, -u * 1.0f * k), Size(u * 0.26f * k, u * 0.95f * k))
        }
    }
    drawPath(pot, Color(0xFFD9CFC2))
    drawPath(pot, Color(0xFFA89C8C), style = Stroke(u * 0.03f))
}

private fun DrawScope.drawFrame(u: Float, r: Rect, art: Color) {
    drawRect(Color(0xFF2B2E31), r.topLeft, r.size)
    val inner = Rect(r.left + u * 0.1f, r.top + u * 0.1f, r.right - u * 0.1f, r.bottom - u * 0.1f)
    drawRect(Brush.verticalGradient(listOf(art.copy(alpha = 0.7f), art), startY = inner.top, endY = inner.bottom), inner.topLeft, inner.size)
    val road = Path().apply {
        moveTo(inner.left + inner.width * 0.45f, inner.top + inner.height * 0.5f); lineTo(inner.left + inner.width * 0.55f, inner.top + inner.height * 0.5f)
        lineTo(inner.right - inner.width * 0.1f, inner.bottom); lineTo(inner.left + inner.width * 0.1f, inner.bottom); close()
    }
    drawPath(road, Color(0xFF4A4A4A).copy(alpha = 0.7f))
}

private fun DrawScope.drawPendant(u: Float, top: Offset, light: Color) {
    val c = top + Offset(0f, u * 1.6f)
    drawLine(Color(0xFF2B2E31), top, c, u * 0.04f)
    drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.5f), Color.Transparent), center = c, radius = u * 2.4f), u * 2.4f, c)
    drawRoundRect(Color(0xFFFFF0D0), c - Offset(u * 0.28f, u * 0.1f), Size(u * 0.56f, u * 0.7f), CornerRadius(u * 0.25f))
}

private fun DrawScope.drawTrophy(u: Float, base: Offset, cycle: Float) {
    val gold = Brush.verticalGradient(listOf(Color(0xFFFFE9A3), Color(0xFFD9A93A)), startY = base.y - u * 1.1f, endY = base.y)
    drawRoundRect(Color(0xFF6B4A2B), base - Offset(u * 0.3f, u * 0.22f), Size(u * 0.6f, u * 0.22f), CornerRadius(u * 0.04f))
    drawRect(gold, base - Offset(u * 0.06f, u * 0.5f), Size(u * 0.12f, u * 0.3f))
    val cup = Path().apply {
        moveTo(base.x - u * 0.38f, base.y - u * 1.1f); lineTo(base.x + u * 0.38f, base.y - u * 1.1f)
        cubicTo(base.x + u * 0.36f, base.y - u * 0.55f, base.x - u * 0.36f, base.y - u * 0.55f, base.x - u * 0.38f, base.y - u * 1.1f); close()
    }
    drawPath(cup, gold)
    val a = (sin(cycle * TAU2 * 2f) + 1f) / 2f
    drawSparkle4(base + Offset(u * 0.3f, -u * 1.2f), u * 0.18f * (0.6f + 0.4f * a), Color.White.copy(alpha = 0.5f + 0.5f * a))
}

private fun DrawScope.drawStain(u: Float, c: Offset, r: Float, col: Color) {
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r), r, c)
    drawCircle(col.copy(alpha = 0.25f), r * 0.3f, c + Offset(r * 0.3f, r * 0.2f))
}

private fun DrawScope.drawTire(u: Float, c: Offset, k: Float) {
    drawOval(Color(0xFF26262B), c - Offset(u * 0.55f * k, u * 0.75f * k), Size(u * 1.1f * k, u * 1.5f * k))
    drawOval(Color(0xFF55555C), c - Offset(u * 0.25f * k, u * 0.36f * k), Size(u * 0.5f * k, u * 0.72f * k))
}

private fun DrawScope.drawCone(u: Float, base: Offset) {
    val cone = Path().apply {
        moveTo(base.x - u * 0.45f, base.y); lineTo(base.x - u * 0.1f, base.y - u * 1.2f)
        lineTo(base.x + u * 0.1f, base.y - u * 1.2f); lineTo(base.x + u * 0.45f, base.y); close()
    }
    drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFF8A3D), Color(0xFFD9541A)), startY = base.y - u * 1.2f, endY = base.y))
    drawLine(Color.White.copy(alpha = 0.85f), base + Offset(-u * 0.26f, -u * 0.55f), base + Offset(u * 0.26f, -u * 0.55f), u * 0.14f)
    drawRoundRect(Color(0xFF3A2A22), base - Offset(u * 0.55f, u * 0.08f), Size(u * 1.1f, u * 0.14f), CornerRadius(u * 0.04f))
}

// ───────────────────────────── 차 (3/4 앞모습) ─────────────────────────────

/** 차체 기본 좌표 (세단 기준, 단위 u). SUV는 지붕·벨트라인을 올리고 바퀴를 키운다. */
private class CarGeo(suv: Boolean) {
    val lift = if (suv) 0.55f else 0f
    val wheelR = if (suv) 1.08f else 0.98f
    val belt = 7.1f - (if (suv) 0.35f else 0f)     // 옆면 어깨선
    val rocker = 9.3f                               // 옆면 아래
    val roof = 5.35f - lift
    val sideX0 = 5.0f
    val cornerX = 12.6f                             // 옆면과 앞면이 만나는 모서리
    val frontX1 = 15.3f
    val rearWheel = Offset(6.55f, 9.42f)
    val frontWheel = Offset(11.15f, 9.45f)
    val farWheel = Offset(14.25f, 9.5f)
}

private fun DrawScope.drawCar3q(u: Float, stage: Stage, part: AvatarPart, model: CarModel, cycle: Float) {
    val p = paintOf(stage, model)
    val suv = model == CarModel.TUCSON || model == CarModel.SANTAFE
    val g = CarGeo(suv)
    fun o(x: Float, y: Float) = Offset(u * x, u * y)
    val line = Stroke(u * 0.045f, join = StrokeJoin.Round, cap = StrokeCap.Round)
    val wild = stage == Stage.WILD
    val grumpy = stage == Stage.GRUMPY
    val belt = g.belt

    // 배기 연기 (뒤쪽)
    if (part == AvatarPart.EXHAUST_SMOKE || wild) {
        for (i in 0 until 5) {
            val ph = (cycle * 2f + i / 5f) % 1f
            val c = Offset(u * (5.0f - ph * 1.6f), u * (9.4f - ph * 2.4f))
            val r = u * (0.3f + ph * 0.8f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9A9AA2).copy(alpha = 0.7f * (1f - ph)), Color.Transparent), center = c, radius = r), r, c)
        }
    }

    // 반대편 앞바퀴 (앞범퍼 아래로 살짝)
    drawWheel(u, o(g.farWheel.x + 0.2f, g.farWheel.y), g.wheelR * 0.92f, stage, squash = 0.45f, shaded = true)

    // 옆면
    val side = Path().apply {
        moveTo(u * g.sideX0, u * (belt + 0.45f))
        cubicTo(u * g.sideX0, u * (belt + 0.05f), u * (g.sideX0 + 0.2f), u * belt, u * (g.sideX0 + 0.6f), u * belt)
        lineTo(u * g.cornerX, u * (belt + 0.08f))
        cubicTo(u * (g.cornerX + 0.25f), u * (belt + 0.6f), u * (g.cornerX + 0.3f), u * 8.6f, u * (g.cornerX + 0.2f), u * g.rocker)
        lineTo(u * (g.sideX0 + 0.3f), u * (g.rocker - 0.05f))
        cubicTo(u * g.sideX0, u * (g.rocker - 0.1f), u * (g.sideX0 - 0.05f), u * 8.9f, u * g.sideX0, u * (belt + 0.45f))
        close()
    }
    drawPath(side, Brush.verticalGradient(listOf(p.top, p.mid, p.mid, p.bottom), startY = u * belt, endY = u * g.rocker))
    // 옆면 캐릭터 라인과 반사
    drawLine(Color.White.copy(alpha = 0.55f), o(g.sideX0 + 0.5f, belt + 0.18f), o(g.cornerX - 0.2f, belt + 0.25f), u * 0.07f, StrokeCap.Round)
    drawLine(p.line.copy(alpha = 0.25f), o(g.sideX0 + 0.6f, belt + 1.05f), o(g.cornerX - 0.1f, belt + 1.0f), u * 0.04f, StrokeCap.Round)
    // 휠 아치 (어둡게)
    listOf(g.rearWheel, g.frontWheel).forEach { w ->
        val r = g.wheelR + 0.16f
        drawArc(Color(0xFF17171B), 180f, 180f, true, o(w.x - r, w.y - r), Size(u * r * 2f, u * r * 2f))
    }

    // 앞면 (보는 쪽)
    val frontTop = belt + 0.08f
    val front = Path().apply {
        moveTo(u * g.cornerX, u * frontTop)
        lineTo(u * (g.frontX1 - 0.35f), u * (frontTop + 0.22f))
        cubicTo(u * g.frontX1, u * (frontTop + 0.28f), u * (g.frontX1 + 0.05f), u * (frontTop + 0.7f), u * (g.frontX1 - 0.05f), u * (frontTop + 1.1f))
        lineTo(u * (g.frontX1 - 0.2f), u * (g.rocker - 0.05f))
        lineTo(u * (g.cornerX + 0.2f), u * g.rocker)
        cubicTo(u * (g.cornerX + 0.3f), u * 8.6f, u * (g.cornerX + 0.25f), u * (belt + 0.6f), u * g.cornerX, u * frontTop)
        close()
    }
    drawPath(front, Brush.verticalGradient(listOf(p.front, p.mid, p.bottom), startY = u * frontTop, endY = u * g.rocker))

    // 보닛 (윗면, 가장 밝게)
    val hoodBackL = o(10.25f, belt - 0.5f)
    val hoodBackR = o(12.9f, belt - 0.38f)
    val hood = Path().apply {
        moveTo(hoodBackL.x, hoodBackL.y); lineTo(hoodBackR.x, hoodBackR.y)
        cubicTo(u * 14.2f, u * (belt - 0.3f), u * (g.frontX1 - 0.1f), u * (frontTop + 0.05f), u * (g.frontX1 - 0.35f), u * (frontTop + 0.22f))
        lineTo(u * g.cornerX, u * frontTop)
        lineTo(u * (g.sideX0 + 5.0f), u * belt)
        close()
    }
    drawPath(hood, Brush.linearGradient(listOf(lerp(p.top, Color.White, 0.45f), p.top), start = hoodBackL, end = o(14f, frontTop)))

    // 유리: 옆 창, 앞유리
    val roofRear = o(7.3f, g.roof + 0.15f)
    val roofFrontL = o(9.75f, g.roof)
    val roofFrontR = o(11.35f, g.roof + 0.1f)
    val sideGlass = Path().apply {
        moveTo(u * (g.sideX0 + 0.75f), u * (belt - 0.02f))
        cubicTo(u * (g.sideX0 + 1.3f), u * (g.roof + 1.1f), u * 6.6f, u * (g.roof + 0.3f), roofRear.x, roofRear.y)
        lineTo(roofFrontL.x, roofFrontL.y)
        lineTo(hoodBackL.x, hoodBackL.y)
        lineTo(u * (g.sideX0 + 5.0f), u * belt)
        close()
    }
    drawPath(sideGlass, Brush.linearGradient(listOf(Color(0xFF55606B), Color(0xFF262C33)), start = roofRear, end = o(10f, belt)))
    clipPath(sideGlass) {
        drawLine(Color.White.copy(alpha = 0.22f), o(6.2f, belt), o(7.6f, g.roof), u * 0.45f)
        drawLine(Color.White.copy(alpha = 0.12f), o(7.0f, belt), o(8.3f, g.roof), u * 0.15f)
        // 실내 시트 실루엣
        drawRoundRect(Color(0xFF1B1F24), o(8.6f, belt - 0.95f), Size(u * 0.7f, u * 0.9f), CornerRadius(u * 0.2f))
    }
    // B필러
    drawLine(lerp(p.mid, Color.Black, 0.55f), o(8.15f, g.roof + 0.05f), o(8.05f, belt), u * 0.14f)
    val windshield = Path().apply {
        moveTo(roofFrontL.x, roofFrontL.y); lineTo(roofFrontR.x, roofFrontR.y)
        lineTo(hoodBackR.x, hoodBackR.y); lineTo(hoodBackL.x, hoodBackL.y); close()
    }
    drawPath(windshield, Brush.linearGradient(listOf(Color(0xFF66727E), Color(0xFF2C333B)), start = roofFrontL, end = hoodBackR))
    clipPath(windshield) {
        drawLine(Color.White.copy(alpha = 0.3f), o(10.4f, belt - 0.5f), o(10.9f, g.roof), u * 0.4f)
        drawLine(Color.White.copy(alpha = 0.16f), o(11.3f, belt - 0.45f), o(11.6f, g.roof), u * 0.14f)
        if (wild || grumpy) {
            val c = o(11.0f, g.roof + 0.8f)
            listOf(10f, 80f, 150f, 230f, 300f).forEach { deg ->
                rotate(deg, c) { drawLine(Color.White.copy(alpha = 0.85f), c, c + Offset(u * 0.8f, u * 0.08f), u * 0.03f) }
            }
        }
    }
    // 지붕 윗면과 A필러
    val roofTop = Path().apply {
        moveTo(roofRear.x, roofRear.y - u * 0.08f); lineTo(roofFrontL.x, roofFrontL.y - u * 0.1f)
        lineTo(roofFrontR.x, roofFrontR.y - u * 0.05f); lineTo(u * 8.7f, roofRear.y - u * 0.02f); close()
    }
    drawPath(roofTop, lerp(p.top, Color.White, 0.35f))
    drawLine(lerp(p.mid, Color.Black, 0.45f), roofFrontL, hoodBackL, u * 0.1f, StrokeCap.Round)
    drawLine(lerp(p.mid, Color.Black, 0.45f), roofFrontR, hoodBackR, u * 0.07f, StrokeCap.Round)
    if (suv) { // 루프랙
        drawLine(Color(0xFF2B2E31), o(7.2f, g.roof - 0.2f), o(10.6f, g.roof - 0.18f), u * 0.12f, StrokeCap.Round)
        drawLine(Color(0xFF2B2E31), o(7.5f, g.roof - 0.18f), o(7.55f, g.roof + 0.05f), u * 0.07f)
        drawLine(Color(0xFF2B2E31), o(10.2f, g.roof - 0.18f), o(10.25f, g.roof + 0.05f), u * 0.07f)
    }
    // 사이드미러
    val mirror = o(10.3f, belt - 0.42f)
    drawRoundRect(Brush.verticalGradient(listOf(p.top, p.bottom), startY = mirror.y - u * 0.3f, endY = mirror.y + u * 0.2f), mirror - Offset(u * 0.55f, u * 0.3f), Size(u * 0.6f, u * 0.42f), CornerRadius(u * 0.16f))
    // 문 손잡이, 하단 가니시
    drawRoundRect(p.line.copy(alpha = 0.55f), o(7.0f, belt + 0.45f), Size(u * 0.45f, u * 0.08f), CornerRadius(u * 0.04f))
    drawRoundRect(p.line.copy(alpha = 0.55f), o(9.2f, belt + 0.45f), Size(u * 0.45f, u * 0.08f), CornerRadius(u * 0.04f))
    drawLine(p.line.copy(alpha = 0.3f), o(8.1f, belt + 0.05f), o(8.15f, g.rocker - 0.15f), u * 0.03f)
    drawLine(Color(0xFF3A3A40).copy(alpha = 0.5f), o(7.6f, g.rocker - 0.12f), o(10.0f, g.rocker - 0.14f), u * 0.18f, StrokeCap.Round)

    // 바퀴 (아치 위에)
    drawWheel(u, o(g.rearWheel.x, g.rearWheel.y), g.wheelR, stage)
    drawWheel(u, o(g.frontWheel.x, g.frontWheel.y), g.wheelR, stage)

    // 차종별 램프·그릴
    drawFrontDetails(u, model, stage, g.cornerX, g.frontX1, frontTop)

    // 범퍼
    val dented = part == AvatarPart.DENTED_BUMPER || wild
    val bTop = g.rocker - 0.55f
    val bumper = Path().apply {
        moveTo(u * (g.cornerX + 0.28f), u * (bTop + 0.02f))
        if (dented) {
            lineTo(u * 13.4f, u * bTop); lineTo(u * 13.7f, u * (bTop + 0.25f)); lineTo(u * 14.05f, u * bTop)
        }
        lineTo(u * (g.frontX1 - 0.12f), u * (bTop - 0.03f))
        lineTo(u * (g.frontX1 - 0.2f), u * (g.rocker - 0.05f))
        lineTo(u * (g.cornerX + 0.2f), u * g.rocker)
        close()
    }
    drawPath(bumper, Brush.verticalGradient(listOf(Color(0xFFC9CCCE), Color(0xFF5C6266)), startY = u * bTop, endY = u * g.rocker))
    drawLine(Color.White.copy(alpha = 0.6f), o(g.cornerX + 0.45f, bTop + 0.08f), o(g.frontX1 - 0.3f, bTop + 0.05f), u * 0.04f, StrokeCap.Round)
    if (dented) {
        val cr = Path().apply { moveTo(u * 13.7f, u * (bTop + 0.25f)); lineTo(u * 13.6f, u * (bTop + 0.5f)); moveTo(u * 13.7f, u * (bTop + 0.25f)); lineTo(u * 13.95f, u * (bTop + 0.45f)) }
        drawPath(cr, Color(0xFF1E1E22), style = Stroke(u * 0.04f, cap = StrokeCap.Round))
    }
    // 번호판
    val plate = o(13.45f, bTop + 0.12f)
    drawRoundRect(Color.White, plate, Size(u * 1.0f, u * 0.28f), CornerRadius(u * 0.05f))
    drawRoundRect(Color(0xFF2B2E31).copy(alpha = 0.5f), plate, Size(u * 1.0f, u * 0.28f), CornerRadius(u * 0.05f), style = Stroke(u * 0.03f))

    // 외곽선
    drawPath(side, p.line.copy(alpha = 0.3f), style = line)
    drawPath(front, p.line.copy(alpha = 0.3f), style = line)

    // 때·흠집·광택
    if (grumpy || wild) drawDirt(u, side, front, if (wild) 1f else 0.55f, g)
    if (part == AvatarPart.SIDE_SCRATCH || grumpy || wild) {
        val n = if (part == AvatarPart.SIDE_SCRATCH || wild) 4 else 2
        for (i in 0 until n) {
            val x = 8.6f + i * 0.32f
            drawLine(Color.White.copy(alpha = 0.75f), o(x, belt + 0.55f), o(x + 0.75f, belt + 0.95f), u * 0.04f, StrokeCap.Round)
            drawLine(p.line.copy(alpha = 0.6f), o(x, belt + 0.6f), o(x + 0.75f, belt + 1.0f), u * 0.03f, StrokeCap.Round)
        }
    }
    if (stage == Stage.ANGEL) {
        listOf(Offset(12.4f, belt - 0.2f), Offset(7.4f, g.roof + 0.1f), Offset(14.8f, belt + 0.6f)).forEachIndexed { i, s ->
            val a = (sin((cycle + i / 3f) * TAU2 * 2f) + 1f) / 2f
            drawSparkle4(o(s.x, s.y), u * 0.3f * (0.5f + 0.5f * a), Color.White.copy(alpha = 0.4f + 0.6f * a))
        }
    }
    if (wild) { // 비상등 깜빡임
        if (sin(cycle * TAU2 * 4f) > 0f) listOf(o(g.cornerX + 0.25f, frontTop + 0.55f), o(g.frontX1 - 0.15f, frontTop + 0.75f)).forEach { c ->
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFA020).copy(alpha = 0.85f), Color.Transparent), center = c, radius = u * 0.7f), u * 0.7f, c)
        }
    }
}

private fun DrawScope.drawFrontDetails(u: Float, model: CarModel, stage: Stage, x0: Float, x1: Float, top: Float) {
    val lampOn = when (stage) {
        Stage.GRUMPY -> 0.35f
        Stage.WILD -> 0.12f
        else -> 1f
    }
    val lamp = lerp(Color(0xFF9A9EA3), Color.White, lampOn)
    val glow = Color.White.copy(alpha = 0.6f * lampOn)
    val dark = Color(0xFF1C1E22)
    fun o(x: Float, y: Float) = Offset(u * x, u * y)
    // 앞면 위 가장자리는 오른쪽으로 갈수록 살짝 내려간다
    fun y(x: Float, dy: Float) = top + 0.22f * ((x - x0) / (x1 - x0)) + dy
    fun glowAt(c: Offset, r: Float) = drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center = c, radius = r), r, c)
    val gl = x0 + 0.55f
    val gr = x1 - 0.3f
    // 램프 하우징 (차체색과 상관없이 램프가 보이도록 어두운 띠)
    val housing = Path().apply {
        moveTo(u * (x0 + 0.22f), u * y(x0, 0.18f)); lineTo(u * (x1 - 0.1f), u * y(x1, 0.2f))
        lineTo(u * (x1 - 0.14f), u * y(x1, 0.62f)); lineTo(u * (x0 + 0.28f), u * y(x0, 0.6f)); close()
    }
    if (model != CarModel.TUCSON) drawPath(housing, Color(0xFF2A2D33))
    when (model) {
        CarModel.AVANTE -> {
            // 좌우를 잇는 슬림 램프 + 넓은 보석 무늬 그릴
            val l = Path().apply {
                moveTo(u * (x0 + 0.3f), u * y(x0 + 0.3f, 0.32f)); lineTo(u * (x1 - 0.15f), u * y(x1, 0.35f))
                lineTo(u * (x1 - 0.2f), u * y(x1, 0.52f)); lineTo(u * (x0 + 0.35f), u * y(x0, 0.5f)); close()
            }
            glowAt(o(x0 + 0.9f, y(x0 + 0.9f, 0.42f)), u * 0.8f); glowAt(o(x1 - 0.6f, y(x1 - 0.6f, 0.45f)), u * 0.8f)
            drawPath(l, lamp)
            val g = Path().apply {
                moveTo(u * gl, u * y(gl, 0.85f)); lineTo(u * gr, u * y(gr, 0.85f)); lineTo(u * (gr - 0.25f), u * y(gr, 1.5f)); lineTo(u * (gl + 0.2f), u * y(gl, 1.5f)); close()
            }
            drawPath(g, dark)
            clipPath(g) {
                for (r in 0 until 3) for (c in 0 until 12) {
                    val cx = u * (gl + c * 0.2f + if (r % 2 == 0) 0f else 0.1f); val cy = u * y(gl + c * 0.2f, 0.98f + r * 0.2f)
                    val d = Path().apply { moveTo(cx - u * 0.07f, cy); lineTo(cx, cy - u * 0.045f); lineTo(cx + u * 0.07f, cy); lineTo(cx, cy + u * 0.045f); close() }
                    drawPath(d, Color(0xFF6A6E76))
                }
            }
        }
        CarModel.SONATA -> {
            val bar = Path().apply {
                moveTo(u * (x0 + 0.25f), u * y(x0, 0.22f)); lineTo(u * (x1 - 0.15f), u * y(x1, 0.25f))
                lineTo(u * (x1 - 0.15f), u * y(x1, 0.32f)); lineTo(u * (x0 + 0.25f), u * y(x0, 0.29f)); close()
            }
            glowAt(o((x0 + x1) / 2f, y((x0 + x1) / 2f, 0.3f)), u * 1.4f)
            drawPath(bar, lamp)
            listOf(x0 + 0.35f, x1 - 0.95f).forEach { x -> drawRoundRect(lamp, o(x, y(x, 0.45f)), Size(u * 0.7f, u * 0.24f), CornerRadius(u * 0.1f)) }
            drawRoundRect(dark, o(gl, y(gl, 0.95f)), Size(u * (gr - gl - 0.1f), u * 0.45f), CornerRadius(u * 0.18f))
            for (i in 1..2) drawLine(Color(0xFF6A6E76), o(gl + 0.1f, y(gl, 0.95f + i * 0.15f)), o(gr - 0.2f, y(gr, 0.95f + i * 0.15f)), u * 0.03f)
        }
        CarModel.TUCSON -> {
            // 그릴에 숨은 날개 모양 주간주행등
            val g = Path().apply {
                moveTo(u * (x0 + 0.3f), u * y(x0, 0.3f)); lineTo(u * (x1 - 0.12f), u * y(x1, 0.3f))
                lineTo(u * (gr - 0.1f), u * y(gr, 1.5f)); lineTo(u * (gl + 0.1f), u * y(gl, 1.5f)); close()
            }
            drawPath(g, dark)
            clipPath(g) {
                for (r in 0 until 4) for (c in 0 until 10) {
                    val x = u * (x0 + 0.35f + c * 0.27f + r * 0.06f); val yy = u * y(x0 + c * 0.27f, 0.33f + r * 0.3f)
                    val cell = Path().apply { moveTo(x, yy + u * 0.03f); lineTo(x + u * 0.19f, yy + u * 0.03f); lineTo(x + u * 0.14f, yy + u * 0.26f); lineTo(x - u * 0.05f, yy + u * 0.26f); close() }
                    val lit = (c < 2 || c > 7) && r < 2
                    drawPath(cell, if (lit) lamp else Color(0xFF5C6066))
                }
            }
            glowAt(o(x0 + 0.6f, y(x0, 0.5f)), u * 0.7f); glowAt(o(x1 - 0.5f, y(x1, 0.5f)), u * 0.7f)
        }
        CarModel.SANTAFE -> {
            // H 모양 램프 + 네모 그릴
            listOf(x0 + 0.35f, x1 - 1.0f).forEach { x ->
                val tl = o(x, y(x, 0.25f))
                glowAt(tl + Offset(u * 0.33f, u * 0.3f), u * 0.8f)
                drawRoundRect(dark, tl, Size(u * 0.68f, u * 0.62f), CornerRadius(u * 0.07f))
                drawRect(lamp, tl + Offset(u * 0.08f, u * 0.07f), Size(u * 0.12f, u * 0.48f))
                drawRect(lamp, tl + Offset(u * 0.48f, u * 0.07f), Size(u * 0.12f, u * 0.48f))
                drawRect(lamp, tl + Offset(u * 0.2f, u * 0.25f), Size(u * 0.28f, u * 0.11f))
            }
            val tl = o(x0 + 1.15f, y(x0 + 1.15f, 0.95f))
            drawRoundRect(dark, tl, Size(u * 1.25f, u * 0.6f), CornerRadius(u * 0.05f))
            for (r in 0 until 2) for (c in 0 until 4) {
                drawRoundRect(Color(0xFF5C6066), tl + Offset(u * (0.07f + c * 0.29f), u * (0.06f + r * 0.27f)), Size(u * 0.24f, u * 0.22f), CornerRadius(u * 0.03f))
            }
        }
    }
}

private fun DrawScope.drawWheel(u: Float, c: Offset, k: Float, stage: Stage, squash: Float = 0.9f, shaded: Boolean = false) {
    val r = u * 0.95f * k
    scale(squash, 1f, pivot = c) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3A40), Color(0xFF141418)), center = c, radius = r), r, c)
        val rim = r * 0.64f
        val rimCol = if (stage == Stage.WILD) Color(0xFF5A5A60) else Color(0xFFC9CCCE)
        drawCircle(Brush.radialGradient(listOf(lerp(rimCol, Color.White, 0.35f), lerp(rimCol, Color.Black, 0.35f)), center = c - Offset(rim * 0.25f, rim * 0.25f), radius = rim * 1.3f), rim, c)
        for (i in 0 until 5) {
            rotate(i * 72f + 15f, c) {
                drawLine(Color(0xFF2B2E31), c + Offset(0f, -rim * 0.28f), c + Offset(0f, -rim * 0.95f), rim * 0.18f, StrokeCap.Round)
            }
        }
        drawCircle(Color(0xFF2B2E31), rim * 0.24f, c)
        drawCircle(Color(0xFF8A8E94), rim * 0.1f, c)
        if (shaded) drawCircle(Color.Black.copy(alpha = 0.55f), r, c) // 반대편 바퀴는 그늘
    }
}

private fun DrawScope.drawDirt(u: Float, side: Path, front: Path, k: Float, g: CarGeo) {
    val mud = Color(0xFF6B5638)
    clipPath(side) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, mud.copy(alpha = 0.6f * k)), startY = u * (g.rocker - 1.2f), endY = u * g.rocker), Offset(u * 4.8f, u * (g.rocker - 1.2f)), Size(u * 8.2f, u * 1.3f))
        listOf(Offset(6.0f, 8.3f), Offset(7.3f, 8.8f), Offset(9.4f, 8.4f), Offset(5.6f, 7.9f), Offset(8.7f, 8.1f), Offset(10.6f, 8.0f)).forEachIndexed { i, s ->
            drawCircle(mud.copy(alpha = 0.5f * k), u * (0.1f + (i % 3) * 0.06f), Offset(u * s.x, u * s.y))
        }
    }
    clipPath(front) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, mud.copy(alpha = 0.5f * k)), startY = u * (g.rocker - 1.0f), endY = u * g.rocker), Offset(u * 12.4f, u * (g.rocker - 1.0f)), Size(u * 3.2f, u * 1.1f))
    }
}

// ───────────────────────────── 운전자 캐릭터 ─────────────────────────────

private class Look(val jacket: Color, val inner: Color, val pants: Color, val hair: Color, val skin: Color)

private fun lookOf(stage: Stage) = when (stage) {
    Stage.ANGEL -> Look(Color(0xFFF4F1EA), Color(0xFFBFD8E6), Color(0xFF8A9AAE), Color(0xFF3A2A22), Color(0xFFF5D3B8))
    Stage.GENTLE -> Look(Color(0xFF6E8A6A), Color(0xFFF4F4F2), Color(0xFF4F5559), Color(0xFF3A2A22), Color(0xFFF5D3B8))
    Stage.NORMAL -> Look(Color(0xFF6F7E6A), Color(0xFFEDEDEA), Color(0xFF4A4E52), Color(0xFF3A2A22), Color(0xFFF2CFB3))
    Stage.GRUMPY -> Look(Color(0xFF5C6266), Color(0xFFBFC2C4), Color(0xFF3B3E42), Color(0xFF2E2420), Color(0xFFE9C6AA))
    Stage.WILD -> Look(Color(0xFF2B2E31), Color(0xFF7A2626), Color(0xFF2B2E31), Color(0xFF22191A), Color(0xFFE6BDA0))
}

private fun DrawScope.drawPerson(u: Float, stage: Stage, part: AvatarPart, mood: Mood, breath: Float, cycle: Float) {
    val l = lookOf(stage)
    val bob = -breath * u * 0.06f
    val cx = u * 2.9f
    val feet = u * 10.55f
    val wave = sin(cycle * TAU2 * 2f)
    val outline = Color(0xFF2B2420).copy(alpha = 0.25f)

    // 천사: 등 뒤 작은 깃털 날개
    if (stage == Stage.ANGEL) listOf(-1f, 1f).forEach { s ->
        val root = Offset(cx + s * u * 0.55f, u * 6.0f + bob)
        rotate(s * wave * 6f, root) {
            listOf(0f, 0.32f, 0.62f).forEachIndexed { i, dy ->
                val len = u * (1.35f - i * 0.25f)
                val f = Path().apply {
                    moveTo(root.x, root.y + u * dy)
                    cubicTo(root.x + s * len * 0.4f, root.y + u * dy - u * 0.55f, root.x + s * len, root.y + u * dy - u * 0.35f, root.x + s * len, root.y + u * dy + u * 0.05f)
                    cubicTo(root.x + s * len * 0.7f, root.y + u * dy + u * 0.3f, root.x + s * len * 0.3f, root.y + u * dy + u * 0.3f, root.x, root.y + u * dy + u * 0.25f)
                    close()
                }
                drawPath(f, Brush.horizontalGradient(listOf(Color(0xFFEDE6FA), Color.White), startX = root.x, endX = root.x + s * len))
                drawPath(f, Color(0xFFCDBEF0), style = Stroke(u * 0.03f))
            }
        }
    }

    // 다리·신발
    val hip = u * 7.75f + bob
    listOf(-1f, 1f).forEach { s ->
        val x = cx + s * u * 0.32f
        drawRoundRect(Brush.horizontalGradient(listOf(lerp(l.pants, Color.White, 0.08f), lerp(l.pants, Color.Black, 0.2f)), startX = x - u * 0.3f, endX = x + u * 0.3f),
            Offset(x - u * 0.3f, hip), Size(u * 0.6f, feet - hip - u * 0.3f), CornerRadius(u * 0.2f))
        drawRoundRect(Color(0xFFF2F0EA), Offset(x - u * 0.38f + s * u * 0.08f, feet - u * 0.42f), Size(u * 0.8f, u * 0.42f), CornerRadius(u * 0.18f))
        drawLine(Color(0xFFBFC2C4), Offset(x - u * 0.34f + s * u * 0.08f, feet - u * 0.06f), Offset(x + u * 0.38f + s * u * 0.08f, feet - u * 0.06f), u * 0.07f, StrokeCap.Round)
    }

    // 몸통 (재킷)
    val shoulderY = u * 5.55f + bob
    val torso = Path().apply {
        moveTo(cx - u * 0.95f, shoulderY + u * 0.3f)
        cubicTo(cx - u * 0.95f, shoulderY, cx - u * 0.6f, shoulderY - u * 0.05f, cx, shoulderY - u * 0.05f)
        cubicTo(cx + u * 0.6f, shoulderY - u * 0.05f, cx + u * 0.95f, shoulderY, cx + u * 0.95f, shoulderY + u * 0.3f)
        lineTo(cx + u * 0.9f, hip + u * 0.15f)
        lineTo(cx - u * 0.9f, hip + u * 0.15f)
        close()
    }
    drawPath(torso, Brush.horizontalGradient(listOf(lerp(l.jacket, Color.White, 0.15f), lerp(l.jacket, Color.Black, 0.18f)), startX = cx - u, endX = cx + u))
    // 안쪽 티셔츠, 지퍼
    val v = Path().apply { moveTo(cx - u * 0.3f, shoulderY); lineTo(cx + u * 0.3f, shoulderY); lineTo(cx + u * 0.12f, hip + u * 0.1f); lineTo(cx - u * 0.12f, hip + u * 0.1f); close() }
    drawPath(v, l.inner)
    drawLine(lerp(l.jacket, Color.Black, 0.35f), Offset(cx + u * 0.14f, shoulderY + u * 0.1f), Offset(cx + u * 0.1f, hip + u * 0.1f), u * 0.03f)
    drawRoundRect(lerp(l.jacket, Color.Black, 0.2f), Offset(cx - u * 0.9f, hip - u * 0.05f), Size(u * 1.8f, u * 0.22f), CornerRadius(u * 0.1f))
    drawPath(torso, outline, style = Stroke(u * 0.03f))
    if (part == AvatarPart.LOOSE_BELT) { // 안전벨트를 손에 들고 있음 → 대신 어깨에 벨트 자국
        drawLine(Color(0xFF2B2E31), Offset(cx - u * 0.7f, shoulderY + u * 0.1f), Offset(cx + u * 0.7f, hip - u * 0.1f), u * 0.16f, StrokeCap.Round)
    }

    // 팔 (단계별 자세)
    val armCol = Brush.verticalGradient(listOf(lerp(l.jacket, Color.White, 0.1f), lerp(l.jacket, Color.Black, 0.15f)), startY = shoulderY, endY = hip)
    fun arm(from: Offset, to: Offset) {
        drawLine(armCol, from, to, u * 0.42f, StrokeCap.Round)
        drawCircle(l.skin, u * 0.2f, to + (to - from) * (0.12f / ((to - from).getDistance() / u).coerceAtLeast(0.01f)))
    }
    val lSh = Offset(cx - u * 0.82f, shoulderY + u * 0.3f)
    val rSh = Offset(cx + u * 0.82f, shoulderY + u * 0.3f)
    when (stage) {
        Stage.ANGEL, Stage.GENTLE -> {
            arm(lSh, Offset(cx - u * 1.0f, hip - u * 0.1f))
            val hand = Offset(cx + u * 1.55f + wave * u * 0.12f, shoulderY - u * 0.85f)
            arm(rSh, hand) // 손 흔들기
        }
        Stage.NORMAL -> {
            arm(lSh, Offset(cx - u * 1.0f, hip - u * 0.05f))
            arm(rSh, Offset(cx + u * 1.0f, hip - u * 0.05f))
        }
        Stage.GRUMPY -> { // 팔짱
            drawLine(armCol, lSh, Offset(cx + u * 0.55f, shoulderY + u * 1.0f), u * 0.42f, StrokeCap.Round)
            drawLine(armCol, rSh, Offset(cx - u * 0.55f, shoulderY + u * 1.15f), u * 0.42f, StrokeCap.Round)
            drawCircle(l.skin, u * 0.18f, Offset(cx + u * 0.68f, shoulderY + u * 0.95f))
            drawCircle(l.skin, u * 0.18f, Offset(cx - u * 0.68f, shoulderY + u * 1.1f))
        }
        Stage.WILD -> { // 주먹 쥐고 화남
            arm(lSh, Offset(cx - u * 1.25f, shoulderY + u * 1.2f))
            arm(rSh, Offset(cx + u * 1.35f, shoulderY - u * 0.3f + wave * u * 0.15f))
        }
    }

    // 목, 머리
    val headC = Offset(cx, u * 4.35f + bob)
    val hr = u * 1.05f
    drawRect(l.skin, Offset(cx - u * 0.18f, headC.y + hr * 0.75f), Size(u * 0.36f, u * 0.45f))
    // 귀
    drawCircle(lerp(l.skin, Color(0xFFD9A07F), 0.3f), u * 0.2f, headC + Offset(-hr * 0.95f, hr * 0.1f))
    drawCircle(lerp(l.skin, Color(0xFFD9A07F), 0.3f), u * 0.2f, headC + Offset(hr * 0.95f, hr * 0.1f))
    drawOval(Brush.radialGradient(listOf(lerp(l.skin, Color.White, 0.25f), l.skin, lerp(l.skin, Color(0xFFC08A6A), 0.35f)), center = headC - Offset(hr * 0.25f, hr * 0.3f), radius = hr * 1.5f),
        headC - Offset(hr * 0.95f, hr * 0.95f), Size(hr * 1.9f, hr * 2.0f))
    // 머리카락 (위쪽 덮기)
    val hair = Path().apply {
        moveTo(headC.x - hr * 1.02f, headC.y + hr * 0.05f)
        cubicTo(headC.x - hr * 1.15f, headC.y - hr * 1.1f, headC.x - hr * 0.2f, headC.y - hr * 1.45f, headC.x + hr * 0.35f, headC.y - hr * 1.15f)
        cubicTo(headC.x + hr * 1.0f, headC.y - hr * 1.2f, headC.x + hr * 1.2f, headC.y - hr * 0.4f, headC.x + hr * 1.0f, headC.y + hr * 0.05f)
        cubicTo(headC.x + hr * 0.75f, headC.y - hr * 0.45f, headC.x + hr * 0.3f, headC.y - hr * 0.55f, headC.x - hr * 0.05f, headC.y - hr * 0.42f)
        cubicTo(headC.x - hr * 0.45f, headC.y - hr * 0.55f, headC.x - hr * 0.8f, headC.y - hr * 0.35f, headC.x - hr * 1.02f, headC.y + hr * 0.05f)
        close()
    }
    drawPath(hair, Brush.verticalGradient(listOf(lerp(l.hair, Color.White, 0.12f), l.hair), startY = headC.y - hr * 1.4f, endY = headC.y))
    if (stage == Stage.WILD) { // 삐죽한 머리
        listOf(-0.6f, -0.1f, 0.4f).forEach { x ->
            val sp = Path().apply {
                moveTo(headC.x + hr * (x - 0.2f), headC.y - hr * 1.0f); lineTo(headC.x + hr * (x + 0.1f), headC.y - hr * 1.65f); lineTo(headC.x + hr * (x + 0.3f), headC.y - hr * 1.0f); close()
            }
            drawPath(sp, l.hair)
        }
    }

    drawFace(u, stage, part, mood, headC, hr, cycle)

    // 천사 링, 화남 표시, 땀
    when (stage) {
        Stage.ANGEL -> {
            val tl = Offset(headC.x - hr * 0.75f, headC.y - hr * 1.75f + wave * u * 0.05f)
            drawOval(Color(0xFFFFE27A).copy(alpha = 0.35f), tl, Size(hr * 1.5f, hr * 0.38f), style = Stroke(u * 0.25f))
            drawOval(Color(0xFFFFD34D), tl, Size(hr * 1.5f, hr * 0.38f), style = Stroke(u * 0.1f))
        }
        Stage.GRUMPY -> drawSweat(u, headC + Offset(hr * 0.95f, -hr * 0.5f))
        Stage.WILD -> {
            val c = headC + Offset(hr * 1.1f, -hr * 1.0f)
            listOf(0f, 90f, 180f, 270f).forEach { deg ->
                rotate(deg, c) { drawArc(Color(0xFFE5342B), 200f, 50f, false, Offset(c.x + u * 0.06f, c.y + u * 0.06f), Size(u * 0.38f, u * 0.38f), style = Stroke(u * 0.08f, cap = StrokeCap.Round)) }
            }
            // 김 나는 머리
            for (i in 0 until 2) {
                val ph = (cycle * 2f + i * 0.5f) % 1f
                val s = headC + Offset(-hr * 0.5f + i * hr, -hr * (1.6f + ph * 0.8f))
                drawCircle(Color.White.copy(alpha = 0.35f * (1f - ph)), u * (0.18f + ph * 0.25f), s)
            }
        }
        else -> {}
    }
    if (mood == Mood.SLEEPY) {
        for (i in 0 until 3) {
            val ph = (cycle * 1.5f + i / 3f) % 1f
            val k = u * (0.14f + ph * 0.18f)
            val o = headC + Offset(hr * 1.2f + ph * u, -hr * (1.0f + ph * 1.2f))
            val z = Path().apply { moveTo(o.x - k, o.y - k); lineTo(o.x + k, o.y - k); lineTo(o.x - k, o.y + k); lineTo(o.x + k, o.y + k) }
            drawPath(z, Color(0xFF5C6266).copy(alpha = 1f - ph), style = Stroke(u * 0.06f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawFace(u: Float, stage: Stage, part: AvatarPart, mood: Mood, c: Offset, hr: Float, cycle: Float) {
    val ink = Color(0xFF2B2420)
    val eyeY = c.y + hr * 0.12f
    val eyes = listOf(Offset(c.x - hr * 0.38f, eyeY) to -1f, Offset(c.x + hr * 0.38f, eyeY) to 1f)
    val blinkT = ((cycle - 0.93f) / 0.07f).coerceIn(0f, 1f)
    val open = 1f - sin(blinkT * PI.toFloat()) * 0.9f
    val mouthY = c.y + hr * 0.62f
    // 볼
    if (stage == Stage.ANGEL || stage == Stage.GENTLE) eyes.forEach { (e, s) ->
        val b = Offset(e.x + s * hr * 0.12f, mouthY - hr * 0.1f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9EB0).copy(alpha = 0.55f), Color.Transparent), center = b, radius = hr * 0.25f), hr * 0.25f, b)
    }
    when {
        mood == Mood.DIZZY -> eyes.forEach { (e, s) ->
            rotate(cycle * 720f * s, e) {
                val p = Path()
                for (i in 0..36) {
                    val a = i / 36f * 4f * PI.toFloat(); val r = hr * 0.17f * i / 36f
                    val pt = Offset(e.x + kotlin.math.cos(a) * r, e.y + sin(a) * r)
                    if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                }
                drawPath(p, ink, style = Stroke(u * 0.05f, cap = StrokeCap.Round))
            }
        }
        mood == Mood.SLEEPY -> eyes.forEach { (e, _) -> drawLine(ink, e - Offset(hr * 0.14f, 0f), e + Offset(hr * 0.14f, 0f), u * 0.06f, StrokeCap.Round) }
        part == AvatarPart.FLAME_EYES || stage == Stage.WILD -> eyes.forEach { (e, s) ->
            drawCircle(Color.White, hr * 0.17f, e)
            drawCircle(Color(0xFFD9342B), hr * 0.1f, e + Offset(-s * hr * 0.03f, hr * 0.02f))
            drawLine(ink, e + Offset(-s * hr * 0.28f, -hr * 0.3f), e + Offset(s * hr * 0.2f, -hr * 0.14f), u * 0.09f, StrokeCap.Round)
        }
        stage == Stage.GRUMPY -> eyes.forEach { (e, s) ->
            drawCircle(ink, hr * 0.11f, e + Offset(0f, hr * 0.04f))
            drawLine(lerp(Color(0xFFE9C6AA), Color.Black, 0.1f), e + Offset(-hr * 0.2f, -hr * 0.05f), e + Offset(hr * 0.2f, -hr * 0.05f), u * 0.12f) // 반쯤 감은 눈꺼풀
            drawLine(ink, e + Offset(-s * hr * 0.25f, -hr * 0.22f), e + Offset(s * hr * 0.18f, -hr * 0.3f), u * 0.07f, StrokeCap.Round)
        }
        else -> eyes.forEach { (e, _) ->
            scale(1f, open, pivot = e) {
                drawOval(ink, e - Offset(hr * 0.12f, hr * 0.16f), Size(hr * 0.24f, hr * 0.32f))
                drawCircle(Color.White, hr * 0.05f, e + Offset(-hr * 0.04f, -hr * 0.07f))
            }
            drawArc(ink, 210f, 120f, false, e - Offset(hr * 0.18f, hr * 0.42f), Size(hr * 0.36f, hr * 0.2f), style = Stroke(u * 0.045f, cap = StrokeCap.Round))
        }
    }
    // 입
    when {
        mood == Mood.DIZZY -> {
            val p = Path().apply {
                moveTo(c.x - hr * 0.22f, mouthY)
                for (i in 0 until 4) quadraticTo(c.x - hr * 0.22f + hr * 0.11f * (i + 0.5f), mouthY + if (i % 2 == 0) -hr * 0.06f else hr * 0.06f, c.x - hr * 0.22f + hr * 0.11f * (i + 1), mouthY)
            }
            drawPath(p, ink, style = Stroke(u * 0.045f, cap = StrokeCap.Round))
        }
        (stage == Stage.ANGEL || stage == Stage.GENTLE || mood == Mood.HAPPY) && stage != Stage.GRUMPY && stage != Stage.WILD -> {
            val m = Path().apply {
                moveTo(c.x - hr * 0.22f, mouthY - hr * 0.04f); lineTo(c.x + hr * 0.22f, mouthY - hr * 0.04f)
                cubicTo(c.x + hr * 0.2f, mouthY + hr * 0.24f, c.x - hr * 0.2f, mouthY + hr * 0.24f, c.x - hr * 0.22f, mouthY - hr * 0.04f); close()
            }
            drawPath(m, Color(0xFF8A3A3A))
            clipPath(m) { drawCircle(Color(0xFFFF9EA8), hr * 0.12f, Offset(c.x, mouthY + hr * 0.2f)) }
        }
        stage == Stage.NORMAL -> drawArc(ink, 20f, 140f, false, Offset(c.x - hr * 0.15f, mouthY - hr * 0.12f), Size(hr * 0.3f, hr * 0.16f), style = Stroke(u * 0.05f, cap = StrokeCap.Round))
        stage == Stage.GRUMPY -> drawArc(ink, 200f, 140f, false, Offset(c.x - hr * 0.17f, mouthY), Size(hr * 0.34f, hr * 0.18f), style = Stroke(u * 0.05f, cap = StrokeCap.Round))
        else -> { // 이 악문 입
            drawRoundRect(Color.White, Offset(c.x - hr * 0.25f, mouthY - hr * 0.06f), Size(hr * 0.5f, hr * 0.17f), CornerRadius(hr * 0.04f))
            drawRoundRect(ink, Offset(c.x - hr * 0.25f, mouthY - hr * 0.06f), Size(hr * 0.5f, hr * 0.17f), CornerRadius(hr * 0.04f), style = Stroke(u * 0.035f))
            for (i in 1..3) drawLine(ink, Offset(c.x - hr * 0.25f + hr * 0.125f * i, mouthY - hr * 0.06f), Offset(c.x - hr * 0.25f + hr * 0.125f * i, mouthY + hr * 0.11f), u * 0.02f)
        }
    }
}

private fun DrawScope.drawSweat(u: Float, c: Offset) {
    val p = Path().apply {
        moveTo(c.x, c.y - u * 0.22f)
        cubicTo(c.x + u * 0.15f, c.y, c.x + u * 0.12f, c.y + u * 0.14f, c.x, c.y + u * 0.14f)
        cubicTo(c.x - u * 0.12f, c.y + u * 0.14f, c.x - u * 0.15f, c.y, c.x, c.y - u * 0.22f); close()
    }
    drawPath(p, Color(0xFF8FC9F2))
}

private fun DrawScope.drawForeground(u: Float, stage: Stage, part: AvatarPart, cycle: Float) {
    val e = envOf(stage)
    if (e.vignette > 0f) {
        val c = Offset(u * 8f, u * 6.5f)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = e.vignette)), center = c, radius = u * 10f), Offset.Zero, Size(u * 16, u * 12))
    }
    if (stage == Stage.ANGEL) { // 따뜻한 빛
        drawRect(Brush.verticalGradient(listOf(e.light.copy(alpha = 0.12f), Color.Transparent), startY = 0f, endY = u * 6f), Offset.Zero, Size(u * 16, u * 6))
    }
}

private fun DrawScope.drawSparkle4(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r); quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color)
}
