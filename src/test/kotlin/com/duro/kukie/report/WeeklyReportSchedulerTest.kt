package com.duro.kukie.report

import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.report.application.CreateScheduledReportsService
import com.duro.kukie.report.application.ResumeStalledReportsService
import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportKind
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.domain.ReportStatus
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.team.presentation.dto.request.UpdateReportScheduleRequest
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class WeeklyReportSchedulerTest : IntegrationTest() {

    @Autowired
    private lateinit var createScheduledReportsService: CreateScheduledReportsService

    @Autowired
    private lateinit var resumeStalledReportsService: ResumeStalledReportsService

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var reportRepository: ReportRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `때가 된 팀의 Shared 대화가 있던 클러스터마다 주간 보고서를 만들고 다음 회차로 넘긴다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        val active = savedCluster(me.user.id, teamId, "dev")
        val privateOnly = savedCluster(me.user.id, teamId, "stage")
        savedCluster(me.user.id, teamId, "prod")
        savedRun(me.user.id, active.id, ChatVisibility.SHARED)
        savedRun(me.user.id, privateOnly.id, ChatVisibility.PRIVATE)
        val slot = teamRepository.findById(teamId).get().reportNextAt
        val notYetDue = savedTeam(me.user.id to TeamRole.ADMIN)
        savedRun(me.user.id, savedCluster(me.user.id, notYetDue, "other").id, ChatVisibility.SHARED)
        jdbc.update("update tbl_team set report_next_at = ? where id = ?", slot.plusDays(1), notYetDue)

        // when
        createScheduledReportsService(slot.plusSeconds(30))

        // then
        val report = reportRepository.findAll().single()
        report.kind shouldBe ReportKind.WEEKLY
        report.clusterId shouldBe active.id
        report.ownerId shouldBe null
        report.shared shouldBe true
        report.periodStart shouldBe slot.minusDays(7)
        report.periodEnd shouldBe slot
        report.timeZone shouldBe "Asia/Seoul"
        report.status shouldBe ReportStatus.READY
        teamRepository.findById(teamId).get().reportNextAt shouldBe slot.plusDays(7)
    }

    @Test
    fun `같은 회차를 다시 돌려도 보고서는 하나다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        savedRun(me.user.id, savedCluster(me.user.id, teamId).id, ChatVisibility.SHARED)
        val slot = teamRepository.findById(teamId).get().reportNextAt
        createScheduledReportsService(slot)
        jdbc.update("update tbl_team set report_next_at = ? where id = ?", slot, teamId)

        // when
        createScheduledReportsService(slot)

        // then
        reportRepository.count() shouldBe 1
        teamRepository.findById(teamId).get().reportNextAt shouldBe slot.plusDays(7)
    }

    @Test
    fun `자동으로 만든 주간 보고서가 완성되면 팀 구성원에게 알린다`() {
        // given
        val me = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        savedRun(me.user.id, savedCluster(me.user.id, teamId).id, ChatVisibility.SHARED)

        // when
        createScheduledReportsService(teamRepository.findById(teamId).get().reportNextAt)

        // then
        val report = reportRepository.findAll().single()
        listOf(me, member).forEach { user ->
            mockMvc.get("/inbox") {
                authorization(user.accessToken)
            }.andExpect {
                status { isOk() }
                jsonPath("$.notifications.length()") { value(1) }
                jsonPath("$.notifications[0].kind") { value("WEEKLY_REPORT_READY") }
                jsonPath("$.notifications[0].payload.reportId") { value(report.id.toString()) }
                jsonPath("$.notifications[0].payload.subject") { value(report.title) }
                jsonPath("$.notifications[0].payload.teamId") { value(teamId.toString()) }
            }
        }
    }

    @Test
    fun `관리자가 요일·시각·시간대를 바꾸면 다음 회차를 다시 계산한다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        val zone = ZoneId.of("America/New_York")
        val local = ZonedDateTime.now(zone)
        val sameWeek = local.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).with(LocalTime.of(18, 30))
        val next = (if (sameWeek.isAfter(local)) sameWeek else sameWeek.plusWeeks(1)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()

        // when & then
        mockMvc.put("/teams/$teamId/report-schedule") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateReportScheduleRequest(true, DayOfWeek.FRIDAY, LocalTime.of(18, 30, 45), "America/New_York").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(teamId.toString()) }
            jsonPath("$.role") { value(TeamRole.ADMIN.name) }
            jsonPath("$.reportSchedule.enabled") { value(true) }
            jsonPath("$.reportSchedule.day") { value("FRIDAY") }
            jsonPath("$.reportSchedule.time") { value("18:30:00") }
            jsonPath("$.reportSchedule.timeZone") { value("America/New_York") }
        }
        teamRepository.findById(teamId).get().reportNextAt shouldBe next
        mockMvc.get("/teams") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].reportSchedule.day") { value("FRIDAY") }
        }
    }

    @Test
    fun `관리자가 꺼 둔 팀은 때가 지나도 주간 보고서를 만들지 않는다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        savedRun(me.user.id, savedCluster(me.user.id, teamId).id, ChatVisibility.SHARED)

        // when
        mockMvc.put("/teams/$teamId/report-schedule") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateReportScheduleRequest(false, DayOfWeek.MONDAY, LocalTime.of(9, 0), "Asia/Seoul").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.reportSchedule.enabled") { value(false) }
            jsonPath("$.reportSchedule.day") { value("MONDAY") }
            jsonPath("$.reportSchedule.nextAt") { value(null) }
        }
        createScheduledReportsService(teamRepository.findById(teamId).get().reportNextAt.plusDays(1))

        // then
        reportRepository.count() shouldBe 0
    }

    @Test
    fun `모르는 시간대로는 일정을 바꿀 수 없다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.put("/teams/$teamId/report-schedule") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateReportScheduleRequest(true, DayOfWeek.FRIDAY, LocalTime.of(18, 30), "Mars/Olympus").toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(TeamErrorCode.INVALID_REPORT_ZONE.code) }
        }
        teamRepository.findById(teamId).get().reportDay shouldBe DayOfWeek.MONDAY
    }

    @Test
    fun `만들다 멈춘 보고서는 10분 뒤 다시 만들고, 만든 지 하루가 지난 것은 그대로 둔다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, savedTeam(me.user.id to TeamRole.ADMIN))
        val session = chatSessionRepository.save(ChatFixture.session(me.user.id, cluster.id))
        val stalled = reportRepository.save(Report.ofConversation(session, me.user.id, ZoneId.of("UTC")))
        val fresh = reportRepository.save(Report.ofConversation(session, me.user.id, ZoneId.of("UTC")))
        val abandoned = reportRepository.save(Report.ofConversation(session, me.user.id, ZoneId.of("UTC")))
        age(stalled.id, createdAgo = 11, updatedAgo = 11)
        age(abandoned.id, createdAgo = 60 * 25, updatedAgo = 11)

        // when
        resumeStalledReportsService()

        // then
        reportRepository.findById(stalled.id).get().status shouldBe ReportStatus.READY
        reportRepository.findById(fresh.id).get().status shouldBe ReportStatus.GENERATING
        reportRepository.findById(abandoned.id).get().status shouldBe ReportStatus.GENERATING
    }

    private fun age(reportId: UUID, createdAgo: Long, updatedAgo: Long) {
        val now = LocalDateTime.now()
        jdbc.update(
            "update tbl_report set created_at = ?, updated_at = ? where id = ?",
            now.minusMinutes(createdAgo), now.minusMinutes(updatedAgo), reportId,
        )
    }

    private fun savedCluster(registeredBy: UUID, teamId: UUID, name: String = ClusterFixture.DEFAULT_NAME): Cluster =
        clusterRepository.save(ClusterFixture.cluster(registeredBy = registeredBy, teamId = teamId, name = name))

    private fun savedRun(userId: UUID, clusterId: UUID, visibility: ChatVisibility) {
        val session = chatSessionRepository.save(ChatFixture.session(userId, clusterId, visibility = visibility))
        chatRunRepository.save(ChatFixture.run(session.id, status = RunStatus.COMPLETED, requestedBy = userId))
    }
}
