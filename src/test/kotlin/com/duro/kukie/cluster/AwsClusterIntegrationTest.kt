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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.net.InetAddress
import java.net.URLEncoder
import java.time.LocalDateTime
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
        val response = mockMvc.post("/clusters/aws/connections") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = StartAwsConnectionRequest(teamId).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.templateUrl") { value(TEMPLATE_URL) }
        }.andReturn().response.contentAsString

        // then
        val externalId = JsonPath.read<String>(response, "$.externalId")
        externalId shouldMatch Regex("kukie-[0-9a-f]{16}")
        JsonPath.read<String>(response, "$.stackName") shouldBe "kukie-access-$externalId"
        JsonPath.read<String>(response, "$.quickCreateUrl") shouldBe
            "https://console.aws.amazon.com/cloudformation/home?region=ap-northeast-2#/stacks/quickcreate" +
            "?templateURL=https%3A%2F%2Fkukie-templates-320343233643-ap-northeast-2.s3.ap-northeast-2.amazonaws.com" +
            "%2Fkukie-access%2Fv3.yaml&stackName=kukie-access-$externalId&param_ExternalId=$externalId"
        val stored = awsConnectionRepository.findByExternalId(externalId)!!
        stored.id.toString() shouldBe JsonPath.read(response, "$.id")
        stored.teamId shouldBe teamId
        stored.createdBy shouldBe admin.user.id
    }

    @Test
    fun `스택 알림을 받기 전에 다시 시작하면 같은 연결을 준다`() {
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
    fun `연결된 계정이 있으면 다른 AWS 계정을 연결할 새 ExternalId 를 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val first = startConnection(admin, teamId)
        connect(first, stackId = null)

        // when
        val second = startConnection(admin, teamId)

        // then
        (second == first) shouldBe false
        awsConnectionRepository.findAllByTeamIdOrderByCreatedAt(teamId).map { it.externalId } shouldBe listOf(first, second)
    }

    @Test
    fun `해제한 연결이 있어도 다른 AWS 계정을 연결할 새 ExternalId 를 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val first = startConnection(admin, teamId)
        connect(first, stackId = null)
        disconnect(admin, first)

        // when & then
        (startConnection(admin, teamId) == first) shouldBe false
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
        mockMvc.post("/clusters/aws/connections") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = StartAwsConnectionRequest(teamId).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        awsConnectionRepository.count() shouldBe 0
    }

    // ── AWS 연결 목록 ────────────────────────────────────────────

    @Test
    fun `팀의 AWS 연결을 만든 순서로 계정마다 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val first = startConnection(admin, teamId)
        connect(first, stackId = null)
        val second = startConnection(admin, teamId)
        connect(second, stackId = null, accountId = OTHER_ACCOUNT_ID)
        val pending = startConnection(admin, teamId)

        // when & then
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].id") { value(idOf(first).toString()) }
            jsonPath("$[0].externalId") { value(first) }
            jsonPath("$[0].accountId") { value(ACCOUNT_ID) }
            jsonPath("$[1].externalId") { value(second) }
            jsonPath("$[1].accountId") { value(OTHER_ACCOUNT_ID) }
            jsonPath("$[2].externalId") { value(pending) }
            jsonPath("$[2].accountId") { value(null) }
            jsonPath("$[2].quickCreateUrl") { value(containsString("param_ExternalId=$pending")) }
        }
    }

    @Test
    fun `연결을 시작하지 않은 팀의 AWS 연결 목록은 비어 있다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `구성원은 AWS 연결 목록을 볼 수 없다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        startConnection(admin, teamId)

        // when & then
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
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
            content = GetEksCandidatesRequest(connectionId = idOf(externalId), accountId = ACCOUNT_ID).toJson()
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
    fun `없는 AWS 연결로는 EKS 후보를 볼 수 없다`() {
        // given
        val admin = loggedInUser()
        savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(connectionId = UUID.randomUUID(), accountId = ACCOUNT_ID).toJson()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_CONNECTION_NOT_STARTED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `연결된 계정과 다른 계정으로는 EKS 후보를 볼 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        connect(externalId, stackId = null)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(connectionId = idOf(externalId), accountId = OTHER_ACCOUNT_ID).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_ACCOUNT_MISMATCH.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `다른 팀의 AWS 연결로는 EKS 후보를 볼 수 없다`() {
        // given
        val admin = loggedInUser()
        val stranger = loggedInUser(UserFixture.user(email = "stranger@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        savedTeam(stranger.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(stranger.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(connectionId = idOf(externalId), accountId = ACCOUNT_ID).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_MEMBER.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `계정 ID 는 12자리 숫자여야 한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(connectionId = idOf(externalId), accountId = "12345").toJson()
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
        val externalId = startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(connectionId = idOf(externalId), accountId = ACCOUNT_ID).toJson()
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
        val externalId = startConnection(admin, teamId)
        fakeEksAccessGateway.failure = AwsRoleNotAssumableException()

        // when & then
        mockMvc.post("/clusters/aws/candidates") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = GetEksCandidatesRequest(connectionId = idOf(externalId), accountId = ACCOUNT_ID).toJson()
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
            content = registerRequest(idOf(externalId)).toJson()
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
    fun `한 팀이 두 AWS 계정의 EKS 클러스터를 각 계정의 ExternalId 로 등록한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val first = startConnection(admin, teamId)
        connect(first, stackId = null)
        val second = startConnection(admin, teamId)
        connect(second, stackId = null, accountId = OTHER_ACCOUNT_ID)

        // when
        val firstId = registerEks(admin, first, namespace = "kukie-demo", name = "운영 EKS")
        val secondId = registerEks(admin, second, namespace = "kukie-demo", name = "스테이징 EKS", accountId = OTHER_ACCOUNT_ID)

        // then
        fakeEksAccessGateway.registrations.map { it.target } shouldBe listOf(
            AwsTarget(accountId = ACCOUNT_ID, externalId = first),
            AwsTarget(accountId = OTHER_ACCOUNT_ID, externalId = second),
        )
        listOf(firstId, secondId).map { id ->
            val credential = credentialCipher.decrypt(clusterRepository.findByIdOrNull(id)!!.credential)
            credential["accountId"] to credential["externalId"]
        } shouldBe listOf(ACCOUNT_ID to first, OTHER_ACCOUNT_ID to second)
    }

    @Test
    fun `연결된 계정과 다른 계정의 EKS 클러스터는 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        connect(externalId, stackId = null)

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(idOf(externalId), accountId = OTHER_ACCOUNT_ID).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_ACCOUNT_MISMATCH.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
        clusterRepository.count() shouldBe 0
    }

    @Test
    fun `패턴이나 시스템 namespace 로는 EKS 클러스터를 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)

        // when & then
        for (namespace in listOf("kukie-*", "kukie-?", "kube-system", "Kukie", "-kukie", "a".repeat(64))) {
            mockMvc.post("/clusters/aws") {
                authorization(admin.accessToken)
                contentType = MediaType.APPLICATION_JSON
                content = registerRequest(idOf(externalId), namespace = namespace).toJson()
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
        val externalId = startConnection(admin, teamId)

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(idOf(externalId), region = "us-east-1").toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_REGION_NOT_SUPPORTED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `없는 AWS 연결로는 EKS 클러스터를 등록할 수 없다`() {
        // given
        val admin = loggedInUser()
        savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(UUID.randomUUID()).toJson()
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
        val externalId = startConnection(admin, teamId)
        fakeHostResolver.addresses = listOf(InetAddress.getByName("10.0.0.5"))

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(idOf(externalId)).toJson()
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
        val externalId = startConnection(admin, teamId)
        fakeEksAccessGateway.failure = EksAccessDeniedException()

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(idOf(externalId), namespace = "payments").toJson()
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
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(connection.id).toJson()
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
        val externalId = startConnection(admin, teamId)
        clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.user.id, teamId = teamId, name = "운영 EKS"))

        // when & then
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(idOf(externalId)).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.CLUSTER_NAME_TAKEN.code) }
        }
        // 고객 계정은 건드리지 않는다
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    // ── 클러스터 구성 조회 ──────────────────────────────────────

    @Test
    fun `구성원은 EKS 클러스터의 애드온·노드 그룹·서브넷을 본다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        val externalId = startConnection(admin, teamId)
        val clusterId = registerEks(admin, externalId, namespace = "kukie-demo", name = "운영 EKS")

        // when
        val response = mockMvc.get("/clusters/$clusterId/setup") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ok") }
            jsonPath("$.kubernetesVersion") { value("1.31") }
            jsonPath("$.platformVersion") { value("eks.12") }
            jsonPath("$.endpointPublic") { value(true) }
            jsonPath("$.endpointPrivate") { value(false) }
            jsonPath("$.serviceCidr") { value("172.20.0.0/16") }
            jsonPath("$.addons[1].name") { value("vpc-cni") }
            jsonPath("$.addons[1].version") { value("v1.19.0-eksbuild.1") }
            jsonPath("$.addons[1].status") { value("ACTIVE") }
            jsonPath("$.nodegroups[0].name") { value("ng-1") }
            jsonPath("$.nodegroups[0].instanceTypes[0]") { value("t3.large") }
            jsonPath("$.nodegroups[0].capacityType") { value("ON_DEMAND") }
            jsonPath("$.nodegroups[0].desired") { value(2) }
            jsonPath("$.nodegroups[0].min") { value(1) }
            jsonPath("$.nodegroups[0].max") { value(3) }
            jsonPath("$.subnets[0].zone") { value("ap-northeast-2a") }
            jsonPath("$.subnets[0].cidr") { value("10.60.0.0/19") }
        }.andReturn().response.contentAsString

        // then
        JsonPath.read<Map<String, Any?>>(response, "$").keys shouldBe setOf(
            "status", "kubernetesVersion", "platformVersion", "endpointPublic", "endpointPrivate", "serviceCidr",
            "addons", "nodegroups", "subnets",
        )
        JsonPath.read<List<String>>(response, "$.nodegroups[0].zones") shouldBe listOf("ap-northeast-2a", "ap-northeast-2c")
        JsonPath.read<Map<String, Any?>>(response, "$.subnets[0]").keys shouldBe setOf("zone", "cidr")
        fakeEksAccessGateway.setupRequests shouldBe listOf(
            FakeEksAccessGateway.SetupRequest(
                AwsTarget(accountId = ACCOUNT_ID, externalId = externalId),
                DEFAULT_REGION,
                DEFAULT_CLUSTER_NAME,
            ),
        )
    }

    @Test
    fun `템플릿 v3 이전 역할이면 버전만 주고 권한이 필요하다고 알린다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val clusterId = registerEks(admin, externalId, namespace = "kukie-demo", name = "운영 EKS")
        fakeEksAccessGateway.setup = FakeEksAccessGateway.DEFAULT_SETUP.copy(inventory = null)

        // when & then
        mockMvc.get("/clusters/$clusterId/setup") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("permission_required") }
            jsonPath("$.kubernetesVersion") { value("1.31") }
            jsonPath("$.serviceCidr") { value("172.20.0.0/16") }
            jsonPath("$.addons.length()") { value(0) }
            jsonPath("$.nodegroups.length()") { value(0) }
            jsonPath("$.subnets.length()") { value(0) }
        }
    }

    @Test
    fun `kubeconfig 로 등록한 클러스터는 구성을 지원하지 않는다고 알린다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.user.id, teamId = teamId))

        // when & then
        mockMvc.get("/clusters/${cluster.id}/setup") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("unsupported") }
            jsonPath("$.kubernetesVersion") { value(null) }
            jsonPath("$.endpointPublic") { value(null) }
            jsonPath("$.addons.length()") { value(0) }
            jsonPath("$.nodegroups.length()") { value(0) }
            jsonPath("$.subnets.length()") { value(0) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `다른 팀의 클러스터 구성은 볼 수 없다`() {
        // given
        val admin = loggedInUser()
        val stranger = loggedInUser(UserFixture.user(email = "stranger@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val clusterId = registerEks(admin, externalId, namespace = "kukie-demo", name = "운영 EKS")
        fakeEksAccessGateway.targets.clear()

        // when & then
        mockMvc.get("/clusters/$clusterId/setup") {
            authorization(stranger.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.NOT_FOUND.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `고객 계정의 역할을 맡지 못하면 구성 대신 알려 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val clusterId = registerEks(admin, externalId, namespace = "kukie-demo", name = "운영 EKS")
        fakeEksAccessGateway.failure = AwsRoleNotAssumableException()

        // when & then
        mockMvc.get("/clusters/$clusterId/setup") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_ROLE_NOT_ASSUMABLE.code) }
        }
    }

    // ── EKS 클러스터 삭제 ────────────────────────────────────────

    @Test
    fun `EKS 클러스터를 지우면 고객 EKS 의 access entry 도 지운다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val clusterId = registerEks(admin, externalId, namespace = "kukie-demo", name = "운영 EKS")

        // when
        mockMvc.delete("/clusters/$clusterId") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        clusterRepository.existsById(clusterId) shouldBe false
        fakeEksAccessGateway.unregistrations shouldBe listOf(
            FakeEksAccessGateway.Unregistration(
                AwsTarget(accountId = ACCOUNT_ID, externalId = externalId),
                DEFAULT_REGION,
                DEFAULT_CLUSTER_NAME,
                emptySet(),
            ),
        )
    }

    @Test
    fun `같은 EKS 를 다른 namespace 로 연결해 두었으면 그 namespace 는 남긴다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val demo = registerEks(admin, externalId, namespace = "kukie-demo", name = "데모")
        registerEks(admin, externalId, namespace = "kukie-app", name = "앱")

        // when
        mockMvc.delete("/clusters/$demo") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        fakeEksAccessGateway.unregistrations.single().keepNamespaces shouldBe setOf("kukie-app")
    }

    @Test
    fun `access entry 를 정리하지 못해도 클러스터는 지운다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val clusterId = registerEks(admin, externalId, namespace = "kukie-demo", name = "운영 EKS")
        fakeEksAccessGateway.failure = AwsRoleNotAssumableException()

        // when & then
        mockMvc.delete("/clusters/$clusterId") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }
        clusterRepository.existsById(clusterId) shouldBe false
    }

    // ── AWS 연결 해제 ────────────────────────────────────────────

    @Test
    fun `관리자가 연결을 해제하면 EKS 마다 access entry 를 한 번씩 지우고 스택 콘솔 주소를 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val stackId = "arn:aws:cloudformation:ap-northeast-2:$ACCOUNT_ID:stack/kukie-access-$externalId/uuid-1"
        connect(externalId, stackId)
        registerEks(admin, externalId, namespace = "kukie-demo", name = "데모")
        registerEks(admin, externalId, namespace = "kukie-app", name = "앱")
        mockMvc.post("/clusters/aws") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(idOf(externalId), name = "다른 EKS").copy(clusterName = "other").toJson()
        }.andExpect { status { isCreated() } }

        // when
        mockMvc.delete("/clusters/aws/connections/${idOf(externalId)}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.stackName") { value("kukie-access-$externalId") }
            jsonPath("$.stackConsoleUrl") {
                value(
                    "https://ap-northeast-2.console.aws.amazon.com/cloudformation/home?region=ap-northeast-2" +
                        "#/stacks/stackinfo?stackId=" + URLEncoder.encode(stackId, Charsets.UTF_8),
                )
            }
        }

        // then
        val target = AwsTarget(accountId = ACCOUNT_ID, externalId = externalId)
        fakeEksAccessGateway.unregistrations shouldContainExactlyInAnyOrder listOf(
            FakeEksAccessGateway.Unregistration(target, DEFAULT_REGION, DEFAULT_CLUSTER_NAME, emptySet()),
            FakeEksAccessGateway.Unregistration(target, DEFAULT_REGION, "other", emptySet()),
        )
        clusterRepository.count() shouldBe 3
        awsConnectionRepository.findByExternalId(externalId)!!.run {
            this.externalId shouldBe externalId
            accountId.shouldBeNull()
            connectedAt.shouldBeNull()
            this.stackId.shouldBeNull()
        }
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].externalId") { value(externalId) }
            jsonPath("$[0].accountId") { value(null) }
            jsonPath("$[0].stackName") { value("kukie-access-$externalId") }
            jsonPath("$[0].stackDeleted") { value(false) }
        }
    }

    @Test
    fun `한 계정의 연결을 해제하면 그 계정의 EKS 만 정리하고 다른 계정의 연결은 그대로 둔다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val first = startConnection(admin, teamId)
        connect(first, stackId = null)
        val second = startConnection(admin, teamId)
        connect(second, stackId = null, accountId = OTHER_ACCOUNT_ID)
        registerEks(admin, first, namespace = "kukie-demo", name = "운영 EKS")
        registerEks(admin, second, namespace = "kukie-demo", name = "스테이징 EKS", accountId = OTHER_ACCOUNT_ID)

        // when
        disconnect(admin, first)

        // then
        fakeEksAccessGateway.unregistrations shouldBe listOf(
            FakeEksAccessGateway.Unregistration(
                AwsTarget(accountId = ACCOUNT_ID, externalId = first),
                DEFAULT_REGION,
                DEFAULT_CLUSTER_NAME,
                emptySet(),
            ),
        )
        awsConnectionRepository.findByExternalId(first)!!.accountId.shouldBeNull()
        awsConnectionRepository.findByExternalId(second)!!.accountId shouldBe OTHER_ACCOUNT_ID
        clusterRepository.count() shouldBe 2
    }

    @Test
    fun `스택 ARN 을 모르면 스택 이름으로 거른 목록 주소를 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        connect(externalId, stackId = null)

        // when & then
        mockMvc.delete("/clusters/aws/connections/${idOf(externalId)}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.stackName") { value("kukie-access-$externalId") }
            jsonPath("$.stackConsoleUrl") {
                value(
                    "https://ap-northeast-2.console.aws.amazon.com/cloudformation/home?region=ap-northeast-2" +
                        "#/stacks?filteringText=kukie-access-$externalId&filteringStatus=active&viewNested=true",
                )
            }
        }
        fakeEksAccessGateway.unregistrations.shouldBeEmpty()
        awsConnectionRepository.findByExternalId(externalId)!!.accountId.shouldBeNull()
    }

    @Test
    fun `access entry 를 정리하지 못해도 연결은 해제한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        connect(externalId, stackId = null)
        registerEks(admin, externalId, namespace = "kukie-demo", name = "운영 EKS")
        fakeEksAccessGateway.failure = AwsRoleNotAssumableException()

        // when & then
        mockMvc.delete("/clusters/aws/connections/${idOf(externalId)}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.stackConsoleUrl") { exists() }
        }
        awsConnectionRepository.findByExternalId(externalId)!!.accountId.shouldBeNull()
        clusterRepository.count() shouldBe 1
    }

    @Test
    fun `구성원은 연결을 해제할 수 없다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)
        val externalId = startConnection(admin, teamId)
        connect(externalId, stackId = null)

        // when & then
        mockMvc.delete("/clusters/aws/connections/${idOf(externalId)}") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        awsConnectionRepository.findByExternalId(externalId)!!.accountId shouldBe ACCOUNT_ID
    }

    @Test
    fun `없는 AWS 연결은 해제할 수 없다`() {
        // given
        val admin = loggedInUser()
        savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.delete("/clusters/aws/connections/${UUID.randomUUID()}") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_CONNECTION_NOT_STARTED.code) }
        }
    }

    // ── 해제한 스택 삭제 확인 ────────────────────────────────────

    @Test
    fun `해제한 스택의 역할이 남아 있으면 아직 지우지 않았다고 알리고 지울 스택을 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        val stackId = "arn:aws:cloudformation:ap-northeast-2:$ACCOUNT_ID:stack/kukie-access-$externalId/uuid-1"
        connect(externalId, stackId)
        disconnect(admin, externalId)

        // when & then
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].accountId") { value(null) }
            jsonPath("$[0].stackDeleted") { value(false) }
            jsonPath("$[0].stackName") { value("kukie-access-$externalId") }
            jsonPath("$[0].stackConsoleUrl") { value(containsString(URLEncoder.encode(stackId, Charsets.UTF_8))) }
        }
        fakeEksAccessGateway.roleChecks shouldBe listOf(AwsTarget(accountId = ACCOUNT_ID, externalId = externalId))
        awsConnectionRepository.findByExternalId(externalId)!!.run {
            lastAccountId shouldBe ACCOUNT_ID
            lastStackId shouldBe stackId
            lastStackDeletedAt.shouldBeNull()
        }
    }

    @Test
    fun `해제한 스택의 역할이 사라지면 지웠다고 적고 다시 묻지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        connect(externalId, stackId = null)
        disconnect(admin, externalId)
        fakeEksAccessGateway.roleExists = false

        // when & then
        repeat(2) {
            mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
                authorization(admin.accessToken)
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].stackDeleted") { value(true) }
                jsonPath("$[0].stackName") { value("kukie-access-$externalId") }
                jsonPath("$[0].stackConsoleUrl") { value(containsString("filteringText=kukie-access-$externalId")) }
            }
        }
        fakeEksAccessGateway.roleChecks.size shouldBe 1
        awsConnectionRepository.findByExternalId(externalId)!!.lastStackDeletedAt.shouldNotBeNull()
    }

    @Test
    fun `해제한 스택의 역할을 확인하지 못하면 모른다고 알린다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)
        connect(externalId, stackId = null)
        disconnect(admin, externalId)
        fakeEksAccessGateway.failure = IllegalStateException("throttled")

        // when & then
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].stackDeleted") { value(null) }
            jsonPath("$[0].stackConsoleUrl") { exists() }
        }
        awsConnectionRepository.findByExternalId(externalId)!!.lastStackDeletedAt.shouldBeNull()
    }

    @Test
    fun `연결되어 있거나 해제한 적이 없으면 스택 삭제를 확인하지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val externalId = startConnection(admin, teamId)

        // when & then
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].stackDeleted") { value(null) }
            jsonPath("$[0].stackName") { value("kukie-access-$externalId") }
            jsonPath("$[0].stackConsoleUrl") { value(null) }
        }
        connect(externalId, stackId = null)
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].accountId") { value(ACCOUNT_ID) }
            jsonPath("$[0].stackDeleted") { value(null) }
            jsonPath("$[0].stackName") { value("kukie-access-$externalId") }
        }
        fakeEksAccessGateway.roleChecks.shouldBeEmpty()
    }

    private fun disconnect(user: LoggedInUser, externalId: String) {
        mockMvc.delete("/clusters/aws/connections/${idOf(externalId)}") {
            authorization(user.accessToken)
        }.andExpect { status { isOk() } }
    }

    /** 스택 알림을 확인한 것처럼 계정을 채운다. */
    private fun connect(externalId: String, stackId: String?, accountId: String = ACCOUNT_ID) {
        val connection = awsConnectionRepository.findByExternalId(externalId)!!
        connection.connect(accountId, LocalDateTime.now(), stackId)
        awsConnectionRepository.save(connection)
    }

    private fun startConnection(user: LoggedInUser, teamId: UUID): String {
        val response = mockMvc.post("/clusters/aws/connections") {
            authorization(user.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = StartAwsConnectionRequest(teamId).toJson()
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        return JsonPath.read(response, "$.externalId")
    }

    private fun idOf(externalId: String): UUID = awsConnectionRepository.findByExternalId(externalId)!!.id

    private fun registerRequest(
        connectionId: UUID,
        region: String = DEFAULT_REGION,
        namespace: String = "kukie-demo",
        name: String = "운영 EKS",
        accountId: String = ACCOUNT_ID,
    ) = RegisterEksClusterRequest(
        connectionId = connectionId,
        accountId = accountId,
        region = region,
        clusterName = DEFAULT_CLUSTER_NAME,
        namespace = namespace,
        name = name,
    )

    private fun registerEks(
        user: LoggedInUser,
        externalId: String,
        namespace: String,
        name: String,
        accountId: String = ACCOUNT_ID,
    ): UUID {
        val response = mockMvc.post("/clusters/aws") {
            authorization(user.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest(idOf(externalId), namespace = namespace, name = name, accountId = accountId).toJson()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    companion object {
        private const val ACCOUNT_ID = "123456789012"
        private const val OTHER_ACCOUNT_ID = "210987654321"
        private const val TEMPLATE_URL =
            "https://kukie-templates-320343233643-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/kukie-access/v3.yaml"
    }
}
