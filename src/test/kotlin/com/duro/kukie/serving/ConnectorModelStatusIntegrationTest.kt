package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.IssueConnectorCredentialCommand
import com.duro.kukie.serving.application.port.`in`.UpdateServingModelAccessCommand
import com.duro.kukie.serving.domain.ConnectorModelStatuses
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@TestPropertySource(properties = ["internal.gateway-token=test-gateway-token"])
class ConnectorModelStatusIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var credentials: ConnectorCredentials
    @Autowired private lateinit var issue: IssueConnectorCredentialService
    @Autowired private lateinit var revoke: RevokeConnectorCredentialService
    @Autowired private lateinit var info: ServingInfo
    @Autowired private lateinit var statuses: ConnectorModelStatuses

    @Autowired private lateinit var modelAccess: GetServingModelAccessService
    @Autowired private lateinit var setModelAccess: UpdateServingModelAccessService

    private fun report(cluster: UUID, hash: String, checked: Instant?, connected: Boolean = true, ready: List<String> = listOf("ready", "not-allowed")) =
        mockMvc.post("/internal/serving-gateway/model-status") {
            header("X-Kukie-Internal-Token", "test-gateway-token")
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("statuses" to listOf(mapOf("clusterId" to cluster, "tokenSha256" to hash, "connected" to connected, "checkedAt" to checked?.toString(), "readyModels" to ready))).toJson()
        }.andExpect { status { isNoContent() } }

    @Test
    fun `상태는 현재 자격과 허용 모델에 한정되고 회전 폐기와 오래된 확인은 준비로 표시하지 않는다`() {
        val admin = loggedInUser(UserFixture.user())
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val a = clusters.save(ClusterFixture.cluster(admin.user.id, team, name = "a"))
        val b = clusters.save(ClusterFixture.cluster(admin.user.id, team, name = "b"))
        issue(IssueConnectorCredentialCommand(a.id, admin.user.id, listOf("ready", "stopped")))
        issue(IssueConnectorCredentialCommand(b.id, admin.user.id, listOf("ready")))
        val hash = credentials.find(a.id)!!.tokenHash
        info.of(a.id).modelStatuses.map { it.state } shouldBe listOf("UNKNOWN", "UNKNOWN")
        report(a.id, hash, Instant.now())
        info.of(a.id).modelStatuses.map { it.state } shouldBe listOf("AVAILABLE", "UNAVAILABLE")
        info.of(a.id).models shouldBe listOf("ready", "stopped")
        (info.of(a.id).modelStatusValidForSeconds in 40..45) shouldBe true
        info.of(b.id).modelStatuses.single().state shouldBe "UNKNOWN"
        report(a.id, hash, Instant.now(), false)
        info.of(a.id).connectionStatus shouldBe "OFFLINE"
        info.of(a.id).modelStatuses.first().state shouldBe "UNAVAILABLE"
        report(a.id, hash, Instant.now().minusSeconds(46))
        info.of(a.id).modelStatuses.first().state shouldBe "UNKNOWN"
        issue(IssueConnectorCredentialCommand(a.id, admin.user.id, listOf("ready")))
        report(a.id, hash, Instant.now())
        info.of(a.id).modelStatuses.single().state shouldBe "UNKNOWN"
        report(a.id, credentials.find(a.id)!!.tokenHash, Instant.now())
        info.of(a.id).modelStatuses.single().state shouldBe "AVAILABLE"
        revoke(a.id, admin.user.id)
        info.of(a.id).modelStatuses.single().state shouldBe "UNAVAILABLE"
        clusters.deleteById(a.id)
        statuses.find(a.id) shouldBe null
    }

    @Test
    fun `보고는 내부 전용 인증과 모델 항목 검증을 적용한다`() {
        mockMvc.post("/internal/serving-gateway/model-status") {
            contentType = MediaType.APPLICATION_JSON; content = "{\"statuses\":[]}"
        }.andExpect { status { isUnauthorized() } }
        for (body in listOf("{\"statuses\":[null]}", "{\"statuses\":null}", "{\"statuses\":[{\"clusterId\":\"${UUID.randomUUID()}\",\"tokenSha256\":\"${"a".repeat(64)}\",\"connected\":true,\"checkedAt\":null,\"readyModels\":[null]}]}")) {
            mockMvc.post("/internal/serving-gateway/model-status") {
                header("X-Kukie-Internal-Token", "test-gateway-token")
                contentType = MediaType.APPLICATION_JSON; content = body
            }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `새로 뜬 모델은 공개 설정 목록에 차단 상태로 올라오고 켜면 쓸 수 있다`() {
        val admin = loggedInUser(UserFixture.user())
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team, name = "discover"))
        issue(IssueConnectorCredentialCommand(cluster.id, admin.user.id, listOf("old")))
        val hash = credentials.find(cluster.id)!!.tokenHash

        report(cluster.id, "b".repeat(64), Instant.now(), ready = listOf("old", "spoofed"))
        report(cluster.id, hash, null, ready = listOf("old", "stale"))
        report(cluster.id, hash, Instant.now(), connected = false, ready = listOf("old", "offline"))
        credentials.find(cluster.id)!!.models shouldBe listOf("old")

        report(cluster.id, hash, Instant.now(), ready = listOf("old", "Qwen/Qwen3-32B"))
        modelAccess(cluster.id, admin.user.id) shouldBe listOf(ServingModelAccess("old", true), ServingModelAccess("Qwen/Qwen3-32B", false))
        info.of(cluster.id).models shouldBe listOf("old")

        setModelAccess(UpdateServingModelAccessCommand(cluster.id, admin.user.id, "Qwen/Qwen3-32B", true))
        report(cluster.id, hash, Instant.now(), ready = listOf("old", "Qwen/Qwen3-32B"))
        info.of(cluster.id).modelStatuses.map { it.id to it.state } shouldBe listOf("old" to "AVAILABLE", "Qwen/Qwen3-32B" to "AVAILABLE")
    }
}
