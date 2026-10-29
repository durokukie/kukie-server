package com.duro.kukie.actionplan

import com.duro.kukie.actionplan.ActionPlanFixture.approved
import com.duro.kukie.actionplan.ActionPlanFixture.executing
import com.duro.kukie.actionplan.ActionPlanFixture.offered
import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.DryRunStatus
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.actionplan.exception.ActionPlanErrorCode
import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.util.UUID

class InternalActionPlanIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Test
    fun `계획을 만들면 초안 상태로 표시용 코드를 붙인다`() {
        // given
        val run = savedRun()

        // when
        val response = createPlan(run, toolCallId = "call-1").andExpect {
            status { isCreated() }
            jsonPath("$.code") { value(matchesPattern("ap-\\d{6}-\\d{4}-scale_resource")) }
            jsonPath("$.runId") { value(run.id.toString()) }
            jsonPath("$.toolCallId") { value("call-1") }
            jsonPath("$.toolName") { value(ActionPlanFixture.TOOL) }
            jsonPath("$.status") { value("DRAFT") }
            jsonPath("$.risk") { value("caution") }
            jsonPath("$.requestHash") { value("h".repeat(64)) }
            jsonPath("$.decision") { value(null) }
            jsonPath("$.executionResult") { value(null) }
            jsonPath("$.failureReason") { value(null) }
            jsonPath("$.appliedAt") { value(null) }
            jsonPath("$.payload.created_at") { value("2026-10-14T09:00:00.123456+00:00") }
            jsonPath("$.payload.args.replicas") { value(3) }
            jsonPath("$.payload.target.resources[0].kind") { value("deployment") }
            jsonPath("$.payload.expected_effects[0]") { value("파드가 3개가 된다") }
            jsonPath("$.payload.dry_run_result") { value(null) }
            jsonPath("$.payload.decision_guidance") { value(null) }
        }.andReturn().response.contentAsString

        // then
        val saved = actionPlanRepository.findByIdOrNull(UUID.fromString(JsonPath.read(response, "$.id")))!!
        saved.payload.keys shouldBe ActionPlanFixture.payload().keys + setOf("dry_run_result", "decision_guidance")
    }

    @Test
    fun `같은 도구 호출로 다시 만들면 기존 계획을 돌려준다`() {
        // given
        val run = savedRun()
        val first = JsonPath.read<String>(createPlan(run, toolCallId = "call-1").andReturn().response.contentAsString, "$.id")

        // when & then
        createPlan(run, toolCallId = "call-1").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(first) }
        }
        actionPlanRepository.count() shouldBe 1
    }

    @Test
    fun `예행에 실패하면 계획을 실패로 닫는다`() {
        // given
        val plan = savedPlan()

        // when & then
        action(plan, "dry-run", mapOf("status" to "failed", "stdout" to "", "stderr" to "forbidden")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.failureReason") { value("DRY_RUN_FAILED") }
            jsonPath("$.payload.dry_run_result.status") { value("failed") }
        }
    }

    @Test
    fun `예행을 통과한 계획을 승인 카드로 내놓는다`() {
        // given
        val plan = savedPlan { it.apply { recordDryRun(DryRunStatus.UNSUPPORTED, "", "") } }

        // when & then
        action(plan, "offer", mapOf("decisionGuidance" to "  되돌리려면 replicas 를 1 로  ")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("WAITING_APPROVAL") }
            jsonPath("$.payload.decision_guidance") { value("되돌리려면 replicas 를 1 로") }
        }
    }

    @Test
    fun `승인 전에 연 PR 을 승인 카드와 함께 남긴다`() {
        // given
        val plan = savedPlan { it.apply { recordDryRun(DryRunStatus.SUCCEEDED, "diff", "") } }

        // when & then
        action(
            plan,
            "offer",
            mapOf(
                "decisionGuidance" to "PR 을 닫으면 되돌린다",
                "pullRequest" to mapOf("number" to 12, "url" to "https://github.com/acme/manifests/pull/12"),
            ),
        ).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("WAITING_APPROVAL") }
            jsonPath("$.payload.pull_request.number") { value(12) }
            jsonPath("$.payload.pull_request.url") { value("https://github.com/acme/manifests/pull/12") }
        }
    }

    @Test
    fun `승인하면 결정한 사람과 시각을 남긴다`() {
        // given
        val plan = savedPlan { it.offered() }

        // when & then
        action(plan, "decision", decisionBody(approved = true)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.decision.approved") { value(true) }
            jsonPath("$.decision.user_id") { value(DECIDER.toString()) }
            jsonPath("$.decision.at") { value(matchesPattern(AT)) }
        }
    }

    @Test
    fun `표기만 다른 같은 호출이면 승인한다`() {
        // given
        val plan = savedPlan { it.offered() }
        val call = callBody() + mapOf(
            "args" to linkedMapOf("replicas" to 3.0, "name" to "web", "kind" to "deployment"),
            "target" to linkedMapOf(
                "resources" to listOf(mapOf("name" to "web", "kind" to "deployment")),
                "namespace" to "web",
                "context" to "prod",
            ),
        )

        // when & then
        action(plan, "decision", decisionBody(approved = true, call = call)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
        }
    }

    @Test
    fun `거절한 계획은 다시 거절해도 그대로다`() {
        // given
        val plan = savedPlan { it.offered() }

        // when
        val first = action(plan, "decision", decisionBody(approved = false)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.decision.approved") { value(false) }
        }.andReturn().response.contentAsString

        // then
        action(plan, "decision", decisionBody(approved = false)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.decision.at") { value(JsonPath.read<String>(first, "$.decision.at")) }
        }
    }

    @Test
    fun `계획과 다른 호출은 승인할 수 없다`() {
        // given
        val plan = savedPlan { it.offered() }
        val call = callBody() + mapOf(
            "args" to ActionPlanFixture.ARGS + ("replicas" to 30),
            "command" to ActionPlanFixture.COMMAND.reversed(),
        )

        // when & then
        action(plan, "decision", decisionBody(approved = true, call = call)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ActionPlanErrorCode.PLAN_CALL_MISMATCH.code) }
            jsonPath("$.message") { value("승인하려는 호출이 계획과 다릅니다: args, command") }
        }
        actionPlanRepository.findByIdOrNull(plan.id)!!.status shouldBe PlanStatus.WAITING_APPROVAL
    }

    @Test
    fun `실행에 성공하면 적용 시각을 남긴다`() {
        // given
        val plan = savedPlan { it.executing(DECIDER) }

        // when & then
        action(plan, "result", mapOf("success" to true, "stdout" to "scaled", "stderr" to "", "exitCode" to 0)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPLIED") }
            jsonPath("$.failureReason") { value(null) }
            jsonPath("$.appliedAt") { exists() }
            jsonPath("$.executionResult.success") { value(true) }
            jsonPath("$.executionResult.stdout") { value("scaled") }
            jsonPath("$.executionResult.exit_code") { value(0) }
            jsonPath("$.executionResult.at") { value(matchesPattern(AT)) }
        }
        actionPlanRepository.findByIdOrNull(plan.id)!!.appliedAt.shouldNotBeNull()
    }

    @Test
    fun `PR 을 머지했으면 머지 커밋과 sync 결과를 실행 결과에 남긴다`() {
        // given
        val plan = savedPlan { it.executing(DECIDER) }

        // when & then
        action(
            plan,
            "result",
            mapOf(
                "success" to true,
                "stdout" to "merged",
                "stderr" to "",
                "exitCode" to 0,
                "mergeCommit" to " abc123 ",
                "syncResult" to mapOf("status" to "Synced", "health" to "Healthy"),
            ),
        ).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPLIED") }
            jsonPath("$.executionResult.merge_commit") { value("abc123") }
            jsonPath("$.executionResult.sync_result.status") { value("Synced") }
        }
    }

    @Test
    fun `실행에 실패하면 계획을 실패로 닫는다`() {
        // given
        val failed = savedPlan { it.executing(DECIDER) }
        val nonZero = savedPlan("call-2") { it.approved(DECIDER) }

        // when & then
        action(failed, "result", mapOf("success" to false, "stdout" to "", "stderr" to "forbidden", "exitCode" to 1)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.failureReason") { value("EXECUTION_FAILED") }
            jsonPath("$.appliedAt") { value(null) }
        }
        action(nonZero, "result", mapOf("success" to true, "stdout" to "", "stderr" to "", "exitCode" to null)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.executionResult.exit_code") { value(null) }
        }
    }

    @Test
    fun `결과를 모르는 채 끝난 실행은 알 수 없음으로 닫는다`() {
        // given
        val plan = savedPlan { it.executing(DECIDER) }

        // when & then
        action(plan, "unknown").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UNKNOWN") }
        }
    }

    @Test
    fun `지금 상태에서 할 수 없는 동작은 거부한다`() {
        // given
        val plan = savedPlan()

        // when & then
        action(plan, "start").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ActionPlanErrorCode.PLAN_STATE_CONFLICT.code) }
            jsonPath("$.message") { value("지금 상태(DRAFT)에서는 할 수 없는 동작입니다.") }
        }
    }

    private fun createPlan(run: ChatRun, toolCallId: String, payload: Map<String, Any?> = ActionPlanFixture.payload()) =
        mockMvc.post("/internal/runs/${run.id}/plans") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = createBody(toolCallId, payload).toJson()
        }

    private fun createBody(toolCallId: String, payload: Map<String, Any?> = ActionPlanFixture.payload()): Map<String, Any?> =
        mapOf(
            "toolCallId" to toolCallId,
            "toolName" to ActionPlanFixture.TOOL,
            "risk" to "caution",
            "requestHash" to "h".repeat(64),
            "payload" to payload,
        )

    private fun action(plan: ActionPlan, name: String, body: Map<String, Any?>? = null): ResultActionsDsl =
        mockMvc.post("/internal/plans/${plan.id}/$name") {
            internalToken()
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body.toJson()
            }
        }

    private fun callBody(): Map<String, Any?> = mapOf(
        "tool" to ActionPlanFixture.TOOL,
        "args" to ActionPlanFixture.ARGS,
        "command" to ActionPlanFixture.COMMAND,
        "risk" to "caution",
        "target" to ActionPlanFixture.TARGET,
    )

    private fun decisionBody(approved: Boolean, call: Map<String, Any?> = callBody()): Map<String, Any?> =
        mapOf("approved" to approved, "userId" to DECIDER.toString(), "call" to call.takeIf { approved })

    private fun savedRun(turnNo: Int = 1): ChatRun {
        val owner = runOwner
        val session = chatSessionRepository.save(ChatFixture.session(owner.first, owner.second))
        return chatRunRepository.save(ChatFixture.run(session.id, turnNo = turnNo))
    }

    private fun savedPlan(toolCallId: String = "call-1", prepare: (ActionPlan) -> ActionPlan = { it }): ActionPlan =
        actionPlanRepository.save(prepare(ActionPlanFixture.plan(savedRun().id, toolCallId = toolCallId)))

    private val runOwner: Pair<UUID, UUID> by lazy {
        val owner = loggedInUser().user.id
        owner to clusterRepository.save(ClusterFixture.cluster(registeredBy = owner, teamId = savedTeam(owner to TeamRole.ADMIN))).id
    }

    companion object {
        private val DECIDER: UUID = UUID.fromString("01900000-0000-7000-8000-0000000000aa")

        private const val AT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"
    }
}
