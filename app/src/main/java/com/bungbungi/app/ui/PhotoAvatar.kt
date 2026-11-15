package com.bungbungi.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bungbungi.app.R
import com.bungbungi.app.logic.Stage
import kotlin.math.PI
import kotlin.math.sin

/** 단계별 아바타 이미지: 차고와 운전자는 그대로, 점수가 내려갈수록 차 상태만 나빠진다. */
fun avatarImage(stage: Stage): Int = when (stage) {
    Stage.ANGEL -> R.drawable.avatar_angel
    Stage.GENTLE -> R.drawable.avatar_gentle
    Stage.NORMAL -> R.drawable.avatar_normal
    Stage.GRUMPY -> R.drawable.avatar_grumpy
    Stage.WILD -> R.drawable.avatar_wild
}

@Composable
fun PhotoAvatar(stage: Stage, mood: Mood, modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "photo")
    val breath by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val cycle by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "cycle")
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(avatarImage(stage)),
            contentDescription = "${stage.label} 붕붕이",
            contentScale = ContentScale.Fit,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).graphicsLayer {
                // 평소엔 아주 천천히 숨 쉬듯, 위반 직후엔 흔들림
                val s = 1f + breath * 0.012f
                scaleX = s; scaleY = s
                if (mood == Mood.DIZZY) {
                    translationX = sin(cycle * 2f * PI.toFloat() * 3f) * 10f
                    rotationZ = sin(cycle * 2f * PI.toFloat() * 3f) * 0.8f
                }
                alpha = if (mood == Mood.SLEEPY) 0.75f else 1f
            },
        )
    }
}
