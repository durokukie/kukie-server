package com.duro.kukie.actionplan

import com.duro.kukie.actionplan.ActionPlanFixture.executing
import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.DryRunStatus
import com.duro.kukie.actionplan.domain.PlanPullRequest
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
import io.kotest.matchers.string.shouldNotContain
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
        val other = userRepository.save(UserFixture.user(name = "배재현", email = "other@example.com"))
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
            jsonPath("$[0].requestedByName") { value("배재현") }
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
            jsonPath("$.title") { value(ActionPlanFixture.INTENT) }
            jsonPath("$.toolName") { value(plan.toolName) }
            jsonPath("$.command.length()") { value(ActionPlanFixture.COMMAND.size) }
            jsonPath("$.expectedEffects[0]") { value("파드가 3개가 된다") }
            jsonPath("$.sideEffects[0]") { value("잠깐 부하가 늘 수 있다") }
            jsonPath("$.argocd") { value(null) }
            jsonPath("$.gitops") { doesNotExist() }
            jsonPath("$.pullRequestNumber") { value(null) }
            jsonPath("$.mergeCommit") { value(null) }
        }
    }

    @Test
    fun `GitOps 길 계획 상세에 ArgoCD 경고와 PR·머지 커밋·sync 결과를 꺼내 준다`() {
        // given
        val me = loggedInUser()
        val run = savedRun(me.user.id, savedCluster(me.user.id), visibility = ChatVisibility.SHARED)
        val argocd = mapOf("summary" to "ArgoCD 앱 api 가 관리합니다", "level" to "self_heal", "applications" to listOf("api"))
        val plan = actionPlanRepository.save(ActionPlanFixture.plan(run.id, payload = ActionPlanFixture.payload() + ("argocd" to argocd)).let {
            it.apply {
                recordDryRun(DryRunStatus.SUCCEEDED, "diff", "")
                offer("PR 을 닫으면 되돌린다", PlanPullRequest(12, "https://github.com/acme/manifests/pull/12"))
                approve(ActionPlanFixture.call(), me.user.id)
                start()
                recordResult(true, "merged", "", 0, "abc123", mapOf("status" to "Synced", "health" to "Healthy"))
            }
        })

        // when & then
        mockMvc.get("/action-plans/${plan.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.argocd.level") { value("self_heal") }
            jsonPath("$.argocd.applications[0]") { value("api") }
            jsonPath("$.pullRequestNumber") { value(12) }
            jsonPath("$.pullRequestUrl") { value("https://github.com/acme/manifests/pull/12") }
            jsonPath("$.mergeCommit") { value("abc123") }
            jsonPath("$.syncResult.status") { value("Synced") }
            jsonPath("$.syncResult.health") { value("Healthy") }
        }
    }

    @Test
    fun `새 리소스 계획 상세에는 새 리소스와 경고만 내주고 payload 의 다른 칸은 내주지 않는다`() {
        // given
        val me = loggedInUser()
        val run = savedRun(me.user.id, savedCluster(me.user.id), visibility = ChatVisibility.SHARED)
        val gitops = mapOf(
            "repository" to "acme/manifests",
            "file_diff" to "+password: file-diff-secret",
            "render_diff" to "+password: render-diff-secret",
            "warnings" to listOf("warnings-secret"),
            "notice" to "notice-secret",
            "new_resources" to listOf(
                mapOf(
                    "api_version" to "apps/v1",
                    "kind" to "Deployment",
                    "name" to "caddy",
                    "namespace" to "shop",
                    "file" to "apps/plain/caddy.yaml",
                    "manifest_yaml" to "manifest-secret",
                ),
            ),
            "risk_warnings" to listOf(
                mapOf(
                    "code" to "latest_image",
                    "location" to mapOf(
                        "file" to "apps/plain/caddy.yaml",
                        "resource" to "Deployment shop/caddy",
                        "field" to "spec.template.spec.containers[caddy].image",
                        "value" to "caddy:latest",
                        "raw" to "location-secret",
                    ),
                    "risk" to "다른 버전이 뜰 수 있어요",
                    "suggestion" to "버전을 정해 주세요",
                    "text" to "한 줄",
                    "raw" to "warning-secret",
                ),
                mapOf(
                    "code" to "plaintext_env",
                    "location" to mapOf(
                        "file" to "apps/plain/caddy.yaml",
                        "resource" to "Deployment shop/caddy",
                        "field" to "spec.template.spec.containers[caddy].env[TOKEN].value",
                        "value" to null,
                    ),
                    "risk" to "값이 평문으로 남아요",
                    "suggestion" to "Secret 을 쓰세요",
                    "text" to "다른 한 줄",
                ),
            ),
        )
        val plan = actionPlanRepository.save(
            ActionPlanFixture.plan(run.id, payload = ActionPlanFixture.payload() + ("gitops" to gitops)),
        )

        // when
        val response = mockMvc.get("/action-plans/${plan.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.gitops.length()") { value(2) }
            jsonPath("$.gitops.new_resources[0].api_version") { value("apps/v1") }
            jsonPath("$.gitops.new_resources[0].kind") { value("Deployment") }
            jsonPath("$.gitops.new_resources[0].name") { value("caddy") }
            jsonPath("$.gitops.new_resources[0].namespace") { value("shop") }
            jsonPath("$.gitops.new_resources[0].file") { value("apps/plain/caddy.yaml") }
            jsonPath("$.gitops.risk_warnings[0].code") { value("latest_image") }
            jsonPath("$.gitops.risk_warnings[0].location.resource") { value("Deployment shop/caddy") }
            jsonPath("$.gitops.risk_warnings[0].location.value") { value("caddy:latest") }
            jsonPath("$.gitops.risk_warnings[0].risk") { value("다른 버전이 뜰 수 있어요") }
            jsonPath("$.gitops.risk_warnings[0].suggestion") { value("버전을 정해 주세요") }
            jsonPath("$.gitops.risk_warnings[0].text") { value("한 줄") }
            jsonPath("$.gitops.risk_warnings[1].location.value") { value(null) }
        }.andReturn().response.contentAsString

        // then
        response shouldNotContain "secret"
    }

    @Test
    fun `새 리소스 칸이 없는 GitOps 계획 상세에는 gitops 칸이 없다`() {
        // given
        val me = loggedInUser()
        val run = savedRun(me.user.id, savedCluster(me.user.id), visibility = ChatVisibility.SHARED)
        val gitops = mapOf("repository" to "acme/manifests", "file_diff" to "+replicas: 3", "warnings" to emptyList<String>())
        val plan = actionPlanRepository.save(
            ActionPlanFixture.plan(run.id, payload = ActionPlanFixture.payload() + ("gitops" to gitops)),
        )

        // when & then
        mockMvc.get("/action-plans/${plan.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.gitops") { doesNotExist() }
        }
    }

    @Test
    fun `남의 Private 방 계획의 새 리소스와 경고는 볼 수 없다`() {
        // given
        val me = loggedInUser()
        val other = loggedInUser(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN, other.user.id to TeamRole.MEMBER)
        val run = savedRun(me.user.id, savedCluster(me.user.id, teamId = teamId), visibility = ChatVisibility.PRIVATE)
        val gitops = mapOf("risk_warnings" to listOf(mapOf("code" to "x", "risk" to "위험")))
        val plan = actionPlanRepository.save(
            ActionPlanFixture.plan(run.id, payload = ActionPlanFixture.payload() + ("gitops" to gitops)),
        )

        // when & then
        mockMvc.get("/action-plans/${plan.id}") {
            authorization(other.accessToken)
        }.andExpect {
            status { isNotFound() }
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
