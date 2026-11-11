package com.bungbungi.app.logic

/** 아바타 5단계 (낮은 것부터). */
enum class Stage(val label: String, val minScore: Int, val look: String) {
    WILD("난폭", 0, "불꽃 눈, 날카로운 이빨, 연기, 찌그러진 범퍼"),
    GRUMPY("까칠", 40, "찡그린 눈썹, 흐린 색, 작은 흠집"),
    NORMAL("보통", 60, "무표정, 기본 색"),
    GENTLE("순둥", 75, "둥글고 웃는 눈, 밝은 색"),
    ANGEL("천사", 90, "반짝이는 눈, 헤드라이트 하트, 파스텔 색, 날개");

    companion object {
        fun forScore(score: Double): Stage = entries.last { score >= it.minScore }
    }
}

/** 아바타 상태 (저장 대상) */
data class AvatarState(
    val installMillis: Long,
    val born: Boolean = false,
    val stage: Stage = Stage.NORMAL,
    val part: AvatarPart = AvatarPart.NONE,
    val lastEvalMillis: Long = installMillis,
    val resting: Boolean = false,
    val collected: Set<Stage> = emptySet(),
    /** 마지막 단계 변화. 화면에서 진화·퇴화 연출을 한 번 보여주고 지운다. */
    val lastChange: StageChange? = null,
)

data class StageChange(val from: Stage?, val to: Stage, val atMillis: Long) {
    val isBirth get() = from == null
    val isUp get() = from != null && to.ordinal > from.ordinal
}

/**
 * 기획서의 아바타 주기:
 * - 설치 후 2주 동안은 알. 2주가 되면 최근 2주 점수로 첫 아바타가 태어난다.
 * - 이후 1주마다 다시 정한다. 최근 2주 거리 가중 평균 점수 기준, 한 번에 한 칸씩만 움직인다.
 * - 그 주에 5km 미만이면 단계는 그대로 두고 '쉬는 중'.
 */
object AvatarEngine {
    const val DAY = 24L * 60 * 60 * 1000
    const val HATCH_DAYS = 14L
    const val CYCLE_DAYS = 7L
    const val WINDOW_DAYS = 14L
    const val MIN_WEEK_KM = 5.0

    fun evaluate(state: AvatarState, trips: List<TripRecord>, now: Long): AvatarState {
        var s = state
        if (!s.born) {
            val hatchAt = s.installMillis + HATCH_DAYS * DAY
            if (now < hatchAt) return s
            val window = tripsIn(trips, hatchAt - WINDOW_DAYS * DAY, hatchAt)
            val score = Scoring.weightedScore(window) ?: 75.0 // 거의 안 달렸으면 순둥이로 시작
            val stage = Stage.forScore(score)
            s = s.copy(
                born = true, stage = stage, lastEvalMillis = hatchAt,
                part = partFor(stage, window), collected = s.collected + stage,
                lastChange = StageChange(null, stage, hatchAt), resting = false,
            )
        }
        while (now >= s.lastEvalMillis + CYCLE_DAYS * DAY) {
            val evalAt = s.lastEvalMillis + CYCLE_DAYS * DAY
            val week = tripsIn(trips, evalAt - CYCLE_DAYS * DAY, evalAt)
            if (week.sumOf { it.distanceKm } < MIN_WEEK_KM) {
                s = s.copy(lastEvalMillis = evalAt, resting = true)
                continue
            }
            val window = tripsIn(trips, evalAt - WINDOW_DAYS * DAY, evalAt)
            val target = Stage.forScore(Scoring.weightedScore(window) ?: s.stage.minScore.toDouble())
            val next = when {
                target.ordinal > s.stage.ordinal -> Stage.entries[s.stage.ordinal + 1]
                target.ordinal < s.stage.ordinal -> Stage.entries[s.stage.ordinal - 1]
                else -> s.stage
            }
            s = s.copy(
                stage = next, lastEvalMillis = evalAt, resting = false,
                part = partFor(next, window), collected = s.collected + next,
                lastChange = if (next != s.stage) StageChange(s.stage, next, evalAt) else s.lastChange,
            )
        }
        return s
    }

    /** 현재 진행 중인 2주 점수 (알 색깔, 다음 단계 예상에 사용) */
    fun currentScore(trips: List<TripRecord>, now: Long): Double? =
        Scoring.weightedScore(tripsIn(trips, now - WINDOW_DAYS * DAY, now + 1))

    fun nextEvalMillis(state: AvatarState): Long =
        if (!state.born) state.installMillis + HATCH_DAYS * DAY else state.lastEvalMillis + CYCLE_DAYS * DAY

    private fun partFor(stage: Stage, trips: List<TripRecord>): AvatarPart =
        if (stage.ordinal >= Stage.NORMAL.ordinal) AvatarPart.NONE
        else Scoring.dominantViolation(trips)?.part ?: AvatarPart.NONE

    private fun tripsIn(trips: List<TripRecord>, from: Long, to: Long) =
        trips.filter { it.endMillis in from until to }
}
