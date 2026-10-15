package com.duro.kukie.chat

import com.duro.kukie.actionplan.ActionPlanFixture
import com.duro.kukie.actionplan.ActionPlanFixture.approved
import com.duro.kukie.actionplan.ActionPlanFixture.offered
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.auth.exception.AuthErrorCode
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.exception.ChatErrorCode
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class InternalChatIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Test
    fun `대화 컨텍스트에 진행 중인 요청과 지난 메시지를 담아 준다`() {
        // given
        val me = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)
        val cluster = savedCluster(admin.id, teamId = teamId)
        val session = savedSession(me.user.id, cluster.id, visibility = ChatVisibility.SHARED)
        val first = run(session, 1, RunStatus.COMPLETED, messages = listOf(mapOf("kind" to "request", "parts" to listOf<Any>())))
        run(session, 2, RunStatus.COMPLETED)
        run(session, 3, RunStatus.FAILED, messages = listOf(mapOf("kind" to "response")))
        val active = run(session, 4, RunStatus.AWAITING_APPROVAL, messages = listOf(mapOf("kind" to "response")))
        val waiting = actionPlanRepository.save(ActionPlanFixture.plan(active.id, toolCallId = "call-1").offered())

        // when & then
        mockMvc.get("/internal/conversations/${session.id}/context?purpose=chat") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(me.user.id.toString()) }
            jsonPath("$.conversation.id") { value(session.id.toString()) }
            jsonPath("$.conversation.clusterId") { value(cluster.id.toString()) }
            jsonPath("$.conversation.teamId") { value(teamId.toString()) }
            jsonPath("$.conversation.userId") { value(me.user.id.toString()) }
            jsonPath("$.conversation.title") { value(ChatSession.DEFAULT_TITLE) }
            jsonPath("$.conversation.shared") { value(true) }
            jsonPath("$.conversation.currentMode") { value(ChatSession.DEFAULT_MODE) }
            jsonPath("$.conversation.context") { value(ChatFixture.DEFAULT_CONTEXT) }
            jsonPath("$.conversation.namespace") { value(ChatFixture.DEFAULT_NAMESPACE) }
            jsonPath("$.conversation.clusterFingerprint") { value(ChatFixture.FINGERPRINT) }
            jsonPath("$.activeRun.id") { value(active.id.toString()) }
            jsonPath("$.activeRun.status") { value("awaiting_approval") }
            jsonPath("$.activeRunPlans[*].id") { value(contains(waiting.id.toString())) }
            jsonPath("$.activeRunPlans[0].status") { value("WAITING_APPROVAL") }
            jsonPath("$.history[*].runId") { value(contains(first.id.toString())) }
            jsonPath("$.history[0].agentMessages[0].kind") { value("request") }
        }
    }

    @Test
    fun `관리자는 shared 대화를 승인할 수 있다`() {
        // given
        val owner = userRepository.save(UserFixture.user(email = "owner@example.com"))
        val admin = loggedInUser(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, owner.id to TeamRole.MEMBER)
        val session = savedSession(owner.id, savedCluster(admin.user.id, teamId = teamId).id, visibility = ChatVisibility.SHARED)

        // when & then
        approveContext(session, admin).andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(admin.user.id.toString()) }
        }
    }

    @Test
    fun `방 주인은 구성원이어도 승인할 수 있다`() {
        // given
        val owner = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, owner.user.id to TeamRole.MEMBER)
        val session = savedSession(owner.user.id, savedCluster(admin.id, teamId = teamId).id, visibility = ChatVisibility.SHARED)

        // when & then
        approveContext(session, owner).andExpect { status { isOk() } }
    }

    @Test
    fun `private 대화는 승인할 수 없다`() {
        // given
        val owner = loggedInUser()
        val session = savedSession(owner.user.id, savedCluster(owner.user.id).id)

        // when & then
        approveContext(session, owner).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ChatErrorCode.PRIVATE_SESSION.code) }
        }
    }

    @Test
    fun `관리자도 방 주인도 아니면 승인할 수 없다`() {
        // given
        val owner = userRepository.save(UserFixture.user(email = "owner@example.com"))
        val member = loggedInUser()
        val teamId = savedTeam(owner.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        val session = savedSession(owner.id, savedCluster(owner.id, teamId = teamId).id, visibility = ChatVisibility.SHARED)

        // when & then
        mockMvc.get("/internal/conversations/${session.id}/context?purpose=chat") {
            internalToken()
            authorization(member.accessToken)
        }.andExpect { status { isOk() } }
        approveContext(session, member).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `요청을 시작하면 대화의 버전이 오른다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)

        // when
        startRun(session, me, requestId = "req-1", inputText = "파드 보여줘").andExpect {
            status { isCreated() }
            jsonPath("$.created") { value(true) }
            jsonPath("$.run.conversationId") { value(session.id.toString()) }
            jsonPath("$.run.requestId") { value("req-1") }
            jsonPath("$.run.seq") { value(1) }
            jsonPath("$.run.kind") { value("chat") }
            jsonPath("$.run.mode") { value("진단") }
            jsonPath("$.run.status") { value("running") }
            jsonPath("$.run.inputText") { value("파드 보여줘") }
            jsonPath("$.run.finishedAt") { value(null) }
        }

        // then
        val after = chatSessionRepository.findByIdOrNull(session.id)!!
        after.version shouldBe session.version + 1
        (after.updatedAt > session.updatedAt) shouldBe true
    }

    @Test
    fun `같은 요청을 다시 보내면 처음 시작한 요청을 돌려준다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        val existing = chatRunRepository.save(
            ChatFixture.run(session.id, requestId = "req-1", inputText = "파드 보여줘", status = RunStatus.COMPLETED),
        )

        // when & then
        startRun(session, me, requestId = "req-1", inputText = "파드 보여줘", mode = "학습").andExpect {
            status { isOk() }
            jsonPath("$.created") { value(false) }
            jsonPath("$.run.id") { value(existing.id.toString()) }
        }
        chatRunRepository.count() shouldBe 1
    }

    @Test
    fun `진행 중인 요청이 있으면 새 요청을 시작할 수 없다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        run(session, 1, RunStatus.AWAITING_APPROVAL)

        // when & then
        startRun(session, me, requestId = "req-2").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ChatErrorCode.ACTIVE_RUN_EXISTS.code) }
        }
        chatRunRepository.count() shouldBe 1
    }

    @Test
    fun `진행 중인 요청을 중단하면 열린 계획도 닫는다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id, visibility = ChatVisibility.SHARED)
        val active = run(session, 2, RunStatus.AWAITING_APPROVAL)
        val done = run(session, 1, RunStatus.COMPLETED)
        val waiting = actionPlanRepository.save(ActionPlanFixture.plan(active.id, toolCallId = "call-1").offered())
        val approved = actionPlanRepository.save(ActionPlanFixture.plan(active.id, toolCallId = "call-2").approved(me.user.id))
        val rejected = actionPlanRepository.save(
            ActionPlanFixture.plan(active.id, toolCallId = "call-3").offered().apply { reject(me.user.id) },
        )

        // when & then
        mockMvc.post("/internal/conversations/${session.id}/interrupt") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("message" to "서버가 다시 떠서 중단됐다").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.runs[*].id") { value(contains(active.id.toString())) }
            jsonPath("$.runs[0].status") { value("interrupted") }
            jsonPath("$.runs[0].finishedAt") { exists() }
            jsonPath("$.runs[0].responsePayload.kind") { value("error") }
            jsonPath("$.runs[0].responsePayload.code") { value("INTERRUPTED") }
            jsonPath("$.runs[0].responsePayload.message") { value("서버가 다시 떠서 중단됐다") }
            jsonPath("$.runs[0].responsePayload.http_status") { value(409) }
            jsonPath("$.closedPlans[*].id") { value(contains(waiting.id.toString(), approved.id.toString())) }
        }

        // then
        actionPlanRepository.findByIdOrNull(waiting.id)!!.status shouldBe PlanStatus.EXPIRED
        actionPlanRepository.findByIdOrNull(approved.id)!!.status shouldBe PlanStatus.UNKNOWN
        actionPlanRepository.findByIdOrNull(rejected.id)!!.status shouldBe PlanStatus.REJECTED
        chatRunRepository.findByIdOrNull(done.id)!!.status shouldBe RunStatus.COMPLETED
    }

    @Test
    fun `내부 토큰이 없으면 내부 API 를 호출할 수 없다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)

        // when & then
        mockMvc.get("/internal/conversations/${session.id}/context?purpose=chat") {
            authorization(me.accessToken)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_INTERNAL_TOKEN.code) }
        }
        mockMvc.post("/internal/conversations/${session.id}/runs") {
            internalToken("wrong-token")
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("requestId" to "req-1", "kind" to "chat", "mode" to "학습").toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_INTERNAL_TOKEN.code) }
        }
        chatRunRepository.count() shouldBe 0
    }

    @Test
    fun `사용자 토큰이 만료돼도 요청을 중단할 수 있다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        run(session, 1, RunStatus.RUNNING)

        // when & then
        mockMvc.post("/internal/conversations/${session.id}/interrupt") {
            internalToken()
            authorization("expired-or-broken-token")
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("message" to "중단").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.runs[0].status") { value("interrupted") }
        }
    }

    private fun approveContext(session: ChatSession, user: LoggedInUser) =
        mockMvc.get("/internal/conversations/${session.id}/context?purpose=approve") {
            internalToken()
            authorization(user.accessToken)
        }

    private fun startRun(
        session: ChatSession,
        user: LoggedInUser,
        requestId: String,
        inputText: String? = "파드 보여줘",
        kind: RunKind = RunKind.CHAT,
        mode: String = "진단",
    ) = mockMvc.post("/internal/conversations/${session.id}/runs") {
        internalToken()
        authorization(user.accessToken)
        contentType = MediaType.APPLICATION_JSON
        content = mapOf("requestId" to requestId, "kind" to kind.value, "mode" to mode, "inputText" to inputText).toJson()
    }

    private fun savedCluster(registeredBy: UUID, teamId: UUID = savedTeam(registeredBy to TeamRole.ADMIN)): Cluster =
        clusterRepository.save(ClusterFixture.cluster(registeredBy = registeredBy, teamId = teamId))

    private fun savedSession(userId: UUID, clusterId: UUID, visibility: ChatVisibility = ChatVisibility.PRIVATE): ChatSession =
        chatSessionRepository.save(ChatFixture.session(userId, clusterId, visibility = visibility))

    private fun run(session: ChatSession, turnNo: Int, status: RunStatus, messages: List<Any?>? = null): ChatRun =
        chatRunRepository.save(
            ChatFixture.run(session.id, turnNo = turnNo, status = status).apply {
                messages?.let(::appendAgentMessages)
            },
        )
}
