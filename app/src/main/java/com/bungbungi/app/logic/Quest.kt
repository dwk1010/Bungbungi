package com.bungbungi.app.logic

/** 도전과제는 주행 한 번마다 보는 것과 한 주 단위로 보는 것이 있다. */
enum class QuestScope(val label: String) { TRIP("주행 과제"), WEEK("주간 과제") }

/**
 * 긍정형 도전과제. 성공하면 가산점만 주고, 실패해도 감점은 없다.
 * (신호위반은 SDK로 신호등 상태를 알 수 없어 감지 가능한 과제로 대신한다.)
 */
enum class Quest(
    val label: String,
    val desc: String,
    val scope: QuestScope,
    val points: Int,
    /** 이 과제를 10번 달성하면 받는 특별 호칭 */
    val title: String,
) {
    SMOOTH_BRAKE("부드러운 제동", "한 번 주행에서 급감속 0회", QuestScope.TRIP, 3, "부드러운 제동왕"),
    STEADY_SPEED("정속 주행", "한 번 주행에서 과속 0회", QuestScope.TRIP, 3, "정속의 달인"),
    SIGNAL_KEEPER("깜빡이 지킴이", "한 번 주행에서 깜빡이 없는 차선변경 0회", QuestScope.TRIP, 3, "깜빡이 장인"),
    KIDS_GUARD("어린이 지킴이", "한 번 주행에서 보호구역 과속 0회", QuestScope.TRIP, 2, "어린이 수호자"),
    BELT_ON("안전벨트 착용", "한 번 주행에서 안전벨트 경고 0회", QuestScope.TRIP, 2, "안전벨트 모범생"),
    WEEK_BRAKE("급감속 3회 이내", "한 주 20km 이상 달리고 급감속 3회 이하", QuestScope.WEEK, 5, "브레이크 마스터"),
    WEEK_NO_SPEEDING("과속 없는 한 주", "한 주 20km 이상 달리고 과속·보호구역 과속 0회", QuestScope.WEEK, 5, "제한속도 수호자"),
    WEEK_CLEAN_50("무위반 50km", "한 주 동안 위반 없이 50km 이상", QuestScope.WEEK, 8, "클린 드라이버"),
}

/** 호칭 단계: 지금까지 달성한 도전과제 수로 정한다. */
data class Title(val name: String, val need: Int)

object Quests {
    /** 너무 짧은 주행으로 과제를 쉽게 쌓지 못하게 하는 최소 거리 */
    const val MIN_TRIP_KM = 3.0
    const val MIN_WEEK_KM = 20.0
    const val SPECIAL_TITLE_COUNT = 10

    val tiers = listOf(
        Title("초보 운전자", 0),
        Title("안전운전 새싹", 3),
        Title("모범 드라이버", 10),
        Title("도로 위의 신사", 25),
        Title("베스트 드라이버", 50),
        Title("붕붕 레전드", 100),
    )

    /** 주행 한 번이 끝났을 때 달성한 주행 과제 */
    fun tripQuests(distanceKm: Double, counts: Map<Violation, Int>): List<Quest> {
        if (distanceKm < MIN_TRIP_KM) return emptyList()
        fun none(v: Violation) = (counts[v] ?: 0) == 0
        return buildList {
            if (none(Violation.HARSH_BRAKE)) add(Quest.SMOOTH_BRAKE)
            if (none(Violation.SPEEDING)) add(Quest.STEADY_SPEED)
            if (none(Violation.NO_SIGNAL_LANE_CHANGE)) add(Quest.SIGNAL_KEEPER)
            if (none(Violation.ZONE_SPEEDING)) add(Quest.KIDS_GUARD)
            if (none(Violation.SEATBELT)) add(Quest.BELT_ON)
        }
    }

    /** 한 주 동안의 주행으로 달성한 주간 과제 */
    fun weekQuests(trips: List<TripRecord>): List<Quest> {
        val km = trips.sumOf { it.distanceKm }
        if (km < MIN_WEEK_KM) return emptyList()
        fun total(v: Violation) = trips.sumOf { it.counts[v] ?: 0 }
        return buildList {
            if (total(Violation.HARSH_BRAKE) <= 3) add(Quest.WEEK_BRAKE)
            if (total(Violation.SPEEDING) + total(Violation.ZONE_SPEEDING) == 0) add(Quest.WEEK_NO_SPEEDING)
            if (km >= 50.0 && trips.all { it.violationTotal == 0 }) add(Quest.WEEK_CLEAN_50)
        }
    }

    fun points(quests: List<Quest>): Int = quests.sumOf { it.points }

    /** 과제별 누적 달성 횟수 (주행 과제 + 주간 과제) */
    fun counts(trips: List<TripRecord>, weekly: Map<Quest, Int>): Map<Quest, Int> {
        val m = Quest.entries.associateWith { 0 }.toMutableMap()
        trips.forEach { t -> t.quests.forEach { q -> m[q] = m.getValue(q) + 1 } }
        weekly.forEach { (q, n) -> m[q] = m.getValue(q) + n }
        return m
    }

    fun tierFor(total: Int): Title = tiers.last { total >= it.need }
    fun nextTier(total: Int): Title? = tiers.firstOrNull { it.need > total }

    /** 10번 이상 달성한 과제의 특별 호칭 (많이 달성한 순) */
    fun specialTitles(counts: Map<Quest, Int>): List<String> =
        counts.filter { it.value >= SPECIAL_TITLE_COUNT }.entries.sortedByDescending { it.value }.map { it.key.title }
}
