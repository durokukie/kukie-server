package com.duro.kukie.chat

import com.duro.kukie.actionplan.ActionPlanFixture
import com.duro.kukie.actionplan.ActionPlanFixture.approved
import com.duro.kukie.actionplan.ActionPlanFixture.executing
import com.duro.kukie.actionplan.ActionPlanFixture.offered
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

class InternalRunIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Test
    fun `요청 기록을 갱신하고 메시지를 이어 붙인다`() {
        // given
        val session = savedSession()
        val run = savedRun(session, messages = listOf(mapOf("kind" to "request", "parts" to listOf(mapOf("part_kind" to "user-prompt")))))

        // when & then
        patchRun(
            run,
            mapOf(
                "responsePayload" to mapOf("kind" to "approval", "approvals" to listOf(mapOf("call_id" to "call-1", "plan_id" to "p"))),
                "appendAgentMessages" to listOf(mapOf("kind" to "response", "parts" to listOf(mapOf("tool_call_id" to "call-1")))),
                "usageSummary" to mapOf("input_tokens" to 120, "output_tokens" to 30, "requests" to 2),
            ),
        ).andExpect {
            status { isOk() }
            jsonPath("$.run.id") { value(run.id.toString()) }
            jsonPath("$.run.status") { value("running") }
            jsonPath("$.run.finishedAt") { value(null) }
            jsonPath("$.run.responsePayload.approvals[0].call_id") { value("call-1") }
            jsonPath("$.run.agentMessages[0].parts[0].part_kind") { value("user-prompt") }
            jsonPath("$.run.agentMessages[1].parts[0].tool_call_id") { value("call-1") }
            jsonPath("$.run.usageSummary.input_tokens") { value(120) }
            jsonPath("$.closedPlans") { value(empty<Any>()) }
        }
        chatRunRepository.findByIdOrNull(run.id)!!.agentMessages!!.size shouldBe 2
    }

    @Test
    fun `NUL 문자가 섞인 출력도 저장한다`() {
        // given
        val run = savedRun(savedSession())

        // when & then
        patchRun(
            run,
            mapOf(
                "status" to "completed",
                "responsePayload" to mapOf("kind" to "answer", "text" to "로그\u0000끝"),
                "appendAgentMessages" to listOf(mapOf("content" to "log line\u0000tail", "na\u0000me" to listOf("\u0000"))),
                "usageSummary" to mapOf("note" to "\u0000"),
            ),
        ).andExpect {
            status { isOk() }
            jsonPath("$.run.status") { value("completed") }
            jsonPath("$.run.responsePayload.text") { value("로그\uFFFD끝") }
            jsonPath("$.run.agentMessages[0].content") { value("log line\uFFFDtail") }
        }

        // then
        val saved = chatRunRepository.findByIdOrNull(run.id)!!
        saved.status shouldBe RunStatus.COMPLETED
        saved.agentMessages shouldBe listOf(mapOf("content" to "log line\uFFFDtail", "na\uFFFDme" to listOf("\uFFFD")))
        saved.usageSummary shouldBe mapOf("note" to "\uFFFD")
    }

    @Test
    fun `요청이 끝나면 열린 계획을 닫는다`() {
        // given
        val session = savedSession()
        val run = savedRun(session, status = RunStatus.AWAITING_APPROVAL)
        val draft = actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-1"))
        val waiting = actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-2").offered())
        val approved = actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-3").approved(DECIDER))
        val executing = actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-4").executing(DECIDER))
        val applied = actionPlanRepository.save(
            ActionPlanFixture.plan(run.id, toolCallId = "call-5").executing(DECIDER).apply {
                recordResult(success = true, stdout = "ok", stderr = "", exitCode = 0)
            },
        )

        // when & then
        patchRun(run, mapOf("status" to "completed", "responsePayload" to mapOf("kind" to "answer"))).andExpect {
            status { isOk() }
            jsonPath("$.run.status") { value("completed") }
            jsonPath("$.run.finishedAt") { exists() }
            jsonPath("$.closedPlans[*].id") {
                value(contains(draft.id.toString(), waiting.id.toString(), approved.id.toString(), executing.id.toString()))
            }
            jsonPath("$.closedPlans[*].status") { value(contains("EXPIRED", "EXPIRED", "UNKNOWN", "UNKNOWN")) }
        }

        // then
        actionPlanRepository.findByIdOrNull(draft.id)!!.status shouldBe PlanStatus.EXPIRED
        actionPlanRepository.findByIdOrNull(waiting.id)!!.status shouldBe PlanStatus.EXPIRED
        actionPlanRepository.findByIdOrNull(approved.id)!!.status shouldBe PlanStatus.UNKNOWN
        actionPlanRepository.findByIdOrNull(executing.id)!!.status shouldBe PlanStatus.UNKNOWN
        actionPlanRepository.findByIdOrNull(applied.id)!!.status shouldBe PlanStatus.APPLIED
        chatRunRepository.findByIdOrNull(run.id)!!.finishedAt.shouldNotBeNull()
    }

    @Test
    fun `첫 메시지로 대화 제목을 짓는다`() {
        // given
        val session = savedSession()
        val run = savedRun(session, inputText = "web 디플로이먼트의   파드가 계속 재시작되는데 원인을 찾아 줘")

        // when
        patchRun(run, mapOf("status" to "completed", "titleFrom" to "web 디플로이먼트의   파드가 계속 재시작되는데 원인을 찾아 줘"))
            .andExpect { status { isOk() } }

        // then
        val after = chatSessionRepository.findByIdOrNull(session.id)!!
        after.title shouldBe "web 디플로이먼트의 파드가 계속 재시작되는데 원인을"
        after.version shouldBe session.version + 1
    }

    @Test
    fun `에이전트가 다시 뜨면 실행 중이던 요청만 중단한다`() {
        // given
        val running = savedRun(savedSession())
        val awaiting = savedRun(savedSession(), status = RunStatus.AWAITING_APPROVAL)
        val recovery = savedRun(savedSession(), status = RunStatus.RECOVERY_REQUIRED)
        val draft = actionPlanRepository.save(ActionPlanFixture.plan(running.id, code = "ap-a"))
        val waiting = actionPlanRepository.save(ActionPlanFixture.plan(awaiting.id, code = "ap-b").offered())

        // when & then
        mockMvc.post("/internal/runs/interrupt-orphaned") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("message" to "서버가 다시 떠서 중단됐다").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.interrupted") { value(1) }
        }

        // then
        val interrupted = chatRunRepository.findByIdOrNull(running.id)!!
        interrupted.status shouldBe RunStatus.INTERRUPTED
        interrupted.finishedAt.shouldNotBeNull()
        interrupted.responsePayload!!["code"] shouldBe "INTERRUPTED"
        actionPlanRepository.findByIdOrNull(draft.id)!!.status shouldBe PlanStatus.EXPIRED
        chatRunRepository.findByIdOrNull(awaiting.id)!!.status shouldBe RunStatus.AWAITING_APPROVAL
        chatRunRepository.findByIdOrNull(awaiting.id)!!.finishedAt.shouldBeNull()
        chatRunRepository.findByIdOrNull(recovery.id)!!.status shouldBe RunStatus.RECOVERY_REQUIRED
        actionPlanRepository.findByIdOrNull(waiting.id)!!.status shouldBe PlanStatus.WAITING_APPROVAL
    }

    private fun patchRun(run: ChatRun, body: Map<String, Any?>) =
        mockMvc.patch("/internal/runs/${run.id}") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = body.toJson()
        }

    private fun savedSession(): ChatSession =
        chatSessionRepository.save(ChatFixture.session(owner, clusterId()))

    private fun clusterId(): UUID =
        clusterRepository.save(
            ClusterFixture.cluster(registeredBy = owner, teamId = savedTeam(owner to TeamRole.ADMIN), name = "c-${UUID.randomUUID()}"),
        ).id

    private fun savedRun(
        session: ChatSession,
        turnNo: Int = 1,
        inputText: String? = "파드 목록 보여줘",
        kind: RunKind = RunKind.CHAT,
        status: RunStatus = RunStatus.RUNNING,
        messages: List<Any?>? = null,
    ): ChatRun = chatRunRepository.save(
        ChatFixture.run(session.id, turnNo = turnNo, inputText = inputText, kind = kind, status = status).apply {
            messages?.let(::appendAgentMessages)
        },
    )

    private val owner: UUID by lazy { loggedInUser().user.id }

    companion object {
        private val DECIDER: UUID = UUID.fromString("01900000-0000-7000-8000-0000000000aa")
    }
}
