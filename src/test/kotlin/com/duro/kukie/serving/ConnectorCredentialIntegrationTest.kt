package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.IssueConnectorCredentialCommand
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.domain.ConnectorCredential
import com.duro.kukie.serving.domain.ServingEndpoint
import com.duro.kukie.serving.domain.ServingEndpointRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import io.kotest.assertions.throwables.shouldThrow
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post

@TestPropertySource(properties = ["internal.gateway-token=test-gateway-token"])
class ConnectorCredentialIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var credentials: ConnectorCredentials
    @Autowired private lateinit var issue: IssueConnectorCredentialService
    @Autowired private lateinit var gatewayPermission: GatewayKeyPermission
    @Autowired private lateinit var endpoints: ServingEndpointRepository
    @Autowired private lateinit var revoke: RevokeConnectorCredentialService

    @Test
    fun `등록 상한에서 새 대상은 거절해도 기존 대상은 회전할 수 있다`() {
        // given
        val admin = loggedInUser(UserFixture.user())
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val targets = clusters.saveAll((0..256).map { ClusterFixture.cluster(admin.user.id, team, name = "capacity-$it") })
        for (cluster in targets.take(256)) credentials.save(ConnectorCredential(cluster.id, "a".repeat(64), listOf("shared"), utcNow().plusHours(1)))
        // when then
        shouldThrow<ServingAccessException> { issue(IssueConnectorCredentialCommand(targets.last().id, admin.user.id, listOf("shared"))) }
            .errorCode shouldBe ServingErrorCode.SERVING_CONNECTOR_LIMIT_REACHED
        issue(IssueConnectorCredentialCommand(targets.first().id, admin.user.id, listOf("shared")))
        credentials.active(utcNow()).size shouldBe 256
    }

    @Test
    fun `관리자만 등록하며 원문은 한 번만 반환하고 내부 조회는 해시만 반환한다`() {
        // given
        val admin = loggedInUser(UserFixture.user())
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team))
        // when
        val body = mockMvc.post("/clusters/${cluster.id}/serving/connector-credential") {
            authorization(admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = mapOf("models" to listOf("shared")).toJson()
        }.andExpect { status { isCreated() }; header { string("Cache-Control", "no-store") }; jsonPath("$.token") { isString() } }.andReturn().response.contentAsString
        // then
        val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        token.length shouldBe 64
        credentials.find(cluster.id)!!.tokenHash shouldNotBe token
        mockMvc.post("/internal/serving-gateway/connector-bindings") {
            header("X-Kukie-Internal-Token", "test-gateway-token")
        }.andExpect { status { isOk() }; jsonPath("$.bindings[0].clusterId") { value(cluster.id.toString()) }; jsonPath("$.bindings[0].token") { doesNotExist() } }
        val other = loggedInUser(UserFixture.user(email = "other@example.com"))
        mockMvc.post("/clusters/${cluster.id}/serving/connector-credential") {
            authorization(other.accessToken); contentType = MediaType.APPLICATION_JSON; content = mapOf("models" to listOf("shared")).toJson()
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `회전은 기존 해시를 대체하고 폐기는 멱등이며 만료 자격은 내부 목록에 없다`() {
        // given
        val admin = loggedInUser(UserFixture.user())
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team))
        val command = IssueConnectorCredentialCommand(cluster.id, admin.user.id, listOf("shared"))
        issue(command)
        val old = credentials.find(cluster.id)!!.tokenHash
        endpoints.save(ServingEndpoint(cluster.id, "https://legacy.example/v1", "fixture", listOf("legacy")))
        gatewayPermission.models(cluster.id) shouldBe listOf("shared")
        // when
        val replacement = issue(command)
        // then
        credentials.find(cluster.id)!!.tokenHash shouldNotBe old
        replacement.toString().contains(replacement.token) shouldBe false
        repeat(2) { revoke(cluster.id, admin.user.id) }
        credentials.active(utcNow()) shouldBe emptyList()
        gatewayPermission.models(cluster.id) shouldBe emptyList()
        mockMvc.post("/internal/serving-gateway/connector-bindings") {
            header("X-Kukie-Internal-Token", "test-gateway-token")
        }.andExpect { status { isOk() }; jsonPath("$.bindings.length()") { value(0) } }
        mockMvc.post("/internal/serving-gateway/connector-bindings") {
            header("X-Kukie-Internal-Token", "test-internal-token")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `등록 입력을 검증하고 클러스터 삭제는 자격도 지운다`() {
        // given
        val admin = loggedInUser(UserFixture.user())
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team))
        // when then
        for (models in listOf(emptyList(), listOf("bad model"), listOf("x".repeat(201)))) {
            mockMvc.post("/clusters/${cluster.id}/serving/connector-credential") {
                authorization(admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = mapOf("models" to models).toJson()
            }.andExpect { status { isBadRequest() } }
        }
        issue(IssueConnectorCredentialCommand(cluster.id, admin.user.id, listOf("shared")))
        mockMvc.delete("/clusters/${cluster.id}") { authorization(admin.accessToken) }.andExpect { status { isNoContent() } }
        credentials.find(cluster.id) shouldBe null
    }
}
