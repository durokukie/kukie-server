package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.IssueConnectorCredentialCommand
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@TestPropertySource(properties = ["internal.gateway-token=test-gateway-token"])
class ServingModelAccessIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var issue: IssueConnectorCredentialService
    @Autowired private lateinit var credentials: ConnectorCredentials
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var keys: IssueServingApiKeyService
    @Autowired private lateinit var info: ServingInfo

    @Test
    fun `차단 모델은 사용자 정보와 게이트웨이 허용 목록에서 사라지고 자격 회전 후에도 차단된다`() {
        val admin = loggedInUser(UserFixture.user(email = "admin@example.com"))
        val user = loggedInUser(UserFixture.user(email = "user@example.com"))
        val team = savedTeam(admin.user.id to TeamRole.ADMIN, user.user.id to TeamRole.MEMBER)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team, name = "a"))
        issue(IssueConnectorCredentialCommand(cluster.id, admin.user.id, listOf("model-a", "model-b")))
        val access = accesses.save(ServingAccess(cluster.id, user.user.email, admin.user.id, utcNow().plusDays(7)).also { it.accept(user.user.id, user.user.email, utcNow()) })
        val key = keys(IssueServingApiKeyCommand(access.id, user.user.id, "test", 90))
        val path = "/clusters/${cluster.id}/serving/model-access"
        val hash = credentials.find(cluster.id)!!.tokenHash
        mockMvc.patch(path) { authorization(admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = """{"model":"model-a","enabled":false}""" }.andExpect { status { isNoContent() } }
        credentials.find(cluster.id)!!.tokenHash shouldBe hash
        info.of(cluster.id).models shouldBe listOf("model-b")
        info.of(cluster.id, true).modelStatuses.first().enabled shouldBe false
        mockMvc.post("/internal/serving-gateway/key-snapshot") {
            header("X-Kukie-Internal-Token", "test-gateway-token"); contentType = MediaType.APPLICATION_JSON; content = "{}"
        }.andExpect {
            status { isOk() }; jsonPath("$.keys[0].keyId") { value(key.key.id.toString()) }
            jsonPath("$.clusterModels.${cluster.id}[0]") { value("model-b") }; jsonPath("$.clusterModels.${cluster.id}.length()") { value(1) }
        }
        issue(IssueConnectorCredentialCommand(cluster.id, admin.user.id, listOf("model-a", "model-b")))
        info.of(cluster.id).models shouldBe listOf("model-b")
        mockMvc.patch(path) { authorization(admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = """{"model":"model-a","enabled":true}""" }.andExpect { status { isNoContent() } }
        info.of(cluster.id).models shouldBe listOf("model-a", "model-b")
        mockMvc.patch(path) { authorization(admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = """{"model":"model-a","enabled":false}""" }.andExpect { status { isNoContent() } }
        mockMvc.patch(path) { authorization(admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = """{"model":"model-b","enabled":false}""" }.andExpect { status { isNoContent() } }
        info.of(cluster.id).models shouldBe emptyList()
    }

    @Test
    fun `관리자만 변경 가능하며 미등록 모델과 불완전한 요청은 거절한다`() {
        val admin = loggedInUser(UserFixture.user(email = "owner@example.com"))
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val outsider = loggedInUser(UserFixture.user(email = "outside@example.com"))
        val team = savedTeam(admin.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team))
        issue(IssueConnectorCredentialCommand(cluster.id, admin.user.id, listOf("model-a")))
        val path = "/clusters/${cluster.id}/serving/model-access"
        mockMvc.patch(path) { authorization(member.accessToken); contentType = MediaType.APPLICATION_JSON; content = """{"model":"model-a","enabled":false}""" }.andExpect { status { isForbidden() } }
        mockMvc.get(path) { authorization(outsider.accessToken) }.andExpect { status { isNotFound() } }
        for (body in listOf("""{"model":"unknown","enabled":false}""", """{"model":"model-a"}""", """{"model":"","enabled":false}""")) {
            mockMvc.patch(path) { authorization(admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = body }.andExpect { status { isBadRequest() } }
        }
        info.of(cluster.id).models shouldBe listOf("model-a")
    }
}
