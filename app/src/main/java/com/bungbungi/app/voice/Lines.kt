package com.bungbungi.app.voice

import com.bungbungi.app.logic.BonusType
import com.bungbungi.app.logic.Stage
import com.bungbungi.app.logic.Violation

/** 아바타 대사. 경고 대신 아바타의 감정으로 말한다. */
object Lines {
    fun violation(v: Violation): String = when (v) {
        Violation.SPEEDING -> listOf("너무 빨라서 무서워요. 조금만 천천히 가요.", "바람이 너무 세요! 속도를 줄여 줄래요?").random()
        Violation.ZONE_SPEEDING -> "여기 아이들이 다니는 곳이에요. 살금살금 가요."
        Violation.HARSH_ACCEL -> listOf("어지러워요, 조금만 천천히 출발해요.", "으앗, 배기구가 매워요!").random()
        Violation.HARSH_BRAKE -> listOf("앗, 깜짝이야! 범퍼가 찌릿했어요.", "멈출 땐 사뿐히 부탁해요.").random()
        Violation.NO_SIGNAL_LANE_CHANGE -> "옆 차가 놀랐을 거예요. 깜빡이 먼저 켜 줘요."
        Violation.SEATBELT -> "안전벨트 안 매면 저 불안해요. 꼭 매 주세요."
    }

    fun bonus(b: BonusType): String = when (b) {
        BonusType.CRUISE -> "크루즈 모드 좋아요. 편안해요!"
        BonusType.CLEAN_30KM -> "30킬로미터 동안 아주 부드러웠어요. 저 기분 최고예요!"
    }

    fun greeting(stage: Stage?, born: Boolean): String = when {
        !born -> "알 속에서 듣고 있어요. 오늘도 부드럽게 달려 주세요."
        stage == Stage.ANGEL -> "안녕하세요! 오늘도 날개가 반짝반짝해요. 이대로만 가요."
        stage == Stage.GENTLE -> "안녕하세요! 오늘 목표는 급가속 없이 달리기예요."
        stage == Stage.NORMAL -> "출발해 볼까요? 오늘은 깜빡이 잘 켜기가 목표예요."
        stage == Stage.GRUMPY -> "흠, 오늘은 좀 살살 달려 줄 거죠?"
        else -> "으르릉… 오늘은 제발 천천히요. 다시 귀여워지고 싶어요."
    }

    fun mood(score: Int?, stage: Stage?, violationsToday: Int): String = when {
        score == null -> "아직 오늘은 안 달렸어요. 같이 나가요!"
        score >= 90 -> "오늘 기분 최고예요! 오늘 점수는 ${score}점이에요."
        score >= 75 -> "기분 좋아요. 오늘 점수는 ${score}점, 위반은 ${violationsToday}번이었어요."
        score >= 60 -> "그냥 그래요. 오늘 점수는 ${score}점이에요. 조금만 더 부드럽게 가요."
        else -> "조금 속상해요. 오늘 점수는 ${score}점, 위반이 ${violationsToday}번이나 있었어요."
    } + if (stage != null) " 지금 저는 ${stage.label} 단계예요." else ""

    fun change(from: Stage?, to: Stage): String = when {
        from == null -> "짜잔! 드디어 태어났어요. 저는 ${to.label} 붕붕이예요!"
        to.ordinal > from.ordinal -> "와, ${to.label} 단계로 진화했어요! 고마워요!"
        else -> "으으… ${to.label} 단계로 변해 버렸어요. 다음 주엔 다시 돌아가고 싶어요."
    }
}
