package com.bungbungi.app.logic

import com.bungbungi.app.logic.AvatarEngine.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestTest {

    private fun trip(day: Long, km: Double, vararg v: Pair<Violation, Int>, quests: List<Quest> = emptyList()) =
        TripRecord(day, day * DAY, day * DAY + 1_000, km, v.toMap(), 0, quests)

    @Test fun cleanTripEarnsAllTripQuests() {
        val q = Quests.tripQuests(10.0, emptyMap())
        assertEquals(Quest.entries.filter { it.scope == QuestScope.TRIP }.toSet(), q.toSet())
        assertEquals(13, Quests.points(q))
    }

    @Test fun violationOnlyBlocksItsOwnQuest() {
        val q = Quests.tripQuests(10.0, mapOf(Violation.HARSH_BRAKE to 1))
        assertTrue(Quest.SMOOTH_BRAKE !in q)
        assertTrue(Quest.STEADY_SPEED in q)
    }

    @Test fun shortTripEarnsNothing() {
        assertEquals(emptyList<Quest>(), Quests.tripQuests(2.0, emptyMap()))
    }

    @Test fun questPointsOnlyAdd() {
        val base = Scoring.tripScore(10.0, mapOf(Violation.SPEEDING to 1), 0)
        val withQuest = Scoring.tripScore(10.0, mapOf(Violation.SPEEDING to 1), 0, 4)
        assertEquals(base + 4, withQuest)
        assertEquals(100, Scoring.tripScore(10.0, emptyMap(), 0, 13)) // 100점 넘지 않음
    }

    @Test fun weekBrakeQuestAllowsUpToThree() {
        val ok = listOf(trip(1, 15.0, Violation.HARSH_BRAKE to 2), trip(2, 10.0, Violation.HARSH_BRAKE to 1))
        assertTrue(Quest.WEEK_BRAKE in Quests.weekQuests(ok))
        val bad = ok + trip(3, 5.0, Violation.HARSH_BRAKE to 1)
        assertTrue(Quest.WEEK_BRAKE !in Quests.weekQuests(bad))
    }

    @Test fun weekQuestsNeedDistance() {
        assertEquals(emptyList<Quest>(), Quests.weekQuests(listOf(trip(1, 10.0))))
        assertTrue(Quest.WEEK_CLEAN_50 in Quests.weekQuests(listOf(trip(1, 30.0), trip(2, 25.0))))
    }

    @Test fun weeklyQuestsLiftAvatarAndAreCounted() {
        // 2주 동안 82점 근처(순둥) → 주간 과제 가산점으로 천사 쪽으로 한 칸
        val trips = (0 until 14L).map { trip(it, 10.0, Violation.HARSH_ACCEL to 1) } // 하루 80점
        var s = AvatarEngine.evaluate(AvatarState(0), trips, 14 * DAY)
        assertEquals(Stage.GENTLE, s.stage)
        val more = trips + (14 until 21L).map { trip(it, 10.0, Violation.HARSH_ACCEL to 1) }
        s = AvatarEngine.evaluate(s, more, 21 * DAY)
        // 급감속 0, 과속 0 → 주간 과제 2개(+10점) → 90점 → 천사
        assertEquals(setOf(Quest.WEEK_BRAKE, Quest.WEEK_NO_SPEEDING), s.lastWeekQuests.toSet())
        assertEquals(Stage.ANGEL, s.stage)
        assertEquals(1, s.weeklyQuests[Quest.WEEK_BRAKE])
    }

    @Test fun titlesGrowWithQuestCount() {
        assertEquals("초보 운전자", Quests.tierFor(0).name)
        assertEquals("안전운전 새싹", Quests.tierFor(3).name)
        assertEquals("모범 드라이버", Quests.tierFor(12).name)
        assertEquals("도로 위의 신사", Quests.nextTier(12)?.name)
        val trips = (0 until 10L).map { trip(it, 10.0, quests = listOf(Quest.SMOOTH_BRAKE)) }
        val counts = Quests.counts(trips, mapOf(Quest.WEEK_BRAKE to 2))
        assertEquals(12, counts.values.sum())
        assertEquals(listOf("부드러운 제동왕"), Quests.specialTitles(counts))
    }
}
