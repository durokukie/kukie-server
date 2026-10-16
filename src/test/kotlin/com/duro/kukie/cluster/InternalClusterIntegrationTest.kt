package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.ClusterStatus
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.LocalDateTime
import java.util.UUID

class InternalClusterIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var credentialCipher: CredentialCipher

    @Test
    fun `클러스터 연결 정보를 자격증명과 함께 준다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, mapOf("token" to "secret-token"))

        // when & then
        mockMvc.get("/internal/clusters/${cluster.id}/connection?expectedFingerprint=${cluster.fingerprint}") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.clusterId") { value(cluster.id.toString()) }
            jsonPath("$.name") { value(cluster.name) }
            jsonPath("$.apiServer") { value(cluster.apiServer) }
            jsonPath("$.caData") { value(ClusterFixture.CA) }
            jsonPath("$.insecure") { value(false) }
            jsonPath("$.contextName") { value(ClusterFixture.DEFAULT_CONTEXT) }
            jsonPath("$.namespace") { value(ClusterFixture.DEFAULT_NAMESPACE) }
            jsonPath("$.fingerprint") { value(cluster.fingerprint) }
            jsonPath("$.credential.token") { value("secret-token") }
        }
    }

    @Test
    fun `대화가 기억하는 클러스터와 다르면 연결 정보를 주지 않는다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, mapOf("token" to "t"))

        // when & then
        mockMvc.get("/internal/clusters/${cluster.id}/connection?expectedFingerprint=다른-지문") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.CLUSTER_CHANGED.code) }
        }
    }

    @Test
    fun `연결 확인 결과를 남긴다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, mapOf("token" to "t"))
        val checkedAt = LocalDateTime.of(2026, 10, 14, 9, 0, 0)

        // when & then
        mockMvc.patch("/internal/clusters/${cluster.id}/status") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("status" to "connected", "checkedAt" to "2026-10-14T09:00:00").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cluster.id.toString()) }
            jsonPath("$.status") { value("connected") }
            jsonPath("$.lastCheckedAt") { value("2026-10-14T09:00:00") }
        }
        val saved = clusterRepository.findByIdOrNull(cluster.id)!!
        saved.status shouldBe ClusterStatus.CONNECTED
        saved.lastCheckedAt shouldBe checkedAt
    }

    private fun savedCluster(
        registeredBy: UUID,
        credential: Map<String, String>,
        teamId: UUID = savedTeam(registeredBy to TeamRole.ADMIN),
        name: String = ClusterFixture.DEFAULT_NAME,
    ): Cluster = clusterRepository.save(
        ClusterFixture.cluster(
            registeredBy = registeredBy,
            teamId = teamId,
            name = name,
            credential = credentialCipher.encrypt(credential),
            fingerprint = "a".repeat(64),
        ),
    )
}
