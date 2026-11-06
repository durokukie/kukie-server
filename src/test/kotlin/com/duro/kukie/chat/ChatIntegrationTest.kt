package com.duro.kukie.chat

import com.duro.kukie.actionplan.ActionPlanFixture
import com.duro.kukie.actionplan.ActionPlanFixture.approved
import com.duro.kukie.actionplan.ActionPlanFixture.offered
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.RunKind
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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.startsWith
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
        val answered = ChatFixture.run(session.id, turnNo = 1, status = RunStatus.COMPLETED, requestedBy = me.user.id).apply {
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
            jsonPath("$.turns[0].requestedBy") { value(me.user.id.toString()) }
            jsonPath("$.turns[0].payload.kind") { value("answer") }
            jsonPath("$.turns[0].payload.tool_calls") { value(empty<Any>()) }
            jsonPath("$.turns[0].error") { value(null) }
            jsonPath("$.turns[0].finishedAt") { exists() }
            jsonPath("$.turns[1].status") { value("failed") }
            jsonPath("$.turns[1].payload") { value(null) }
            jsonPath("$.turns[1].requestedBy") { value(null) }
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
    fun `대화의 요청·명령·변경·답을 작업내역으로 모아 준다`() {
        // given
        val me = loggedInUser()
        val bok = userRepository.save(UserFixture.user(email = "bok@example.com", name = "Bok"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN, bok.id to TeamRole.MEMBER)
        val cluster = savedCluster(me.user.id, teamId = teamId, name = "dev")
        val session = savedSession(me.user.id, cluster.id, visibility = ChatVisibility.SHARED)
        val asked = ChatFixture.run(session.id, turnNo = 1, inputText = "api 가 왜 죽어?", status = RunStatus.COMPLETED, requestedBy = bok.id)
            .apply { recordResponse(answerPayload(output = "x".repeat(4001))) }
        val failed = ChatFixture.run(session.id, turnNo = 2, inputText = "다시", status = RunStatus.FAILED).apply {
            recordResponse(mapOf("kind" to "error", "code" to "RUN_FAILED", "message" to "실패", "http_status" to 500))
        }
        chatRunRepository.saveAll(listOf(asked, failed))
        actionPlanRepository.save(
            ActionPlanFixture.plan(asked.id).approved(me.user.id).apply {
                start()
                recordResult(success = true, stdout = "deployment.apps/web scaled", stderr = "", exitCode = 0)
            },
        )
        actionPlanRepository.save(ActionPlanFixture.plan(asked.id, toolCallId = "call-2").offered().apply { reject(me.user.id) })

        // when & then
        mockMvc.get("/conversations/${session.id}/worklog") {
            authorization(me.accessToken)
            param("tz", "Asia/Seoul")
        }.andExpect {
            status { isOk() }
            jsonPath("$.schema") { value("kukie.worklog/v1") }
            jsonPath("$.exportedAt") { value(endsWith("Z")) }
            jsonPath("$.timeZone") { value("Asia/Seoul") }
            jsonPath("$.utcOffset") { value("+09:00") }
            jsonPath("$.conversation.createdAt") { value(endsWith("Z")) }
            jsonPath("$.turns[0].changes[0].proposedAt") { doesNotExist() }
            jsonPath("$.conversation.shared") { value(true) }
            jsonPath("$.conversation.ownerId") { value(me.user.id.toString()) }
            jsonPath("$.environment.clusterName") { value("dev") }
            jsonPath("$.environment.context") { value(ChatFixture.DEFAULT_CONTEXT) }
            jsonPath("$.environment.namespace") { value(ChatFixture.DEFAULT_NAMESPACE) }
            jsonPath("$.environment.fingerprint") { value(ChatFixture.FINGERPRINT) }
            jsonPath("$.people[*].name") { value(contains("Bok", me.user.name)) }
            jsonPath("$.turns[*].seq") { value(contains(1, 2)) }
            jsonPath("$.turns[0].requestedBy") { value(bok.id.toString()) }
            jsonPath("$.turns[0].request") { value("api 가 왜 죽어?") }
            jsonPath("$.turns[0].startedAt") { value(endsWith("Z")) }
            jsonPath("$.turns[0].changes[0].appliedAt") { value(endsWith("Z")) }
            jsonPath("$.turns[0].steps[0].label") { value("로그 조회") }
            jsonPath("$.turns[0].steps[0].access") { value("read-only") }
            jsonPath("$.turns[0].steps[0].command") { value("kubectl logs deploy/api -n web") }
            jsonPath("$.turns[0].steps[0].output") { value(startsWith("x".repeat(4000) + "\n…(1자 생략)")) }
            jsonPath("$.turns[0].answer.narration") { value("메모리가 부족해요") }
            jsonPath("$.turns[0].answer.finding.cause") { value("OOMKilled") }
            jsonPath("$.turns[0].answer.suggestedNextAction") { value("한도를 올리세요") }
            jsonPath("$.turns[0].changes[*].status") { value(contains("APPLIED", "REJECTED")) }
            jsonPath("$.turns[0].changes[0].tool") { value(ActionPlanFixture.TOOL) }
            jsonPath("$.turns[0].changes[0].intent") { value(ActionPlanFixture.INTENT) }
            jsonPath("$.turns[0].changes[0].command[1]") { value("scale") }
            jsonPath("$.turns[0].changes[0].args.replicas") { value(3) }
            jsonPath("$.turns[0].changes[0].risk") { value("caution") }
            jsonPath("$.turns[0].changes[0].dryRun.status") { value("succeeded") }
            jsonPath("$.turns[0].changes[0].decision.approved") { value(true) }
            jsonPath("$.turns[0].changes[0].decision.by") { value(me.user.id.toString()) }
            jsonPath("$.turns[0].changes[0].decision.at") { value(endsWith("Z")) }
            jsonPath("$.turns[0].changes[0].result.exit_code") { value(0) }
            jsonPath("$.turns[0].changes[1].decision.approved") { value(false) }
            jsonPath("$.turns[0].error") { value(null) }
            jsonPath("$.turns[1].requestedBy") { value(null) }
            jsonPath("$.turns[1].answer") { value(null) }
            jsonPath("$.turns[1].error.code") { value("RUN_FAILED") }
        }
    }

    @Test
    fun `작업내역을 Markdown 파일로 내려받는다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, name = "dev")
        val session = savedSession(me.user.id, cluster.id)
        val run = chatRunRepository.save(
            ChatFixture.run(session.id, inputText = "api 가 왜 죽어?", status = RunStatus.COMPLETED, requestedBy = me.user.id)
                .apply { recordResponse(answerPayload(output = "```\nOOMKilled")) },
        )
        actionPlanRepository.save(ActionPlanFixture.plan(run.id).offered().apply { reject(me.user.id) })
        chatRunRepository.save(
            ChatFixture.run(session.id, turnNo = 2, kind = RunKind.MODE_CHANGE, inputText = null, status = RunStatus.COMPLETED),
        )

        // when
        val markdown = mockMvc.get("/conversations/${session.id}/worklog/markdown") {
            authorization(me.accessToken)
            param("tz", "Asia/Seoul")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("text/markdown") }
            header { string("Content-Disposition", containsString("worklog-${session.id}.md")) }
        }.andReturn().response.getContentAsString(Charsets.UTF_8)

        // then
        markdown shouldStartWith "# 작업내역 · ${ChatSession.DEFAULT_TITLE}"
        markdown shouldContain "- 대화: 개인(Private) · 만든 사람 ${me.user.name}"
        markdown shouldContain "- 클러스터: dev · context `prod` · namespace `web`"
        markdown shouldContain "## 1. api 가 왜 죽어?"
        markdown shouldContain "요청: ${me.user.name} · 상태 `completed`"
        markdown shouldContain "- 시작: "
        markdown shouldContain " (Asia/Seoul, UTC+09:00)"
        markdown shouldContain "- 내보낸 시각: "
        markdown shouldContain "> 시각은 내려받은 브라우저의 시간대(Asia/Seoul, UTC+09:00) 기준입니다."
        markdown shouldContain "**로그 조회** · 읽기 전용\n\n````\n$ kubectl logs deploy/api -n web\n```\nOOMKilled\n````"
        markdown shouldContain "#### ${ActionPlanFixture.INTENT}"
        markdown shouldContain "- 명령: `kubectl scale deployment/web --replicas=3 -n web`"
        markdown shouldContain "- 결정: 거절 · ${me.user.name} · "
        markdown shouldContain "- 원인: OOMKilled"
        markdown shouldContain "다음에 할 일: 한도를 올리세요"
        markdown shouldContain "## 2. 모드 변경 → 학습"
    }

    @Test
    fun `기록이 없는 대화도 빈 작업내역을 준다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)

        // when & then
        mockMvc.get("/conversations/${session.id}/worklog") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.turns") { value(empty<Any>()) }
            jsonPath("$.timeZone") { value("UTC") }
            jsonPath("$.utcOffset") { value("+00:00") }
            jsonPath("$.people[*].id") { value(contains(me.user.id.toString())) }
        }
    }

    @Test
    fun `시간대 없이 받은 Markdown 은 UTC 로 그렸다고 적는다`() {
        // given
        val me = loggedInUser()
        val session = savedSession(me.user.id, savedCluster(me.user.id).id)

        // when
        val markdown = mockMvc.get("/conversations/${session.id}/worklog/markdown") {
            authorization(me.accessToken)
            param("tz", "Mars/Olympus")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.getContentAsString(Charsets.UTF_8)

        // then
        markdown shouldContain " (UTC, UTC+00:00)"
        markdown shouldContain "> 브라우저 시간대를 받지 못해 UTC 기준으로 표시합니다."
    }

    @Test
    fun `남의 private 대화의 작업내역은 받을 수 없다`() {
        // given
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(other.id to TeamRole.ADMIN, me.user.id to TeamRole.ADMIN)
        val othersPrivate = savedSession(other.id, savedCluster(other.id, teamId = teamId).id)

        // when & then
        mockMvc.get("/conversations/${othersPrivate.id}/worklog/markdown") {
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

    private fun answerPayload(output: String): Map<String, Any?> = mapOf(
        "kind" to "answer",
        "skill" to "shared",
        "response" to mapOf(
            "narration" to "메모리가 부족해요",
            "steps" to listOf(
                mapOf(
                    "step_label" to "로그 조회",
                    "access" to "read-only",
                    "command" to "kubectl logs deploy/api -n web",
                    "output" to output,
                    "explanations" to emptyList<Any>(),
                ),
            ),
            "suggested_next_action" to "한도를 올리세요",
            "finding" to mapOf("cause" to "OOMKilled", "evidence" to "로그", "recommendation" to "한도 올리기"),
        ),
    )

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
