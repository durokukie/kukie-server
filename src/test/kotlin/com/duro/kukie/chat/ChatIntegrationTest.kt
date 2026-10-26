package com.duro.kukie.chat

import com.duro.kukie.actionplan.ActionPlanFixture
import com.duro.kukie.actionplan.ActionPlanFixture.approved
import com.duro.kukie.actionplan.ActionPlanFixture.offered
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.exception.ChatErrorCode
import com.duro.kukie.chat.presentation.dto.request.CreateChatRequest
import com.duro.kukie.chat.presentation.dto.request.RenameChatRequest
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.util.UUID

class ChatIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatRunRepository: ChatRunRepository

    @Autowired
    private lateinit var actionPlanRepository: ActionPlanRepository

    @Test
    fun `클러스터를 골라 대화를 만든다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)

        // when
        val response = mockMvc.post("/conversations") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = CreateChatRequest(clusterId = cluster.id).toJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.conversation.clusterId") { value(cluster.id.toString()) }
            jsonPath("$.conversation.teamId") { value(cluster.teamId.toString()) }
            jsonPath("$.conversation.userId") { value(me.user.id.toString()) }
            jsonPath("$.conversation.title") { value(ChatSession.DEFAULT_TITLE) }
            jsonPath("$.conversation.shared") { value(false) }
            jsonPath("$.conversation.version") { value(1) }
            jsonPath("$.conversation.running") { value(false) }
            jsonPath("$.session.context") { value(ClusterFixture.DEFAULT_CONTEXT) }
            jsonPath("$.session.namespace") { value(ClusterFixture.DEFAULT_NAMESPACE) }
            jsonPath("$.session.skill") { doesNotExist() }
            jsonPath("$.session.pending") { value(empty<String>()) }
        }.andReturn().response.contentAsString

        // then
        val saved = chatSessionRepository.findByIdOrNull(UUID.fromString(JsonPath.read(response, "$.conversation.id")))!!
        saved.clusterFingerprint shouldBe cluster.fingerprint
        saved.contextName shouldBe cluster.contextName
    }

    @Test
    fun `클러스터를 고르지 않으면 대화를 만들 수 없다`() {
        // given
        val me = loggedInUser()

        // when & then
        mockMvc.post("/conversations") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("title" to "클러스터 없음").toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ChatErrorCode.CLUSTER_REQUIRED.code) }
        }
        chatSessionRepository.count() shouldBe 0
    }

    @Test
    fun `볼 수 있는 대화만 최근에 바뀐 순으로 조회한다`() {
        // given
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val myTeam = savedTeam(other.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)
        val leftTeam = savedTeam(other.id to TeamRole.ADMIN)
        val myPersonal = savedCluster(me.user.id, name = "내 것")
        val othersPersonal = savedCluster(other.id, name = "남의 것")
        val teamCluster = savedCluster(other.id, teamId = myTeam, name = "팀")
        val leftTeamCluster = savedCluster(other.id, teamId = leftTeam, name = "나간 팀")

        val minePrivate = savedSession(me.user.id, myPersonal.id)
        val mineSharedNoTeam = savedSession(me.user.id, myPersonal.id, visibility = ChatVisibility.SHARED)
        val othersTeamShared = savedSession(other.id, teamCluster.id, visibility = ChatVisibility.SHARED)
        savedSession(other.id, teamCluster.id)
        savedSession(other.id, othersPersonal.id, visibility = ChatVisibility.SHARED)
        savedSession(me.user.id, leftTeamCluster.id, visibility = ChatVisibility.SHARED)
        val minePrivateInLeftTeam = savedSession(me.user.id, leftTeamCluster.id)

        // when & then
        mockMvc.get("/conversations") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[*].id") {
                value(
                    contains(
                        minePrivateInLeftTeam.id.toString(),
                        othersTeamShared.id.toString(),
                        mineSharedNoTeam.id.toString(),
                        minePrivate.id.toString(),
                    ),
                )
            }
            jsonPath("$[1].teamId") { value(myTeam.toString()) }
            jsonPath("$[1].userId") { value(other.id.toString()) }
        }
    }

    @Test
    fun `대화와 지난 기록을 함께 조회한다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val session = savedSession(me.user.id, cluster.id)
        val answered = ChatFixture.run(session.id, turnNo = 1, status = RunStatus.COMPLETED).apply {
            recordResponse(mapOf("kind" to "answer", "text" to "파드 3개", "tool_calls" to emptyList<Any>()))
        }
        val failed = ChatFixture.run(session.id, turnNo = 2, inputText = "다시", status = RunStatus.FAILED).apply {
            recordResponse(mapOf("kind" to "error", "code" to "RUN_FAILED", "message" to "실패", "http_status" to 500))
        }
        chatRunRepository.saveAll(listOf(failed, answered))

        // when & then
        mockMvc.get("/conversations/${session.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.conversation.id") { value(session.id.toString()) }
            jsonPath("$.conversation.running") { value(false) }
            jsonPath("$.session.pending") { value(empty<String>()) }
            jsonPath("$.turns[*].seq") { value(contains(1, 2)) }
            jsonPath("$.turns[0].id") { value(answered.id.toString()) }
            jsonPath("$.turns[0].kind") { value("chat") }
            jsonPath("$.turns[0].mode") { value("학습") }
            jsonPath("$.turns[0].status") { value("completed") }
            jsonPath("$.turns[0].inputText") { value("파드 목록 보여줘") }
            jsonPath("$.turns[0].payload.kind") { value("answer") }
            jsonPath("$.turns[0].payload.tool_calls") { value(empty<Any>()) }
            jsonPath("$.turns[0].error") { value(null) }
            jsonPath("$.turns[0].finishedAt") { exists() }
            jsonPath("$.turns[1].status") { value("failed") }
            jsonPath("$.turns[1].payload") { value(null) }
            jsonPath("$.turns[1].error.code") { value("RUN_FAILED") }
            jsonPath("$.turns[1].error.http_status") { value(500) }
            jsonPath("$.turns[1].error.kind") { doesNotExist() }
        }
    }

    @Test
    fun `승인을 기다리는 대화는 대기 중인 카드를 함께 준다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val session = savedSession(me.user.id, cluster.id, visibility = ChatVisibility.SHARED)
        val run = chatRunRepository.save(ChatFixture.run(session.id, status = RunStatus.AWAITING_APPROVAL))
        actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-1").offered())
        actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-2").offered().apply { reject(me.user.id) })
        actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-3"))
        actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-4").approved(me.user.id))

        // when & then
        mockMvc.get("/conversations/${session.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.session.pending") { value(contains("call-1", "call-2", "call-4")) }
            jsonPath("$.turns[0].status") { value("awaiting_approval") }
        }
    }

    @Test
    fun `남의 private 대화는 볼 수 없다`() {
        // given
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(other.id to TeamRole.ADMIN, me.user.id to TeamRole.ADMIN)
        val cluster = savedCluster(other.id, teamId = teamId)
        val othersPrivate = savedSession(other.id, cluster.id)

        // when & then
        mockMvc.get("/conversations/${othersPrivate.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ChatErrorCode.NOT_FOUND.code) }
        }
    }

    @Test
    fun `팀을 나가면 자기가 만든 shared 대화도 볼 수 없다`() {
        // given
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(other.id to TeamRole.ADMIN)
        val cluster = savedCluster(other.id, teamId = teamId)
        val mine = savedSession(me.user.id, cluster.id, visibility = ChatVisibility.SHARED)

        // when & then
        mockMvc.get("/conversations/${mine.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ChatErrorCode.NOT_FOUND.code) }
        }
    }

    @Test
    fun `대화 이름을 바꿔도 목록 순서는 그대로다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val older = savedSession(me.user.id, cluster.id)
        val newer = savedSession(me.user.id, cluster.id)
        val updatedAt = chatSessionRepository.findByIdOrNull(older.id)!!.updatedAt

        // when
        val response = mockMvc.patch("/conversations/${older.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("  배포 점검  ").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(older.id.toString()) }
            jsonPath("$.teamId") { value(cluster.teamId.toString()) }
            jsonPath("$.title") { value("배포 점검") }
            jsonPath("$.shared") { value(false) }
            jsonPath("$.version") { value(2) }
            jsonPath("$.running") { value(false) }
        }.andReturn().response.contentAsString

        // then
        val renamed = chatSessionRepository.findByIdOrNull(older.id)!!
        renamed.title shouldBe "배포 점검"
        renamed.version shouldBe 2
        renamed.updatedAt shouldBe updatedAt
        LocalDateTime.parse(JsonPath.read<String>(response, "$.updatedAt")) shouldBe updatedAt
        mockMvc.get("/conversations") {
            authorization(me.accessToken)
        }.andExpect {
            jsonPath("$[*].id") { value(contains(newer.id.toString(), older.id.toString())) }
            jsonPath("$[1].title") { value("배포 점검") }
        }
    }

    @Test
    fun `shared 대화는 만든 팀원이 실행 중에도 이름을 바꾼다`() {
        // given
        val me = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)
        val cluster = savedCluster(admin.id, teamId = teamId)
        val shared = savedSession(me.user.id, cluster.id, visibility = ChatVisibility.SHARED)
        chatRunRepository.save(ChatFixture.run(shared.id))

        // when & then
        mockMvc.patch("/conversations/${shared.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("배포 점검").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("배포 점검") }
            jsonPath("$.shared") { value(true) }
            jsonPath("$.running") { value(true) }
        }
        chatSessionRepository.findByIdOrNull(shared.id)!!.title shouldBe "배포 점검"
    }

    @Test
    fun `첫 요청이 끝나기 전에 바꾼 이름은 첫 메시지 제목으로 덮이지 않는다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)
        val firstMessage = "web 디플로이먼트의 파드가 계속 재시작되는데 원인을 찾아 줘"
        val run = chatRunRepository.save(ChatFixture.run(session.id, inputText = firstMessage))
        mockMvc.patch("/conversations/${session.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("배포 점검").toJson()
        }.andExpect { status { isOk() } }
        val renamedVersion = chatSessionRepository.findByIdOrNull(session.id)!!.version

        // when
        mockMvc.patch("/internal/runs/${run.id}") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("status" to "completed", "titleFrom" to firstMessage).toJson()
        }.andExpect { status { isOk() } }

        // then
        val after = chatSessionRepository.findByIdOrNull(session.id)!!
        after.title shouldBe "배포 점검"
        after.version shouldBe renamedVersion
    }

    @Test
    fun `shared 대화는 팀 관리자도 이름을 바꾼다`() {
        // given
        val admin = loggedInUser()
        val member = userRepository.save(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, member.id to TeamRole.MEMBER)
        val cluster = savedCluster(member.id, teamId = teamId)
        val shared = savedSession(member.id, cluster.id, visibility = ChatVisibility.SHARED)

        // when & then
        mockMvc.patch("/conversations/${shared.id}") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("배포 점검").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("배포 점검") }
            jsonPath("$.userId") { value(member.id.toString()) }
        }
        chatSessionRepository.findByIdOrNull(shared.id)!!.title shouldBe "배포 점검"
    }

    @Test
    fun `만든 사람도 팀 관리자도 아니면 shared 대화 이름을 바꿀 수 없다`() {
        // given
        val me = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER, other.id to TeamRole.MEMBER)
        val cluster = savedCluster(admin.id, teamId = teamId)
        val shared = savedSession(other.id, cluster.id, visibility = ChatVisibility.SHARED)

        // when & then
        mockMvc.patch("/conversations/${shared.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("배포 점검").toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        chatSessionRepository.findByIdOrNull(shared.id)!!.title shouldBe ChatSession.DEFAULT_TITLE
    }

    @Test
    fun `남의 private 대화는 팀 관리자도 이름을 바꿀 수 없다`() {
        // given
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN, other.id to TeamRole.MEMBER)
        val cluster = savedCluster(me.user.id, teamId = teamId)
        val othersPrivate = savedSession(other.id, cluster.id)

        // when & then
        mockMvc.patch("/conversations/${othersPrivate.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("배포 점검").toJson()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ChatErrorCode.NOT_FOUND.code) }
        }
        chatSessionRepository.findByIdOrNull(othersPrivate.id)!!.title shouldBe ChatSession.DEFAULT_TITLE
    }

    @Test
    fun `없는 대화는 이름을 바꿀 수 없다`() {
        // given
        val me = loggedInUser()

        // when & then
        mockMvc.patch("/conversations/${UUID.randomUUID()}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("배포 점검").toJson()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ChatErrorCode.NOT_FOUND.code) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["   ", "\u00A0\u2007\u202F"])
    fun `대화 이름이 비어 있으면 바꿀 수 없다`(title: String) {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)

        // when & then
        mockMvc.patch("/conversations/${session.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest(title).toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
        chatSessionRepository.findByIdOrNull(session.id)!!.title shouldBe ChatSession.DEFAULT_TITLE
    }

    @Test
    fun `대화 이름이 200자를 넘으면 바꿀 수 없다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)

        // when & then
        mockMvc.patch("/conversations/${session.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RenameChatRequest("가".repeat(201)).toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
        chatSessionRepository.findByIdOrNull(session.id)!!.title shouldBe ChatSession.DEFAULT_TITLE
    }

    @Test
    fun `대화 이름을 보내지 않으면 바꿀 수 없다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)

        // when & then
        mockMvc.patch("/conversations/${session.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
        chatSessionRepository.findByIdOrNull(session.id)!!.title shouldBe ChatSession.DEFAULT_TITLE
    }

    @Test
    fun `내 private 대화를 기록과 함께 지우면 클러스터도 지울 수 있다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val session = savedSession(me.user.id, cluster.id)
        val run = chatRunRepository.save(ChatFixture.run(session.id, status = RunStatus.COMPLETED))
        val plan = actionPlanRepository.save(ActionPlanFixture.plan(run.id, toolCallId = "call-1"))

        // when
        mockMvc.delete("/conversations/${session.id}") {
            authorization(me.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        chatSessionRepository.existsById(session.id) shouldBe false
        chatRunRepository.existsById(run.id) shouldBe false
        actionPlanRepository.existsById(plan.id) shouldBe false
        mockMvc.delete("/clusters/${cluster.id}") {
            authorization(me.accessToken)
        }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `shared 대화는 팀 관리자가 지운다`() {
        // given
        val admin = loggedInUser()
        val member = userRepository.save(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, member.id to TeamRole.MEMBER)
        val cluster = savedCluster(member.id, teamId = teamId)
        val shared = savedSession(member.id, cluster.id, visibility = ChatVisibility.SHARED)

        // when & then
        mockMvc.delete("/conversations/${shared.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }
        chatSessionRepository.existsById(shared.id) shouldBe false
    }

    @Test
    fun `팀 관리자가 아니면 shared 대화를 지울 수 없다`() {
        // given
        val me = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)
        val cluster = savedCluster(admin.id, teamId = teamId)
        val shared = savedSession(me.user.id, cluster.id, visibility = ChatVisibility.SHARED)

        // when & then
        mockMvc.delete("/conversations/${shared.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        chatSessionRepository.existsById(shared.id) shouldBe true
    }

    @Test
    fun `남의 private 대화는 팀 관리자도 지울 수 없다`() {
        // given
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN, other.id to TeamRole.MEMBER)
        val cluster = savedCluster(me.user.id, teamId = teamId)
        val othersPrivate = savedSession(other.id, cluster.id)

        // when & then
        mockMvc.delete("/conversations/${othersPrivate.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ChatErrorCode.NOT_FOUND.code) }
        }
        chatSessionRepository.existsById(othersPrivate.id) shouldBe true
    }

    @Test
    fun `끝나지 않은 요청이 있는 대화는 지울 수 없다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id)
        val session = savedSession(me.user.id, cluster.id)
        chatRunRepository.save(ChatFixture.run(session.id))

        // when & then
        mockMvc.delete("/conversations/${session.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ChatErrorCode.ACTIVE_RUN_EXISTS.code) }
        }
        chatSessionRepository.existsById(session.id) shouldBe true
    }

    private fun savedCluster(
        registeredBy: UUID,
        teamId: UUID = savedTeam(registeredBy to TeamRole.ADMIN),
        name: String = ClusterFixture.DEFAULT_NAME,
    ): Cluster =
        clusterRepository.save(
            ClusterFixture.cluster(registeredBy = registeredBy, teamId = teamId, name = name, fingerprint = "f".repeat(64)),
        )

    private fun savedSession(userId: UUID, clusterId: UUID, visibility: ChatVisibility = ChatVisibility.PRIVATE): ChatSession =
        chatSessionRepository.save(ChatFixture.session(userId, clusterId, visibility = visibility))
}
