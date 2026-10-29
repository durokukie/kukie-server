package com.duro.kukie.cluster

import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.cluster.ClusterFixture.kubeconfig
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.ClusterStatus
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.cluster.presentation.dto.request.RegisterClusterRequest
import com.duro.kukie.cluster.presentation.dto.request.UpdateClusterRequest
import com.duro.kukie.support.FakeHostResolver
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.TeamFixture
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import com.jayway.jsonpath.JsonPath
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.net.InetAddress
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class ClusterIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var teamMembershipRepository: TeamMembershipRepository

    @Autowired
    private lateinit var credentialCipher: CredentialCipher

    @Autowired
    private lateinit var fakeHostResolver: FakeHostResolver

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `관리자는 kubeconfig 로 팀 클러스터를 등록한다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)

        // when
        val response = mockMvc.post("/clusters") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RegisterClusterRequest(kubeconfig = kubeconfig(), name = ClusterFixture.DEFAULT_NAME, teamId = teamId).toJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.teamId") { value(teamId.toString()) }
            jsonPath("$.registeredBy") { value(me.user.id.toString()) }
            jsonPath("$.name") { value(ClusterFixture.DEFAULT_NAME) }
            jsonPath("$.provider") { value("GENERIC") }
            jsonPath("$.apiServer") { value(ClusterFixture.DEFAULT_SERVER) }
            jsonPath("$.context") { value(ClusterFixture.DEFAULT_CONTEXT) }
            jsonPath("$.namespace") { value(ClusterFixture.DEFAULT_NAMESPACE) }
            jsonPath("$.status") { value("disconnected") }
            jsonPath("$.insecure") { value(false) }
            jsonPath("$.lastCheckedAt") { value(null) }
            jsonPath("$.hasGpu") { value(null) }
        }.andReturn().response.contentAsString

        // then
        response shouldNotContain ClusterFixture.DEFAULT_TOKEN
        JsonPath.read<Map<String, Any?>>(response, "$").keys shouldBe setOf(
            "id", "teamId", "registeredBy", "name", "provider", "apiServer", "context", "namespace", "fingerprint",
            "status", "insecure", "lastCheckedAt", "hasGpu", "accessLevel", "accessLevelFailedAt", "createdAt", "updatedAt",
        )
        val stored = clusterRepository.findByIdOrNull(UUID.fromString(JsonPath.read(response, "$.id")))!!
        stored.credential shouldNotContain ClusterFixture.DEFAULT_TOKEN
        credentialCipher.decrypt(stored.credential) shouldBe mapOf("token" to ClusterFixture.DEFAULT_TOKEN)
    }

    @Test
    fun `인증서 방식의 kubeconfig 도 등록한다`() {
        // given
        val me = loggedInUser()
        val body = kubeconfig(user = mapOf("client-certificate-data" to "Y2VydA==", "client-key-data" to "a2V5"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)

        // when
        val response = mockMvc.post("/clusters") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RegisterClusterRequest(kubeconfig = body, name = ClusterFixture.DEFAULT_NAME, teamId = teamId).toJson()
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        // then
        val stored = clusterRepository.findByIdOrNull(UUID.fromString(JsonPath.read(response, "$.id")))!!
        credentialCipher.decrypt(stored.credential) shouldBe mapOf("client_certificate_data" to "Y2VydA==", "client_key_data" to "a2V5")
    }

    @Test
    fun `kubeconfig 를 검사하는 동안 DB 연결을 잡지 않는다`() {
        // given
        val admin = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id, TeamRole.ADMIN))
        var activeWhileResolving: Int? = null
        fakeHostResolver.onResolve = { activeWhileResolving = activeConnections() }

        // when
        mockMvc.post("/clusters") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RegisterClusterRequest(kubeconfig = kubeconfig(), name = "운영", teamId = team.id).toJson()
        }.andExpect {
            status { isCreated() }
        }

        // then
        activeWhileResolving shouldBe 0
    }

    @Test
    fun `외부 인증 프로그램을 쓰는 kubeconfig 는 등록할 수 없다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.post("/clusters") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RegisterClusterRequest(
                kubeconfig = kubeconfig(user = mapOf("exec" to mapOf("command" to "aws"))),
                name = "운영",
                teamId = teamId,
            ).toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.KUBECONFIG_REJECTED.code) }
            jsonPath("$.message") { value(containsString("외부 인증 프로그램")) }
        }
        clusterRepository.count() shouldBe 0
    }

    @Test
    fun `사설 주소를 가리키는 클러스터는 등록할 수 없다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        fakeHostResolver.addresses = listOf(InetAddress.getByName("10.0.0.5"))

        // when & then
        for (serverUrl in listOf("https://127.0.0.1:6443", "https://[::1]:6443", "https://sneaky.example")) {
            mockMvc.post("/clusters") {
                authorization(me.accessToken)
                contentType = MediaType.APPLICATION_JSON
                content = RegisterClusterRequest(kubeconfig = kubeconfig(serverUrl = serverUrl), name = "운영", teamId = teamId).toJson()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value(ClusterErrorCode.KUBECONFIG_REJECTED.code) }
                jsonPath("$.message") { value(containsString("사설")) }
            }
        }
    }

    @Test
    fun `구성원은 클러스터를 등록할 수 없다`() {
        // given
        val member = loggedInUser()
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, member.user.id, TeamRole.MEMBER))

        // when & then
        mockMvc.post("/clusters") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RegisterClusterRequest(kubeconfig = "아무거나", name = "운영", teamId = team.id).toJson()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `같은 팀에 같은 이름의 클러스터를 등록할 수 없다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        register(me, name = "운영", teamId = teamId)

        // when & then
        mockMvc.post("/clusters") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RegisterClusterRequest(kubeconfig = kubeconfig(), name = "운영", teamId = teamId).toJson()
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.CLUSTER_NAME_TAKEN.code) }
        }
    }

    @Test
    fun `내가 속한 팀의 클러스터만 조회한다`() {
        // given
        val me = loggedInUser()
        val other = loggedInUser(UserFixture.user(email = "other@example.com"))
        val myTeam = teamRepository.save(TeamFixture.team("내 팀"))
        val otherTeam = teamRepository.save(TeamFixture.team("남의 팀"))
        teamMembershipRepository.save(TeamFixture.membership(myTeam.id, me.user.id, TeamRole.MEMBER))
        teamMembershipRepository.save(TeamFixture.membership(myTeam.id, other.user.id))
        teamMembershipRepository.save(TeamFixture.membership(otherTeam.id, other.user.id))
        val first = clusterRepository.save(ClusterFixture.cluster(registeredBy = other.user.id, teamId = myTeam.id, name = "운영"))
        val second = clusterRepository.save(ClusterFixture.cluster(registeredBy = me.user.id, teamId = myTeam.id, name = "스테이징"))
        clusterRepository.save(ClusterFixture.cluster(registeredBy = other.user.id, teamId = otherTeam.id, name = "남의 팀"))

        // when & then
        mockMvc.get("/clusters") {
            authorization(me.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(first.id.toString()) }
            jsonPath("$[1].id") { value(second.id.toString()) }
        }
    }

    @Test
    fun `kubeconfig 를 바꾸면 연결을 다시 확인해야 한다`() {
        // given
        val me = loggedInUser()
        val id = register(me)
        val before = clusterRepository.findByIdOrNull(id)!!
        before.recordCheck(ClusterStatus.CONNECTED, LocalDateTime.now())
        before.recordGpu(true)
        clusterRepository.save(before)
        val replaced = kubeconfig(serverUrl = "https://other.example.com", context = "renamed", user = mapOf("token" to "새-토큰"))

        // when
        mockMvc.patch("/clusters/$id") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateClusterRequest(kubeconfig = replaced).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.apiServer") { value("https://other.example.com") }
            jsonPath("$.status") { value("disconnected") }
            jsonPath("$.hasGpu") { value(null) }
            jsonPath("$.context") { value(ClusterFixture.DEFAULT_CONTEXT) }
            jsonPath("$.namespace") { value(ClusterFixture.DEFAULT_NAMESPACE) }
        }

        // then
        val after = clusterRepository.findByIdOrNull(id)!!
        after.fingerprint shouldNotBe before.fingerprint
        credentialCipher.decrypt(after.credential) shouldBe mapOf("token" to "새-토큰")
    }

    @Test
    fun `kubeconfig 를 검사하는 사이에 바뀐 이름을 덮어쓰지 않는다`() {
        // given
        val admin = loggedInUser()
        val id = register(admin)
        fakeHostResolver.onResolve = {
            val other = clusterRepository.findByIdOrNull(id)!!
            other.rename("다른 관리자가 바꾼 이름")
            clusterRepository.save(other)
        }

        // when
        mockMvc.patch("/clusters/$id") {
            authorization(admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateClusterRequest(kubeconfig = kubeconfig(serverUrl = "https://other.example.com")).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("다른 관리자가 바꾼 이름") }
            jsonPath("$.apiServer") { value("https://other.example.com") }
        }

        // then
        clusterRepository.findByIdOrNull(id)!!.name shouldBe "다른 관리자가 바꾼 이름"
    }

    @Test
    fun `EKS 클러스터는 이름만 바꾸고 kubeconfig 나 namespace 는 바꿀 수 없다`() {
        // given
        val me = loggedInUser()
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN)
        val cluster = clusterRepository.save(
            ClusterFixture.cluster(registeredBy = me.user.id, teamId = teamId, provider = ClusterProvider.EKS),
        )

        // when & then
        mockMvc.patch("/clusters/${cluster.id}") {
            authorization(me.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateClusterRequest(name = "새 이름").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("새 이름") }
            jsonPath("$.provider") { value("EKS") }
        }
        for (request in listOf(UpdateClusterRequest(kubeconfig = kubeconfig()), UpdateClusterRequest(namespace = "other"))) {
            mockMvc.patch("/clusters/${cluster.id}") {
                authorization(me.accessToken)
                contentType = MediaType.APPLICATION_JSON
                content = request.toJson()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value(ClusterErrorCode.CLUSTER_PROVIDER_MISMATCH.code) }
            }
        }
        val stored = clusterRepository.findByIdOrNull(cluster.id)!!
        stored.apiServer shouldBe ClusterFixture.DEFAULT_SERVER
        stored.defaultNamespace shouldBe ClusterFixture.DEFAULT_NAMESPACE
    }

    @Test
    fun `관리자는 클러스터를 삭제한다`() {
        // given
        val admin = loggedInUser()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        val team = teamRepository.save(TeamFixture.team())
        teamMembershipRepository.save(TeamFixture.membership(team.id, admin.user.id, TeamRole.ADMIN))
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = member.user.id, teamId = team.id))

        // when & then
        mockMvc.delete("/clusters/${cluster.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isNoContent() } }
        clusterRepository.existsById(cluster.id) shouldBe false
    }

    @Test
    fun `대화가 있는 클러스터는 삭제할 수 없다`() {
        // given
        val me = loggedInUser()
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = me.user.id, teamId = savedTeam(me.user.id to TeamRole.ADMIN)))
        chatSessionRepository.save(ChatFixture.session(me.user.id, cluster.id))

        // when & then
        mockMvc.delete("/clusters/${cluster.id}") {
            authorization(me.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.CLUSTER_IN_USE.code) }
        }
        clusterRepository.existsById(cluster.id) shouldBe true
    }

    private fun activeConnections(): Int =
        dataSource.unwrap(HikariDataSource::class.java).hikariPoolMXBean.activeConnections

    private fun register(
        user: LoggedInUser,
        name: String = ClusterFixture.DEFAULT_NAME,
        teamId: UUID = savedTeam(user.user.id to TeamRole.ADMIN),
    ): UUID {
        val response = mockMvc.post("/clusters") {
            authorization(user.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = RegisterClusterRequest(kubeconfig = kubeconfig(), name = name, teamId = teamId).toJson()
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        return UUID.fromString(JsonPath.read(response, "$.id"))
    }
}
