package com.duro.kukie.team.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TeamReportScheduleTest {

    // 화요일 12:00 KST
    private val now = LocalDateTime.parse("2026-11-24T03:00:00")
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `일정을 바꾸면 그 시간대에서 지금 뒤에 처음 오는 때가 다음 회차다`() {
        // given
        val team = Team("DURO")

        // when & then
        team.changeReportSchedule(true, DayOfWeek.TUESDAY, LocalTime.of(13, 0, 59), seoul, now)
        team.reportTime shouldBe LocalTime.of(13, 0)
        team.reportNextAt shouldBe LocalDateTime.parse("2026-11-24T04:00:00")

        team.changeReportSchedule(true, DayOfWeek.TUESDAY, LocalTime.of(12, 0), seoul, now)
        team.reportNextAt shouldBe LocalDateTime.parse("2026-12-01T03:00:00")

        team.changeReportSchedule(true, DayOfWeek.MONDAY, LocalTime.of(22, 0), ZoneId.of("America/New_York"), now)
        team.reportZone shouldBe "America/New_York"
        team.reportNextAt shouldBe LocalDateTime.parse("2026-12-01T03:00:00")
    }

    @Test
    fun `때가 되면 가장 최근 회차를 주고 다음 회차로 넘긴다`() {
        // given
        val team = Team("DURO").apply { changeReportSchedule(true, DayOfWeek.MONDAY, LocalTime.of(9, 0), seoul, now.minusWeeks(3)) }

        // when & then
        team.takeDueReportSlot(now) shouldBe LocalDateTime.parse("2026-11-23T00:00:00")
        team.reportNextAt shouldBe LocalDateTime.parse("2026-11-30T00:00:00")
        team.takeDueReportSlot(now) shouldBe null
    }

    @Test
    fun `꺼 두면 때가 지나도 회차를 주지 않고, 다시 켜면 다음 회차부터다`() {
        // given
        val team = Team("DURO").apply { changeReportSchedule(false, DayOfWeek.MONDAY, LocalTime.of(9, 0), seoul, now.minusWeeks(3)) }

        // when & then
        team.takeDueReportSlot(now) shouldBe null

        team.changeReportSchedule(true, DayOfWeek.MONDAY, LocalTime.of(9, 0), seoul, now)
        team.takeDueReportSlot(now) shouldBe null
        team.reportNextAt shouldBe LocalDateTime.parse("2026-11-30T00:00:00")
    }
}
