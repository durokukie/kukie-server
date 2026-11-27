package com.duro.kukie.report.domain

import com.duro.kukie.cluster.ClusterFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class ReportTest {

    @Test
    fun `주간 보고서는 서머타임이 끝나는 주에도 그 시간대의 일주일을 담는다`() {
        // given
        val cluster = ClusterFixture.cluster(registeredBy = UUID.randomUUID(), teamId = UUID.randomUUID())
        val mondayNineEst = LocalDateTime.parse("2026-11-02T14:00:00")
        val zone = ZoneId.of("America/New_York")

        // when
        val report = Report.weekly(cluster, ownerId = null, Report.weekBefore(mondayNineEst, zone), mondayNineEst, zone)

        // then
        report.periodStart shouldBe LocalDateTime.parse("2026-10-26T13:00:00")
        report.periodEnd shouldBe mondayNineEst
        report.title shouldBe "${ClusterFixture.DEFAULT_NAME} 주간 보고서 (2026-10-26 ~ 2026-11-02)"
    }
}
