package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.NamespaceStatus
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.ClusterStatus
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.support.FakeEksAccessGateway
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.jayway.jsonpath.JsonPath
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

    @Autowired
    private lateinit var fakeEksAccessGateway: FakeEksAccessGateway

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
            jsonPath("$.hasGpu") { value(null) }
            jsonPath("$.credential.token") { value("secret-token") }
        }
    }

    @Test
    fun `EKS 클러스터는 고객 역할로 새로 만든 토큰을 같은 모양으로 준다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, EKS_CREDENTIAL, provider = ClusterProvider.EKS)

        // when
        val response = mockMvc.get("/internal/clusters/${cluster.id}/connection?expectedFingerprint=${cluster.fingerprint}") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.clusterId") { value(cluster.id.toString()) }
            jsonPath("$.apiServer") { value(cluster.apiServer) }
            jsonPath("$.caData") { value(ClusterFixture.CA) }
            jsonPath("$.insecure") { value(false) }
        }.andReturn().response.contentAsString

        // then
        JsonPath.read<Map<String, Any?>>(response, "$.credential") shouldBe mapOf("token" to FakeEksAccessGateway.tokenFor("demo"))
        fakeEksAccessGateway.targets.toSet() shouldBe setOf(AwsTarget(accountId = "123456789012", externalId = EXTERNAL_ID))
        fakeEksAccessGateway.namespaceChecks shouldBe listOf(
            FakeEksAccessGateway.NamespaceCheck(ClusterFixture.DEFAULT_NAMESPACE, waitForGrant = false),
        )
    }

    @Test
    fun `EKS 클러스터의 namespace 가 사라졌으면 연결 정보를 주지 않는다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, EKS_CREDENTIAL, provider = ClusterProvider.EKS)
        fakeEksAccessGateway.namespaceStatus = NamespaceStatus.MISSING

        // when & then
        mockMvc.get("/internal/clusters/${cluster.id}/connection?expectedFingerprint=${cluster.fingerprint}") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.NAMESPACE_NOT_FOUND.code) }
            jsonPath("$.credential") { doesNotExist() }
        }
    }

    @Test
    fun `EKS 클러스터의 namespace 를 확인하지 못해도 연결 정보를 준다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, EKS_CREDENTIAL, provider = ClusterProvider.EKS)
        fakeEksAccessGateway.namespaceStatus = NamespaceStatus.UNKNOWN

        // when & then
        mockMvc.get("/internal/clusters/${cluster.id}/connection?expectedFingerprint=${cluster.fingerprint}") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.credential.token") { value(FakeEksAccessGateway.tokenFor("demo")) }
        }
    }

    @Test
    fun `EKS 클러스터의 namespace 를 권한이 아직 퍼지지 않아 확인하지 못해도 연결 정보를 준다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, EKS_CREDENTIAL, provider = ClusterProvider.EKS)
        fakeEksAccessGateway.namespaceStatus = NamespaceStatus.GRANT_PENDING

        // when & then
        mockMvc.get("/internal/clusters/${cluster.id}/connection?expectedFingerprint=${cluster.fingerprint}") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.credential.token") { value(FakeEksAccessGateway.tokenFor("demo")) }
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

    @Test
    fun `GPU 여부를 남기고 연결 정보와 클러스터 응답에 싣는다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, mapOf("token" to "t"))

        // when
        mockMvc.patch("/internal/clusters/${cluster.id}/gpu") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("hasGpu" to true).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cluster.id.toString()) }
            jsonPath("$.hasGpu") { value(true) }
        }

        // then
        clusterRepository.findByIdOrNull(cluster.id)!!.hasGpu shouldBe true
        mockMvc.get("/internal/clusters/${cluster.id}/connection") {
            internalToken()
            authorization(me.accessToken)
        }.andExpect {
            jsonPath("$.hasGpu") { value(true) }
        }
        mockMvc.get("/clusters/${cluster.id}") {
            authorization(me.accessToken)
        }.andExpect {
            jsonPath("$.hasGpu") { value(true) }
        }
    }

    @Test
    fun `GPU 여부가 비어 있으면 400 이다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, mapOf("token" to "t"))

        // when & then
        mockMvc.patch("/internal/clusters/${cluster.id}/gpu") {
            internalToken()
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("hasGpu" to null).toJson()
        }.andExpect {
            status { isBadRequest() }
        }
        clusterRepository.findByIdOrNull(cluster.id)!!.hasGpu shouldBe null
    }

    @Test
    fun `내부 토큰 없이는 GPU 여부를 남길 수 없다`() {
        // given
        val me = loggedInUser()
        val cluster = savedCluster(me.user.id, mapOf("token" to "t"))

        // when & then
        mockMvc.patch("/internal/clusters/${cluster.id}/gpu") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("hasGpu" to true).toJson()
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    private fun savedCluster(
        registeredBy: UUID,
        credential: Map<String, String>,
        teamId: UUID = savedTeam(registeredBy to TeamRole.ADMIN),
        name: String = ClusterFixture.DEFAULT_NAME,
        provider: ClusterProvider = ClusterProvider.GENERIC,
    ): Cluster = clusterRepository.save(
        ClusterFixture.cluster(
            registeredBy = registeredBy,
            teamId = teamId,
            name = name,
            credential = credentialCipher.encrypt(credential),
            fingerprint = "a".repeat(64),
            provider = provider,
        ),
    )

    companion object {
        private const val EXTERNAL_ID = "kukie-0123456789abcdef"
        private val EKS_CREDENTIAL = mapOf(
            "type" to "eks",
            "accountId" to "123456789012",
            "externalId" to EXTERNAL_ID,
            "region" to "ap-northeast-2",
            "clusterName" to "demo",
        )
    }
}
