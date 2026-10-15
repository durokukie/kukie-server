package com.duro.kukie.actionplan

import com.duro.kukie.actionplan.ActionPlanFixture.executing
import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import java.util.UUID

class ActionPlanIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Test
    fun `볼 수 있는 대화의 계획만 최근에 바뀐 순으로 조회한다`() {
        // given
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(other.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)
        val leftTeam = savedTeam(other.id to TeamRole.ADMIN)
        val myCluster = savedCluster(me.user.id, name = "내 것")
        val teamCluster = savedCluster(other.id, teamId = teamId, name = "팀")
        val leftCluster = savedCluster(other.id, teamId = leftTeam, name = "나간 팀")

        val mine = savedPlan(savedRun(me.user.id, myCluster), "call-1")
        val teamShared = savedPlan(savedRun(other.id, teamCluster, visibility = ChatVisibility.SHARED), "call-2")
        savedPlan(savedRun(other.id, teamCluster), "call-3")
        savedPlan(savedRun(me.user.id, leftCluster, visibility = ChatVisibility.SHARED), "call-4")

        // when & then
        mockMvc.get("/action-plans") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[*].id") { value(contains(teamShared.id.toString(), mine.id.toString())) }
            jsonPath("$[0].code") { value(teamShared.code) }
            jsonPath("$[0].clusterId") { value(teamCluster.id.toString()) }
            jsonPath("$[0].title") { value(ActionPlanFixture.INTENT) }
            jsonPath("$[0].status") { value("DRAFT") }
            jsonPath("$[0].running") { value(false) }
            jsonPath("$[0].risk") { value("caution") }
            jsonPath("$[0].requestedBy") { value(other.id.toString()) }
            jsonPath("$[0].approvals") { value(1) }
            jsonPath("$[0].updatedAt") { exists() }
        }
    }

    @Test
    fun `계획 상세를 마크다운과 함께 조회한다`() {
        // given
        val me = loggedInUser()
        val run = savedRun(me.user.id, savedCluster(me.user.id), visibility = ChatVisibility.SHARED)
        val plan = savedPlan(run, "call-1") { it.executing(me.user.id).apply { recordResult(true, "scaled", "", 0) } }

        // when & then
        mockMvc.get("/action-plans/${plan.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(plan.id.toString()) }
            jsonPath("$.code") { value(plan.code) }
            jsonPath("$.runId") { value(run.id.toString()) }
            jsonPath("$.status") { value("APPLIED") }
            jsonPath("$.risk") { value("caution") }
            jsonPath("$.failureReason") { value(null) }
            jsonPath("$.decision.approved") { value(true) }
            jsonPath("$.decision.user_id") { value(me.user.id.toString()) }
            jsonPath("$.executionResult.exit_code") { value(0) }
            jsonPath("$.appliedAt") { exists() }
            jsonPath("$.createdAt") { exists() }
            jsonPath("$.updatedAt") { exists() }
            jsonPath("$.markdown") { value(startsWith("# Action Plan\n\n```yaml\nid: ${plan.code}\n")) }
            jsonPath("$.markdown") { value(containsString("status: APPLIED\n")) }
            jsonPath("$.markdown") { value(containsString("# Intent\n\n${ActionPlanFixture.INTENT}\n")) }
        }
    }

    private fun savedCluster(
        registeredBy: UUID,
        teamId: UUID = savedTeam(registeredBy to TeamRole.ADMIN),
        name: String = ClusterFixture.DEFAULT_NAME,
    ): Cluster =
        clusterRepository.save(ClusterFixture.cluster(registeredBy = registeredBy, teamId = teamId, name = name))

    private fun savedRun(owner: UUID, cluster: Cluster, visibility: ChatVisibility = ChatVisibility.PRIVATE): ChatRun {
        val session = chatSessionRepository.save(ChatFixture.session(owner, cluster.id, visibility = visibility))
        return chatRunRepository.save(ChatFixture.run(session.id))
    }

    private fun savedPlan(run: ChatRun, toolCallId: String, prepare: (ActionPlan) -> ActionPlan = { it }): ActionPlan =
        actionPlanRepository.save(prepare(ActionPlanFixture.plan(run.id, toolCallId = toolCallId)))
}
