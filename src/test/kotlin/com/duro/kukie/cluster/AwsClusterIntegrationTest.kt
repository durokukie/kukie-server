package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.presentation.dto.request.GetEksCandidatesRequest
import com.duro.kukie.cluster.presentation.dto.request.RegisterEksClusterRequest
import com.duro.kukie.cluster.presentation.dto.request.StartAwsConnectionRequest
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.support.FakeEksAccessGateway
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_CLUSTER_NAME
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_ENDPOINT
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_REGION
import com.duro.kukie.support.FakeHostResolver
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import java.net.InetAddress
import java.util.UUID

class AwsClusterIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var awsConnectionRepository: AwsConnectionRepository

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var credentialCipher: CredentialCipher

    @Autowired
    private lateinit var fakeEksAccessGateway: FakeEksAccessGateway

    @Autowired
    private lateinit var fakeHostResolver: FakeHostResolver

    // ── AWS 연결 시작 ────────────────────────────────────────────

    @Test
    fun `관리자가 AWS 연결을 시작하면 ExternalId 와 CloudFormation 빠른 생성 링크를 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when
        val response = mockMvc.post("/clusters/aws/connection") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = StartAwsConnectionRequest(teamId).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.templateUrl") { value(TEMPLATE_URL) }
            jsonPath("$.stackName") { value("kukie-access") }
        }.andReturn().response.contentAsString

        // then
        val externalId = JsonPath.read<String>(response, "$.externalId")
        externalId shouldMatch Regex("kukie-[0-9a-f]{16}")
        JsonPath.read<String>(response, "$.quickCreateUrl") shouldBe
            "https://console.aws.amazon.com/cloudformation/home?region=ap-northeast-2#/stacks/quickcreate" +
            "?templateURL=https%3A%2F%2Fkukie-templates-320343233643-ap-northeast-2.s3.ap-northeast-2.amazonaws.com" +
            "%2Fkukie-access%2Fv1.yaml&stackName=kukie-access&param_ExternalId=$externalId"
        val stored = awsConnectionRepository.findByTeamId(teamId)!!
        stored.externalId shouldBe externalId
        stored.createdBy shouldBe admin.user.id
    }

    @Test
    fun `AWS 연결을 다시 시작하면 같은 ExternalId 를 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val first = startConnection(admin, teamId)

        // when
        val second = startConnection(admin, teamId)

        // then
        second shouldBe first
        awsConnectionRepository.count() shouldBe 1
    }

    @Test
    fun `팀마다 다른 ExternalId 를 준다`() {
        // given
        val admin = loggedInUser()
        val firstTeam = savedTeam(admin.user.id to TeamRole.ADMIN)
        val secondTeam = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        (startConnection(admin, firstTeam) == startConnection(admin, secondTeam)) shouldBe false
    }

    @Test
    fun `구성원은 AWS 연결을 시작할 수 없다`() {
        // given
        val member = loggedInUser()
        val teamId = savedTeam(member.user.id to TeamRole.MEMBER)

        // when & then
        mockMvc.post("/clusters/aws/connection") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = StartAwsConnectionRequest(teamId).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        awsConnectionRepository.count() shouldBe 0
    }

    // ── EKS 후보 조회 ────────────────────────────────────────────

    @Test
    fun `연결을 시작한 팀은 설정된 리전의 EKS 클러스터를 본다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(teamId = teamId, accountId = ACCOUNT_ID).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value(DEFAULT_CLUSTER_NAME) }
            jsonPath("$[0].region") { value(DEFAULT_REGION) }
            jsonPath("$[0].status") { value("ACTIVE") }
            jsonPath("$[0].version") { value("1.33") }
        }
        fakeEksAccessGateway.targets shouldBe listOf(AwsTarget(accountId = ACCOUNT_ID, externalId = externalId))
    }

    @Test
    fun `AWS 연결을 시작하지 않은 팀은 EKS 후보를 볼 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(teamId = teamId, accountId = ACCOUNT_ID).toJson()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_CONNECTION_NOT_STARTED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `계정 ID 는 12자리 숫자여야 한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(teamId = teamId, accountId = "12345").toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
    }

    @Test
    fun `구성원은 EKS 후보를 볼 수 없다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(teamId = teamId, accountId = ACCOUNT_ID).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `고객 계정의 역할을 맡지 못하면 알려 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        startConnection(admin, teamId)
        fakeEksAccessGateway.failure = AwsRoleNotAssumableException()

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(teamId = teamId, accountId = ACCOUNT_ID).toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_ROLE_NOT_ASSUMABLE.code) }
        }
    }

    // ── EKS 클러스터 등록 ─────────────────────────────────────────

    @Test
    fun `관리자는 EKS 클러스터를 등록하고 자격증명 대신 연결 정보를 암호화해 둔다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)

        // when
        val response = mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(teamId).toJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.teamId") { value(teamId.toString()) }
            jsonPath("$.registeredBy") { value(admin.user.id.toString()) }
            jsonPath("$.name") { value("운영 EKS") }
            jsonPath("$.provider") { value("EKS") }
            jsonPath("$.apiServer") { value(DEFAULT_ENDPOINT.endpoint) }
            jsonPath("$.context") { value(DEFAULT_CLUSTER_NAME) }
            jsonPath("$.namespace") { value("kukie-demo") }
            jsonPath("$.status") { value("disconnected") }
            jsonPath("$.insecure") { value(false) }
            jsonPath("$.fingerprint") { value(Cluster.fingerprintOf(DEFAULT_ENDPOINT.endpoint, DEFAULT_ENDPOINT.caData)) }
        }.andReturn().response.contentAsString

        // then
        response shouldNotContain externalId
        JsonPath.read<Map<String, Any?>>(response, "$").keys shouldBe setOf(
            "id", "teamId", "registeredBy", "name", "provider", "apiServer", "context", "namespace", "fingerprint",
            "status", "insecure", "lastCheckedAt", "createdAt", "updatedAt",
        )
        val target = AwsTarget(accountId = ACCOUNT_ID, externalId = externalId)
        fakeEksAccessGateway.registrations shouldBe listOf(
            FakeEksAccessGateway.Registration(target, DEFAULT_REGION, DEFAULT_CLUSTER_NAME, "kukie-demo"),
        )
        val stored = clusterRepository.findByIdOrNull(UUID.fromString(JsonPath.read(response, "$.id")))!!
        stored.provider shouldBe ClusterProvider.EKS
        stored.caData shouldBe DEFAULT_ENDPOINT.caData
        credentialCipher.decrypt(stored.credential) shouldBe mapOf(
            "type" to "eks",
            "accountId" to ACCOUNT_ID,
            "externalId" to externalId,
            "region" to DEFAULT_REGION,
            "clusterName" to DEFAULT_CLUSTER_NAME,
        )
    }

    @Test
    fun `패턴이나 시스템 namespace 로는 EKS 클러스터를 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        startConnection(admin, teamId)

        // when & then
        for (namespace in listOf("kukie-*", "kukie-?", "kube-system", "Kukie", "-kukie", "a".repeat(64))) {
            mockMvc.post("/clusters/aws") {
                authorization(admin.accessToken)
                contentType = MediaType.APPLICATION_JSON
                content = registerRequest(teamId, namespace = namespace).toJson()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value(ClusterErrorCode.INVALID_NAMESPACE.code) }
            }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
        clusterRepository.count() shouldBe 0
    }

    @Test
    fun `설정하지 않은 리전의 EKS 클러스터는 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(teamId, region = "us-east-1").toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_REGION_NOT_SUPPORTED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `AWS 연결을 시작하지 않은 팀은 EKS 클러스터를 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(teamId).toJson()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_CONNECTION_NOT_STARTED.code) }
        }
    }

    @Test
    fun `사설 주소로 풀리는 EKS 엔드포인트는 등록하지 않고 접근 권한도 만들지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        startConnection(admin, teamId)
        fakeHostResolver.addresses = listOf(InetAddress.getByName("10.0.0.5"))

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(teamId).toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.EKS_ENDPOINT_REJECTED.code) }
            jsonPath("$.message") { value(containsString("사설")) }
        }
        fakeEksAccessGateway.registrations.shouldBeEmpty()
        clusterRepository.count() shouldBe 0
    }

    @Test
    fun `허용하지 않은 namespace 라 AWS 가 거부하면 등록하지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        startConnection(admin, teamId)
        fakeEksAccessGateway.failure = EksAccessDeniedException()

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(teamId, namespace = "payments").toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.EKS_ACCESS_DENIED.code) }
        }
        clusterRepository.count() shouldBe 0
    }

    @Test
    fun `구성원은 EKS 클러스터를 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(teamId).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `같은 팀에 같은 이름의 클러스터가 있으면 EKS 클러스터를 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        startConnection(admin, teamId)
        clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.user.id, teamId = teamId, name = "운영 EKS"))

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(teamId).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.CLUSTER_NAME_TAKEN.code) }
        }
    }

    private fun startConnection(user: LoggedInUser, teamId: UUID): String {
        val response = mockMvc.post("/clusters/aws/connection") {
            authorization(user.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = StartAwsConnectionRequest(teamId).toJson()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        return JsonPath.read(response, "$.externalId")
    }

    private fun registerRequest(
        teamId: UUID,
        region: String = DEFAULT_REGION,
        namespace: String = "kukie-demo",
    ) = RegisterEksClusterRequest(
        teamId = teamId,
        accountId = ACCOUNT_ID,
        region = region,
        clusterName = DEFAULT_CLUSTER_NAME,
        namespace = namespace,
        name = "운영 EKS",
    )

    companion object {
        private const val ACCOUNT_ID = "123456789012"
        private const val TEMPLATE_URL =
            "https://kukie-templates-320343233643-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/kukie-access/v1.yaml"
    }
}
