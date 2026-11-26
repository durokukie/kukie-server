package com.duro.kukie.team.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.global.util.utcNow
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** 구성원이 함께 클러스터를 관리하는 최상위 협업 단위 (Slack 의 Workspace). */
// 바뀐 열만 쓴다. 이름을 고치는 요청이 먼저 읽어 둔 값으로 그사이 바뀐 보고서 일정·다음 회차를 덮지 않게 한다
@DynamicUpdate
@Entity
@Table(name = "tbl_team")
class Team(
    name: String,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(nullable = false, length = 50)
    var name = name
        protected set

    @Column(name = "report_enabled", nullable = false)
    var reportEnabled: Boolean = true
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "report_day", nullable = false, length = 10)
    var reportDay: DayOfWeek = DEFAULT_REPORT_DAY
        protected set

    @Column(name = "report_time", nullable = false)
    var reportTime: LocalTime = DEFAULT_REPORT_TIME
        protected set

    @Column(name = "report_zone", nullable = false, length = 50)
    var reportZone: String = DEFAULT_REPORT_ZONE
        protected set

    @Column(name = "report_next_at", nullable = false)
    var reportNextAt: LocalDateTime = nextReportAt(utcNow())
        protected set

    val reportZoneId: ZoneId
        get() = ZoneId.of(reportZone)

    fun rename(name: String) {
        this.name = name
    }

    fun changeReportSchedule(enabled: Boolean, day: DayOfWeek, time: LocalTime, zone: ZoneId, now: LocalDateTime) {
        reportEnabled = enabled
        reportDay = day
        reportTime = time.truncatedTo(ChronoUnit.MINUTES)
        reportZone = zone.id
        reportNextAt = nextReportAt(now)
    }

    /** 때가 됐으면 가장 최근 회차의 시각을 주고 다음 회차로 넘긴다. 밀린 회차는 건너뛴다. */
    fun takeDueReportSlot(now: LocalDateTime): LocalDateTime? {
        if (!reportEnabled || reportNextAt.isAfter(now)) return null

        val next = nextReportAt(now)
        reportNextAt = next

        return previousSlot(next)
    }

    private fun nextReportAt(after: LocalDateTime): LocalDateTime {
        val local = after.atOffset(ZoneOffset.UTC).atZoneSameInstant(reportZoneId)
        val sameWeek = local.with(TemporalAdjusters.nextOrSame(reportDay)).with(reportTime)
        val next = if (sameWeek.isAfter(local)) sameWeek else sameWeek.plusWeeks(1)

        return next.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    }

    private fun previousSlot(slot: LocalDateTime): LocalDateTime =
        slot.atOffset(ZoneOffset.UTC).atZoneSameInstant(reportZoneId).minusWeeks(1)
            .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

    companion object {
        val DEFAULT_REPORT_DAY = DayOfWeek.MONDAY
        val DEFAULT_REPORT_TIME: LocalTime = LocalTime.of(9, 0)
        const val DEFAULT_REPORT_ZONE = "Asia/Seoul"
    }
}
