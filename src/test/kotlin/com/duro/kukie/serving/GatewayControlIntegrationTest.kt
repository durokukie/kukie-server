package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.domain.*
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import java.util.UUID

@TestPropertySource(properties = ["internal.gateway-token=test-gateway-token"])
class GatewayControlIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var endpoints: ServingEndpointRepository
    @Autowired private lateinit var usage: ServingUsageRepository
    @Autowired private lateinit var issue: IssueServingApiKeyService
    @Autowired private lateinit var revoke: RevokeMyServingApiKeyService

    @Test
    fun `키의 고객을 확인하고 기록하며 완료 재전송은 멱등이다`() {
        // given
        val s = setup("a")
        val issued = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90))
        // when
        post("/authorize", mapOf("token" to issued.token)).andExpect {
            status { isOk() }; header { string("Cache-Control", "no-store") }
            jsonPath("$.keyId") { value(issued.key.id.toString()) }
            jsonPath("$.clusterId") { value(s.first.clusterId.toString()) }
            jsonPath("$.models[0]") { value("shared") }
            jsonPath("$.token") { doesNotExist() }; jsonPath("$.baseUrl") { doesNotExist() }
        }
        post("/requests", mapOf("keyId" to issued.key.id, "model" to "shared")).andExpect { status { isOk() } }
        val id = usage.findAll().single().id
        post("/requests/$id/finish", finish()).andExpect { status { isNoContent() } }
        post("/requests/$id/finish", finish() + ("inputTokens" to 999)).andExpect { status { isNoContent() } }
        // then
        usage.findAll().size shouldBe 1
        usage.findById(id).get().let { it.outcome shouldBe "COMPLETED"; it.inputTokens shouldBe 3; it.outputTokens shouldBe 7 }
    }

    @Test
    fun `일반 내부 토큰과 개인 키는 게이트웨이 서비스 인증에 사용할 수 없다`() {
        // given
        val s = setup("a")
        val personal = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).token
        // when then
        for (token in listOf("test-internal-token", "", "invalid", personal)) {
            post("/authorize", mapOf("token" to "invalid"), token).andExpect { status { isUnauthorized() } }
        }
        mockMvc.patch("/internal/clusters/${UUID.randomUUID()}/status") {
            header("X-Kukie-Internal-Token", "test-gateway-token")
            contentType = MediaType.APPLICATION_JSON; content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `인증 후 폐기한 키는 시작할 수 없지만 진행 요청은 완료할 수 있다`() {
        // given
        val s = setup("a")
        val issued = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90))
        post("/requests", mapOf("keyId" to issued.key.id, "model" to "shared")).andExpect { status { isOk() } }
        val id = usage.findAll().single().id
        // when
        revoke(s.first.id, s.second, issued.key.id)
        // then
        post("/authorize", mapOf("token" to issued.token)).andExpect { status { isUnauthorized() } }
        post("/requests", mapOf("keyId" to issued.key.id, "model" to "shared")).andExpect { status { isUnauthorized() } }
        post("/requests/$id/finish", finish()).andExpect { status { isNoContent() } }
        usage.findById(id).get().outcome shouldBe "COMPLETED"
        usage.count() shouldBe 1
    }

    @Test
    fun `다른 고객 대상 입력은 무시하고 키의 고객에만 기록하며 미허용 모델은 차단한다`() {
        // given
        val a = setup("a"); val b = setup("b")
        val issued = issue(IssueServingApiKeyCommand(a.first.id, a.second, "test", 90))
        // when then
        post("/requests", mapOf("keyId" to issued.key.id, "model" to "denied")).andExpect { status { isForbidden() } }
        post("/requests", mapOf("keyId" to issued.key.id, "model" to "shared", "clusterId" to b.first.clusterId)).andExpect { status { isOk() } }
        usage.findAll().single().clusterId shouldBe a.first.clusterId
    }

    @Test
    fun `음수 토큰과 잘못된 상태를 거절하고 사용량 누락을 영으로 추정하지 않는다`() {
        // given
        val s = setup("a")
        val issued = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90))
        post("/requests", mapOf("keyId" to issued.key.id, "model" to "shared")).andExpect { status { isOk() } }
        val id = usage.findAll().single().id
        // when then
        post("/requests/${UUID.randomUUID()}/finish", finish()).andExpect { status { isNotFound() } }
        post("/requests/$id/finish", finish() + ("status" to 0)).andExpect { status { isBadRequest() } }
        post("/requests/$id/finish", finish() + ("status" to 42)).andExpect { status { isBadRequest() } }
        post("/requests/$id/finish", finish() + ("inputTokens" to -1)).andExpect { status { isBadRequest() } }
        post("/requests/$id/finish", finish() + ("outcome" to "IN_PROGRESS")).andExpect { status { isBadRequest() } }
        post("/requests/$id/finish", finish() + mapOf("tokensKnown" to false, "inputTokens" to null, "outputTokens" to null)).andExpect { status { isNoContent() } }
        usage.findById(id).get().let { it.usageMissing shouldBe true; it.inputTokens shouldBe null }
    }

    private fun post(path: String, body: Any, serviceToken: String = "test-gateway-token") = mockMvc.post("/internal/serving-gateway$path") {
        header("X-Kukie-Internal-Token", serviceToken); contentType = MediaType.APPLICATION_JSON; content = body.toJson()
    }

    private fun finish(): Map<String, Any?> = mapOf("outcome" to "COMPLETED", "status" to 200, "tokensKnown" to true, "inputTokens" to 3, "outputTokens" to 7)

    private fun setup(label: String): Pair<ServingAccess, UUID> {
        val admin = loggedInUser(UserFixture.user(email = "$label-admin@example.com"))
        val user = loggedInUser(UserFixture.user(email = "$label-user@example.com"))
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team, name = label))
        val access = accesses.save(ServingAccess(cluster.id, user.user.email, admin.user.id, utcNow().plusDays(7)).also { it.accept(user.user.id, user.user.email, utcNow()) })
        endpoints.save(ServingEndpoint(cluster.id, "https://counter.example/v1", "unused-test-ciphertext", listOf("shared")))
        return access to user.user.id
    }
}
