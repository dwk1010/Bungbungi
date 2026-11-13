package com.bungbungi.app.logic

import com.bungbungi.app.logic.AvatarEngine.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 도전과제·호칭 경계값과 예외 상황 */
class QuestEdgeTest {

    private fun trip(day: Long, km: Double, vararg v: Pair<Violation, Int>, quests: List<Quest> = emptyList()) =
        TripRecord(day, day * DAY, day * DAY + 1_000, km, v.toMap(), 0, quests)

    @Test fun tripDistanceBoundary() {
        assertEquals(emptyList<Quest>(), Quests.tripQuests(2.99, emptyMap()))
        assertEquals(5, Quests.tripQuests(3.0, emptyMap()).size)
    }

    @Test fun roughTripEarnsNoQuests() {
        val rough = mapOf(
            Violation.SEATBELT to 1, Violation.HARSH_ACCEL to 3, Violation.SPEEDING to 2,
            Violation.NO_SIGNAL_LANE_CHANGE to 2, Violation.HARSH_BRAKE to 3, Violation.ZONE_SPEEDING to 1,
        )
        assertEquals(emptyList<Quest>(), Quests.tripQuests(10.0, rough))
    }

    @Test fun mixedTripOnlyLosesAffectedQuests() {
        // 급가속은 어떤 과제와도 상관없음 → 5개 모두 성공
        assertEquals(5, Quests.tripQuests(10.0, mapOf(Violation.HARSH_ACCEL to 5)).size)
        val q = Quests.tripQuests(10.0, mapOf(Violation.ZONE_SPEEDING to 1, Violation.SEATBELT to 1))
        assertEquals(setOf(Quest.SMOOTH_BRAKE, Quest.STEADY_SPEED, Quest.SIGNAL_KEEPER), q.toSet())
    }

    @Test fun zoneSpeedingBreaksWeeklyNoSpeeding() {
        val week = listOf(trip(1, 30.0, Violation.ZONE_SPEEDING to 1))
        assertTrue(Quest.WEEK_NO_SPEEDING !in Quests.weekQuests(week))
        assertTrue(Quest.WEEK_BRAKE in Quests.weekQuests(week))
        assertTrue(Quest.WEEK_CLEAN_50 !in Quests.weekQuests(week))
    }

    @Test fun clean50Boundary() {
        assertTrue(Quest.WEEK_CLEAN_50 !in Quests.weekQuests(listOf(trip(1, 49.9))))
        assertTrue(Quest.WEEK_CLEAN_50 in Quests.weekQuests(listOf(trip(1, 50.0))))
        // 위반이 한 번이라도 있으면 실패 (급가속처럼 다른 과제와 상관없는 위반도)
        assertTrue(Quest.WEEK_CLEAN_50 !in Quests.weekQuests(listOf(trip(1, 40.0), trip(2, 20.0, Violation.HARSH_ACCEL to 1))))
    }

    @Test fun weekDistanceBoundary() {
        assertEquals(emptyList<Quest>(), Quests.weekQuests(listOf(trip(1, 19.9))))
        assertEquals(setOf(Quest.WEEK_BRAKE, Quest.WEEK_NO_SPEEDING), Quests.weekQuests(listOf(trip(1, 20.0))).toSet())
    }

    @Test fun tierBoundaries() {
        val cases = mapOf(
            2 to "초보 운전자", 3 to "안전운전 새싹", 9 to "안전운전 새싹", 10 to "모범 드라이버",
            24 to "모범 드라이버", 25 to "도로 위의 신사", 49 to "도로 위의 신사", 50 to "베스트 드라이버",
            99 to "베스트 드라이버", 100 to "붕붕 레전드", 500 to "붕붕 레전드",
        )
        cases.forEach { (n, name) -> assertEquals("total=$n", name, Quests.tierFor(n).name) }
        assertEquals(null, Quests.nextTier(100))
        assertEquals("안전운전 새싹", Quests.nextTier(0)?.name)
    }

    @Test fun specialTitleNeedsTen() {
        val nine = (0 until 9L).map { trip(it, 10.0, quests = listOf(Quest.SIGNAL_KEEPER)) }
        assertEquals(emptyList<String>(), Quests.specialTitles(Quests.counts(nine, emptyMap())))
        val ten = nine + trip(9, 10.0, quests = listOf(Quest.SIGNAL_KEEPER))
        assertEquals(listOf("깜빡이 장인"), Quests.specialTitles(Quests.counts(ten, emptyMap())))
        // 주간 과제도 특별 호칭에 쳐줌
        assertEquals(listOf("클린 드라이버"), Quests.specialTitles(Quests.counts(emptyList(), mapOf(Quest.WEEK_CLEAN_50 to 10))))
    }

    @Test fun specialTitlesSortedByCount() {
        val counts = Quests.counts(emptyList(), mapOf(Quest.WEEK_BRAKE to 12, Quest.WEEK_CLEAN_50 to 20))
        assertEquals(listOf("클린 드라이버", "브레이크 마스터"), Quests.specialTitles(counts))
    }

    @Test fun restingWeekGivesNoWeeklyQuests() {
        val good = (0 until 14L).map { trip(it, 20.0) }
        var s = AvatarEngine.evaluate(AvatarState(0), good, 14 * DAY)
        s = AvatarEngine.evaluate(s, good + trip(15, 3.0), 21 * DAY)
        assertTrue(s.resting)
        assertEquals(emptyList<Quest>(), s.lastWeekQuests)
        assertEquals(emptyMap<Quest, Int>(), s.weeklyQuests)
    }

    @Test fun shortButActiveWeekGivesNoWeeklyQuests() {
        val good = (0 until 14L).map { trip(it, 20.0) }
        var s = AvatarEngine.evaluate(AvatarState(0), good, 14 * DAY)
        s = AvatarEngine.evaluate(s, good + trip(15, 12.0), 21 * DAY) // 5km 이상이라 쉬는 주는 아니지만 20km 미만
        assertTrue(!s.resting)
        assertEquals(emptyList<Quest>(), s.lastWeekQuests)
    }

    @Test fun weeklyQuestsAccumulateOverWeeks() {
        val trips = (0 until 35L).map { trip(it, 10.0) }
        val s = AvatarEngine.evaluate(AvatarState(0), trips, 35 * DAY) // 탄생 후 3주 평가
        assertEquals(3, s.weeklyQuests[Quest.WEEK_BRAKE])
        assertEquals(3, s.weeklyQuests[Quest.WEEK_CLEAN_50])
    }

    @Test fun questBonusStillMovesOnlyOneStep() {
        val bad = (0 until 14L).map { trip(it, 10.0, Violation.HARSH_ACCEL to 5) } // 0점 → 난폭
        var s = AvatarEngine.evaluate(AvatarState(0), bad, 14 * DAY)
        assertEquals(Stage.WILD, s.stage)
        val good = bad + (14 until 21L).map { trip(it, 30.0) }
        s = AvatarEngine.evaluate(s, good, 21 * DAY)
        assertEquals(Stage.GRUMPY, s.stage) // 가산점이 있어도 한 칸만
    }

    @Test fun questPointsNeverLowerScore() {
        Quest.entries.forEach { assertTrue(it.points > 0) }
        val noQuest = Scoring.tripScore(5.0, mapOf(Violation.SPEEDING to 3), 0, 0)
        val withQuest = Scoring.tripScore(5.0, mapOf(Violation.SPEEDING to 3), 0, 13)
        assertTrue(withQuest >= noQuest)
    }
}
