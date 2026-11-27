package com.duro.kukie.report

import com.duro.kukie.actionplan.ActionPlanFixture
import com.duro.kukie.actionplan.ActionPlanFixture.approved
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.report.application.GenerateReportService
import com.duro.kukie.report.application.ReportQuota
import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.exception.ReportErrorCode
import com.duro.kukie.report.presentation.dto.request.UpdateReportRequest
import com.duro.kukie.support.FakeReportOutlineClient
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class ReportIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Autowired
    private lateinit var reportRepository: ReportRepository

    @Autowired
    private lateinit var outlineClient: FakeReportOutlineClient

    @Autowired
    private lateinit var generateReportService: GenerateReportService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `대화 보고서를 만들면 AI 가 고른 턴과 변경을 담아 저장한다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val session = savedSession(me.user.id, cluster.id, ChatVisibility.SHARED)
        savedRun(session.id, 1, "파드 목록 보여줘")
        val scaled = savedRun(session.id, 2, "3개로 늘려줘", requestedBy = me.user.id)
        actionPlanRepository.save(
            ActionPlanFixture.plan(scaled.id).approved(me.user.id).apply {
                start()
                recordResult(success = true, stdout = "deployment.apps/web scaled", stderr = "", exitCode = 0)
            },
        )
        outlineClient.answer = {
            ReportOutline("배포 수를 늘렸다.", listOf(ReportOutline.Task("배포 수 조정", "3개로 늘렸다.", listOf(ReportOutline.TurnRef(session.id, 2)), emptyList())))
        }

        // when
        val reportId = createConversationReport(me, session.id, tz = "Asia/Seoul")

        // then
        mockMvc.get("/reports/$reportId") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.kind") { value("CONVERSATION") }
            jsonPath("$.status") { value("READY") }
            jsonPath("$.clusterId") { value(cluster.id.toString()) }
            jsonPath("$.conversationId") { value(session.id.toString()) }
            jsonPath("$.ownerId") { value(me.user.id.toString()) }
            jsonPath("$.shared") { value(true) }
            jsonPath("$.title") { value(ChatSession.DEFAULT_TITLE) }
            jsonPath("$.aiRefined") { value(true) }
            jsonPath("$.throughTurn") { value(2) }
            jsonPath("$.timeZone") { value("Asia/Seoul") }
            jsonPath("$.version") { value(1) }
            jsonPath("$.content") { value(startsWith("# ${ChatSession.DEFAULT_TITLE}\n")) }
            jsonPath("$.content") { value(containsString("(Asia/Seoul) · ${me.user.name} · 변경 1건 (적용 1)\n\n## 요약\n\n배포 수를 늘렸다.\n")) }
            jsonPath("$.content") { value(containsString("## 한 일\n\n- **배포 수 조정**: 3개로 늘렸다.\n")) }
            jsonPath("$.content") { value(containsString("| ${me.user.name} | scale `deployment/web` | caution | ${me.user.name} 승인 | 적용 | - |")) }
        }
        mockMvc.get("/reports") {
            authorization(me.accessToken)
            param("clusterId", cluster.id.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(reportId.toString()) }
            jsonPath("$[0].status") { value("READY") }
            jsonPath("$[0].content") { doesNotExist() }
        }
    }

    @Test
    fun `AI 를 부르지 못해도 변경이 있는 턴만 담아 보고서를 만든다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        savedRun(session.id, 1, "파드 목록 보여줘")
        actionPlanRepository.save(ActionPlanFixture.plan(savedRun(session.id, 2, "3개로 늘려줘").id).approved(me.user.id))
        outlineClient.failing = true

        // when
        val reportId = createConversationReport(me, session.id)

        // then
        mockMvc.get("/reports/$reportId") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("READY") }
            jsonPath("$.aiRefined") { value(false) }
            jsonPath("$.shared") { value(false) }
            jsonPath("$.timeZone") { value("Asia/Seoul") }
            jsonPath("$.content") { value(containsString("## 한 일\n\n- ${ActionPlanFixture.INTENT} (진행 중)\n")) }
            jsonPath("$.content") { value(containsString("## 남은 일\n\n- 진행 중: ${ActionPlanFixture.INTENT}\n")) }
            jsonPath("$.content") { value(not(containsString("파드 목록 보여줘"))) }
        }
    }

    @Test
    fun `끝난 턴이 그대로면 보고서를 새로 만들지 않고 앞의 것을 돌려준다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        savedRun(session.id, 1, "파드 목록 보여줘")
        val first = createConversationReport(me, session.id)

        // when & then
        mockMvc.post("/conversations/${session.id}/reports") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(first.toString()) }
            jsonPath("$.status") { value("READY") }
        }
        reportRepository.count() shouldBe 1
    }

    @Test
    fun `턴이 늘면 보고서를 새로 만든다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        savedRun(session.id, 1, "파드 목록 보여줘")
        val first = createConversationReport(me, session.id)
        savedRun(session.id, 2, "3개로 늘려줘")

        // when
        val second = createConversationReport(me, session.id)

        // then
        second shouldNotBe first
    }

    @Test
    fun `진행 중이던 턴이 끝나면 보고서를 새로 만든다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        savedRun(session.id, 1, "파드 목록 보여줘")
        chatRunRepository.save(ChatFixture.run(session.id, turnNo = 2, status = RunStatus.AWAITING_APPROVAL))
        val first = createConversationReport(me, session.id)
        jdbc.update("update tbl_chat_run set status = 'completed' where session_id = ? and turn_no = 2", session.id)

        // when
        val second = createConversationReport(me, session.id)

        // then
        second shouldNotBe first
        reportRepository.findById(first).get().throughTurn shouldBe 1
    }

    @Test
    fun `한 사람이 한 시간에 만들 수 있는 보고서 수를 넘으면 거절한다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        repeat(ReportQuota.HOURLY_LIMIT) { createConversationReport(me, savedSession(me.user.id, cluster.id).id) }
        val session = savedSession(me.user.id, cluster.id)

        // when & then
        mockMvc.post("/conversations/${session.id}/reports") {
            authorization(me.accessToken)
        }.andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value(ReportErrorCode.REPORT_TOO_MANY.code) }
        }
        reportRepository.count() shouldBe ReportQuota.HOURLY_LIMIT.toLong()
    }

    @Test
    fun `만드는 사이 대화가 지워진 보고서는 남기지 않는다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        val report = reportRepository.save(Report.ofConversation(session, me.user.id, ZoneId.of("UTC")))
        chatSessionRepository.delete(session)

        // when
        generateReportService(report.id)

        // then
        reportRepository.existsById(report.id) shouldBe false
    }

    @Test
    fun `클러스터의 최근 7일 Shared 대화로 주간 보고서를 직접 만든다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val shared = savedSession(me.user.id, cluster.id, ChatVisibility.SHARED)
        val old = savedRun(shared.id, 1, "지난달 점검")
        jdbc.update("update tbl_chat_run set started_at = ? where id = ?", utcNow().minusDays(8), old.id)
        savedRun(shared.id, 2, "api 가 왜 죽어?")
        savedRun(savedSession(me.user.id, cluster.id).id, 1, "혼자 본 것")

        // when
        val response = mockMvc.post("/clusters/${cluster.id}/weekly-reports") {
            authorization(me.accessToken)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.kind") { value("WEEKLY") }
            jsonPath("$.status") { value("GENERATING") }
            jsonPath("$.shared") { value(true) }
            jsonPath("$.ownerId") { value(me.user.id.toString()) }
            jsonPath("$.title") { value(startsWith("${cluster.name} 주간 보고서 (")) }
        }.andReturn().response.contentAsString

        // then
        val source = outlineClient.sources.single()
        source.conversations.map { it.conversation.id } shouldContainExactly listOf(shared.id)
        source.conversations.single().turns.map { it.seq } shouldContainExactly listOf(2)
        val content = reportRepository.findById(UUID.fromString(JsonPath.read(response, "$.id"))).get().content!!
        content shouldContain "· 대화 1개 · 변경 없음\n\n## 운영 지표\n\n클러스터에서 Prometheus 를 찾지 못해 지표를 싣지 못함.\n\n변경이나 기록할 작업 없음.\n"
    }

    @Test
    fun `자동으로 만든 주간 보고서가 있으면 직접 만드는 보고서는 그 뒤부터 담는다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val shared = savedSession(me.user.id, cluster.id, ChatVisibility.SHARED)
        val covered = savedRun(shared.id, 1, "지난 회차에 담긴 것")
        jdbc.update("update tbl_chat_run set started_at = ? where id = ?", utcNow().minusDays(5), covered.id)
        savedRun(shared.id, 2, "api 가 왜 죽어?")
        val zone = ZoneId.of("Asia/Seoul")
        val lastEnd = utcNow().minusDays(3).truncatedTo(ChronoUnit.SECONDS)
        reportRepository.save(Report.weekly(cluster, ownerId = null, Report.weekBefore(lastEnd, zone), lastEnd, zone))

        // when
        val response = mockMvc.post("/clusters/${cluster.id}/weekly-reports") {
            authorization(me.accessToken)
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        // then
        reportRepository.findById(UUID.fromString(JsonPath.read(response, "$.id"))).get().periodStart shouldBe lastEnd
        outlineClient.sources.single().conversations.single().turns.map { it.seq } shouldContainExactly listOf(2)
    }

    @Test
    fun `마지막 자동 주간 보고서가 일주일보다 오래됐으면 최근 일주일만 담는다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val zone = ZoneId.of("Asia/Seoul")
        val lastEnd = utcNow().minusDays(10).truncatedTo(ChronoUnit.SECONDS)
        reportRepository.save(Report.weekly(cluster, ownerId = null, Report.weekBefore(lastEnd, zone), lastEnd, zone))

        // when
        val response = mockMvc.post("/clusters/${cluster.id}/weekly-reports") {
            authorization(me.accessToken)
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        // then
        val report = reportRepository.findById(UUID.fromString(JsonPath.read(response, "$.id"))).get()
        report.periodStart shouldBe Report.weekBefore(report.periodEnd!!, zone)
    }

    @Test
    fun `보고서를 고치면 version 이 오르고 낡은 version 으로는 저장할 수 없다`() {
        // given
        val me = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        val session = savedSession(me.user.id, savedCluster(me.user.id, teamId).id, ChatVisibility.SHARED)
        val reportId = createConversationReport(me, session.id)

        // when & then
        mockMvc.put("/reports/$reportId") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateReportRequest("고친 제목", "# 고친 본문", version = 1).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("고친 제목") }
            jsonPath("$.content") { value("# 고친 본문") }
            jsonPath("$.version") { value(2) }
            jsonPath("$.editedBy") { value(member.user.id.toString()) }
        }
        mockMvc.put("/reports/$reportId") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateReportRequest("늦은 제목", "# 늦은 본문", version = 1).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ReportErrorCode.REPORT_CHANGED.code) }
        }
        reportRepository.findById(reportId).get().title shouldBe "고친 제목"
    }

    @Test
    fun `만드는 중인 보고서는 고칠 수 없다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        val generating = reportRepository.save(Report.ofConversation(session, me.user.id, ZoneId.of("UTC")))

        // when & then
        mockMvc.put("/reports/${generating.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateReportRequest("제목", "본문", version = 1).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ReportErrorCode.REPORT_NOT_READY.code) }
        }
    }

    @Test
    fun `Private 대화의 보고서는 만든 사람만 본다`() {
        // given
        val me = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(me.user.id to TeamRole.MEMBER, member.user.id to TeamRole.ADMIN)
        val session = savedSession(me.user.id, savedCluster(member.user.id, teamId).id)
        val reportId = createConversationReport(me, session.id)

        // when & then
        mockMvc.get("/reports/$reportId") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ReportErrorCode.REPORT_NOT_FOUND.code) }
        }
        mockMvc.put("/reports/$reportId") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateReportRequest("제목", "본문", version = 1).toJson()
        }.andExpect {
            status { isNotFound() }
        }
        mockMvc.get("/reports") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$") { value(empty<Any>()) }
        }
        mockMvc.get("/reports") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(reportId.toString()) }
        }
    }

    @Test
    fun `대화를 지워도 보고서는 남는다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        val reportId = createConversationReport(me, session.id)

        // when
        mockMvc.delete("/conversations/${session.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isNoContent() }
        }

        // then
        mockMvc.get("/reports/$reportId") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("READY") }
            jsonPath("$.conversationId") { value(session.id.toString()) }
        }
    }

    @Test
    fun `Shared 보고서는 만든 사람이나 팀 관리자가 지운다`() {
        // given
        val admin = loggedInUser()
        val owner = loggedInUser(UserFixture.user(email = "owner@example.com"))
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, owner.user.id to TeamRole.MEMBER, member.user.id to TeamRole.MEMBER)
        val clusterId = savedCluster(admin.user.id, teamId).id
        val mine = createConversationReport(owner, savedSession(owner.user.id, clusterId, ChatVisibility.SHARED).id)
        val others = createConversationReport(owner, savedSession(owner.user.id, clusterId, ChatVisibility.SHARED).id)

        // when & then
        mockMvc.delete("/reports/$mine") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        mockMvc.delete("/reports/$mine") {
            authorization(owner.accessToken)
        }.andExpect {
            status { isNoContent() }
        }
        mockMvc.delete("/reports/$others") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNoContent() }
        }
        reportRepository.count() shouldBe 0
    }

    @Test
    fun `클러스터나 팀을 지우면 그 보고서도 지워진다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        val deleted = savedCluster(me.user.id, teamId, name = "dev")
        val kept = savedCluster(me.user.id, teamId, name = "prod")
        createConversationReport(me, savedSession(me.user.id, deleted.id).id)
        createConversationReport(me, savedSession(me.user.id, kept.id).id)

        // when & then
        mockMvc.delete("/clusters/${deleted.id}") {
            authorization(me.accessToken)
            param("deleteConversations", "true")
        }.andExpect {
            status { isNoContent() }
        }
        reportRepository.findAll().map { it.clusterId } shouldContainExactly listOf(kept.id)

        mockMvc.delete("/teams/$teamId") {
            authorization(me.accessToken)
        }.andExpect {
            status { isNoContent() }
        }
        reportRepository.count() shouldBe 0
    }

    @Test
    fun `탈퇴하면 개인 보고서만 지우고 팀이 보는 보고서는 남긴다`() {
        // given
        val me = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)
        val cluster = savedCluster(admin.id, teamId)
        createConversationReport(me, savedSession(me.user.id, cluster.id).id)
        val shared = createConversationReport(me, savedSession(me.user.id, cluster.id, ChatVisibility.SHARED).id)

        // when
        mockMvc.delete("/users/me") {
            authorization(me.accessToken)
        }.andExpect {
            status { isNoContent() }
        }

        // then
        reportRepository.findAll().map { it.id } shouldContainExactly listOf(shared)
    }

    private fun createConversationReport(user: LoggedInUser, conversationId: UUID, tz: String? = null): UUID {
        val response = mockMvc.post("/conversations/$conversationId/reports") {
            authorization(user.accessToken)
            tz?.let { param("tz", it) }
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("GENERATING") }
            jsonPath("$.content") { value(null) }
        }.andReturn().response.contentAsString

        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    private fun savedCluster(
        registeredBy: UUID,
        teamId: UUID = savedTeam(registeredBy to TeamRole.ADMIN),
        name: String = ClusterFixture.DEFAULT_NAME,
    ): Cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = registeredBy, teamId = teamId, name = name))

    private fun savedSession(userId: UUID, clusterId: UUID, visibility: ChatVisibility = ChatVisibility.PRIVATE): ChatSession =
        chatSessionRepository.save(ChatFixture.session(userId, clusterId, visibility = visibility))

    private fun savedRun(sessionId: UUID, turnNo: Int, inputText: String, requestedBy: UUID? = null): ChatRun =
        chatRunRepository.save(
            ChatFixture.run(sessionId, turnNo = turnNo, inputText = inputText, status = RunStatus.COMPLETED, requestedBy = requestedBy),
        )
}
