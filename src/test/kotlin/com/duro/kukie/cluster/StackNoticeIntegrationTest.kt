package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.EksCredential
import com.duro.kukie.cluster.application.HandleStackNoticeService
import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.StackNoticeResult
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.support.FakeEksAccessGateway
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_CLUSTER_NAME
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_REGION
import com.duro.kukie.support.FakeStackNoticeResponder
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.data.repository.findByIdOrNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.UUID

class StackNoticeIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var handleStackNoticeService: HandleStackNoticeService

    @Autowired
    private lateinit var awsConnectionRepository: AwsConnectionRepository

    @Autowired
    private lateinit var fakeEksAccessGateway: FakeEksAccessGateway

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var credentialCipher: CredentialCipher

    @Autowired
    private lateinit var fakeStackNoticeResponder: FakeStackNoticeResponder

    @Test
    fun `스택 알림의 역할을 맡아 본 뒤 계정을 저장하고 성공을 회신한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when
        val done = handleStackNoticeService(notice(connection.externalId))

        // then
        done shouldBe true
        fakeEksAccessGateway.targets shouldBe listOf(AwsTarget(accountId = ACCOUNT_ID, externalId = connection.externalId))
        awsConnectionRepository.findByExternalId(connection.externalId)!!.run {
            accountId shouldBe ACCOUNT_ID
            connectedAt.shouldNotBeNull()
            stackId shouldBe stackIdOf(connection.externalId)
            accessLevel shouldBe AccessLevel.LIMITED
        }
        fakeStackNoticeResponder.responses shouldBe
            listOf(StackNoticeResult(true, "kukie-connection-${connection.externalId}", ""))
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].externalId") { value(connection.externalId) }
            jsonPath("$[0].accountId") { value(ACCOUNT_ID) }
            jsonPath("$[0].accessLevel") { value("limited") }
            jsonPath("$[0].stackConsoleUrl") {
                value(
                    "https://ap-northeast-2.console.aws.amazon.com/cloudformation/home?region=ap-northeast-2" +
                        "#/stacks/stackinfo?stackId=" + URLEncoder.encode(stackIdOf(connection.externalId), Charsets.UTF_8),
                )
            }
        }
        // 이 팀에는 아직 EKS 가 없다
        fakeEksAccessGateway.accessChanges.shouldBeEmpty()
    }

    @Test
    fun `스택을 다시 만들면 등록해 둔 EKS 마다 옛 access entry 를 지우고 다시 붙인다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        val externalId = connection.externalId
        saveEks(teamId, admin.user.id, "데모", eksCredential(externalId), "kukie-demo")
        saveEks(teamId, admin.user.id, "앱", eksCredential(externalId), "kukie-app")
        saveEks(teamId, admin.user.id, "다른 EKS", eksCredential(externalId, clusterName = "other"), "kukie-demo")
        // 다른 계정의 EKS 와 kubeconfig 클러스터는 건드리지 않는다
        saveEks(teamId, admin.user.id, "다른 계정", eksCredential(externalId, accountId = "210987654321"), "kukie-demo")
        clusterRepository.save(ClusterFixture.cluster(registeredBy = admin.user.id, teamId = teamId, name = "kubeconfig"))

        // when
        handleStackNoticeService(notice(externalId))

        // then
        val target = AwsTarget(accountId = ACCOUNT_ID, externalId = externalId)
        val changes = fakeEksAccessGateway.accessChanges
        changes shouldHaveSize 5
        // EKS 마다 지우기 한 번이 그 EKS 의 다시 붙이기보다 먼저다
        for (clusterName in listOf(DEFAULT_CLUSTER_NAME, "other")) {
            val unregister = FakeEksAccessGateway.Unregistration(target, DEFAULT_REGION, clusterName, emptySet())
            changes.count { it == unregister } shouldBe 1
            val registers = changes.withIndex().filter {
                (it.value as? FakeEksAccessGateway.Registration)?.clusterName == clusterName
            }
            registers.forEach { (it.index > changes.indexOf(unregister)) shouldBe true }
        }
        fakeEksAccessGateway.registrations shouldContainExactlyInAnyOrder listOf(
            FakeEksAccessGateway.Registration(target, DEFAULT_REGION, DEFAULT_CLUSTER_NAME, "kukie-demo"),
            FakeEksAccessGateway.Registration(target, DEFAULT_REGION, DEFAULT_CLUSTER_NAME, "kukie-app"),
            FakeEksAccessGateway.Registration(target, DEFAULT_REGION, "other", "kukie-demo"),
        )
        fakeStackNoticeResponder.responses.single().success shouldBe true
    }

    @Test
    fun `레벨이 그대로인 스택 업데이트에는 access entry 를 다시 붙이지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        saveEks(teamId, admin.user.id, "데모", eksCredential(connection.externalId), "kukie-demo")

        // when
        handleStackNoticeService(notice(connection.externalId, requestType = "Update", physicalResourceId = "kukie-connection-x"))

        // then
        fakeEksAccessGateway.accessChanges.shouldBeEmpty()
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId shouldBe ACCOUNT_ID
    }

    @Test
    fun `v5 스택 알림의 권한 레벨을 연결에 저장한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when
        handleStackNoticeService(notice(connection.externalId, accessLevel = "full"))

        // then
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accessLevel shouldBe AccessLevel.FULL
        fakeStackNoticeResponder.responses.single().success shouldBe true
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect { jsonPath("$[0].accessLevel") { value("full") } }
    }

    @Test
    fun `전체 권한으로 스택을 다시 만들면 등록해 둔 EKS 에 전체 권한으로 다시 붙인다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        val clusterId = saveEks(teamId, admin.user.id, "데모", eksCredential(connection.externalId), "kukie-demo")

        // when
        handleStackNoticeService(notice(connection.externalId, accessLevel = "full"))

        // then
        val target = AwsTarget(accountId = ACCOUNT_ID, externalId = connection.externalId)
        fakeEksAccessGateway.registrations shouldBe listOf(
            FakeEksAccessGateway.Registration(target, DEFAULT_REGION, DEFAULT_CLUSTER_NAME, "kukie-demo", AccessLevel.FULL),
        )
        clusterRepository.findByIdOrNull(clusterId)!!.accessLevel shouldBe AccessLevel.FULL
    }

    @Test
    fun `제한에서 전체로 바꾼 스택 업데이트면 등록된 EKS 마다 정책을 바꿔 붙인다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = connected(teamId, admin.user.id, AccessLevel.LIMITED)
        val externalId = connection.externalId
        val demo = saveEks(teamId, admin.user.id, "데모", eksCredential(externalId), "kukie-demo")
        val app = saveEks(teamId, admin.user.id, "앱", eksCredential(externalId), "kukie-app")
        val other = saveEks(teamId, admin.user.id, "다른 EKS", eksCredential(externalId, clusterName = "other"), "kukie-demo")
        // 다른 계정의 EKS 는 건드리지 않는다
        val elsewhere = saveEks(teamId, admin.user.id, "다른 계정", eksCredential(externalId, accountId = OTHER_ACCOUNT_ID), "kukie-demo")

        // when
        handleStackNoticeService(notice(externalId, requestType = "Update", physicalResourceId = "kukie-connection-x", accessLevel = "full"))

        // then
        val target = AwsTarget(accountId = ACCOUNT_ID, externalId = externalId)
        fakeEksAccessGateway.levelChanges shouldContainExactlyInAnyOrder listOf(
            FakeEksAccessGateway.LevelChange(target, DEFAULT_REGION, DEFAULT_CLUSTER_NAME, setOf("kukie-demo", "kukie-app"), AccessLevel.FULL),
            FakeEksAccessGateway.LevelChange(target, DEFAULT_REGION, "other", setOf("kukie-demo"), AccessLevel.FULL),
        )
        fakeEksAccessGateway.registrations.shouldBeEmpty()
        fakeEksAccessGateway.unregistrations.shouldBeEmpty()
        listOf(demo, app, other, elsewhere).map { clusterRepository.findByIdOrNull(it)!!.accessLevel } shouldBe
            listOf(AccessLevel.FULL, AccessLevel.FULL, AccessLevel.FULL, AccessLevel.LIMITED)
        awsConnectionRepository.findByExternalId(externalId)!!.accessLevel shouldBe AccessLevel.FULL
        fakeStackNoticeResponder.responses.single().success shouldBe true
    }

    @Test
    fun `전체에서 제한으로 바꾼 스택 업데이트면 등록한 namespace 범위의 정책으로 바꿔 붙인다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = connected(teamId, admin.user.id, AccessLevel.FULL)
        val externalId = connection.externalId
        val demo = saveEks(teamId, admin.user.id, "데모", eksCredential(externalId), "kukie-demo", AccessLevel.FULL)

        // when
        handleStackNoticeService(notice(externalId, requestType = "Update", physicalResourceId = "kukie-connection-x", accessLevel = "limited"))

        // then
        fakeEksAccessGateway.levelChanges shouldBe listOf(
            FakeEksAccessGateway.LevelChange(
                AwsTarget(accountId = ACCOUNT_ID, externalId = externalId),
                DEFAULT_REGION,
                DEFAULT_CLUSTER_NAME,
                setOf("kukie-demo"),
                AccessLevel.LIMITED,
            ),
        )
        clusterRepository.findByIdOrNull(demo)!!.accessLevel shouldBe AccessLevel.LIMITED
        mockMvc.get("/clusters/$demo") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessLevel") { value("limited") }
            jsonPath("$.accessLevelFailedAt") { value(null) }
        }
    }

    @Test
    fun `한 EKS 의 정책을 바꾸지 못해도 나머지는 바꾸고 실패한 EKS 는 바꾸기 전 레벨과 실패 시각을 남긴다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = connected(teamId, admin.user.id, AccessLevel.LIMITED)
        val externalId = connection.externalId
        val failing = saveEks(teamId, admin.user.id, "데모", eksCredential(externalId), "kukie-demo")
        val other = saveEks(teamId, admin.user.id, "다른 EKS", eksCredential(externalId, clusterName = "other"), "kukie-demo")
        fakeEksAccessGateway.deniedClusters = setOf(DEFAULT_CLUSTER_NAME)

        // when
        val done = handleStackNoticeService(
            notice(externalId, requestType = "Update", physicalResourceId = "kukie-connection-x", accessLevel = "full"),
        )

        // then
        done shouldBe true
        fakeStackNoticeResponder.responses.single().success shouldBe true
        fakeEksAccessGateway.levelChanges.map { it.clusterName } shouldBe listOf("other")
        clusterRepository.findByIdOrNull(other)!!.run {
            accessLevel shouldBe AccessLevel.FULL
            accessLevelFailedAt.shouldBeNull()
        }
        clusterRepository.findByIdOrNull(failing)!!.run {
            accessLevel shouldBe AccessLevel.LIMITED
            accessLevelFailedAt.shouldNotBeNull()
        }
        mockMvc.get("/clusters/$failing") {
            authorization(admin.accessToken)
        }.andExpect {
            jsonPath("$.accessLevel") { value("limited") }
            jsonPath("$.accessLevelFailedAt") { exists() }
        }
    }

    @Test
    fun `레벨이 그대로인 스택 업데이트라도 지난번에 못 바꾼 EKS 는 다시 바꿔 붙인다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = connected(teamId, admin.user.id, AccessLevel.LIMITED)
        val externalId = connection.externalId
        val failing = saveEks(teamId, admin.user.id, "데모", eksCredential(externalId), "kukie-demo")
        saveEks(teamId, admin.user.id, "다른 EKS", eksCredential(externalId, clusterName = "other"), "kukie-demo")
        fakeEksAccessGateway.deniedClusters = setOf(DEFAULT_CLUSTER_NAME)
        val update = notice(externalId, requestType = "Update", physicalResourceId = "kukie-connection-x", accessLevel = "full")
        handleStackNoticeService(update)
        fakeEksAccessGateway.deniedClusters = emptySet()
        fakeEksAccessGateway.levelChanges.clear()

        // when
        handleStackNoticeService(update)

        // then
        fakeEksAccessGateway.levelChanges.map { it.clusterName } shouldBe listOf(DEFAULT_CLUSTER_NAME)
        clusterRepository.findByIdOrNull(failing)!!.run {
            accessLevel shouldBe AccessLevel.FULL
            accessLevelFailedAt.shouldBeNull()
        }
    }

    @Test
    fun `다른 팀이 연결한 계정이어도 연결한다`() {
        // given
        val admin = loggedInUser()
        val otherTeam = savedTeam(admin.user.id to TeamRole.ADMIN)
        val other = awsConnectionRepository.save(AwsConnection(teamId = otherTeam, createdBy = admin.user.id))
        handleStackNoticeService(notice(other.externalId))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when
        handleStackNoticeService(notice(connection.externalId))

        // then
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId shouldBe ACCOUNT_ID
    }

    @Test
    fun `역할을 아직 맡을 수 없으면 회신하지 않고 다시 받는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        fakeEksAccessGateway.failure = AwsRoleNotAssumableException()

        // when
        val done = handleStackNoticeService(notice(connection.externalId))

        // then
        done shouldBe false
        fakeStackNoticeResponder.responses.shouldBeEmpty()
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId.shouldBeNull()
    }

    @Test
    fun `마지막 시도에도 역할을 맡을 수 없으면 실패를 회신한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        fakeEksAccessGateway.failure = AwsRoleNotAssumableException()

        // when
        val done = handleStackNoticeService(notice(connection.externalId), lastAttempt = true)

        // then
        done shouldBe true
        fakeStackNoticeResponder.responses.single().success shouldBe false
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId.shouldBeNull()
    }

    @Test
    fun `Kukie 가 만들지 않은 ExternalId 면 실패를 회신한다`() {
        // when
        val done = handleStackNoticeService(notice("kukie-0000000000000000"))

        // then
        done shouldBe true
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeStackNoticeResponder.responses.single().success shouldBe false
    }

    @Test
    fun `역할 이름이 ExternalId 와 다르면 맡아 보지 않고 실패를 회신한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when
        handleStackNoticeService(notice(connection.externalId, roleArn = "arn:aws:iam::$ACCOUNT_ID:role/KukieAccess-other"))

        // then
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeStackNoticeResponder.responses.single().success shouldBe false
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId.shouldBeNull()
    }

    @Test
    fun `모르는 권한 레벨이면 실패를 회신하고 연결하지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when
        handleStackNoticeService(notice(connection.externalId, accessLevel = "admin"))

        // then
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeStackNoticeResponder.responses.single().success shouldBe false
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId.shouldBeNull()
    }

    @Test
    fun `다른 계정에 연결된 링크의 스택 알림이면 맡아 보지 않고 실패를 회신한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        handleStackNoticeService(notice(connection.externalId))
        fakeEksAccessGateway.targets.clear()
        fakeStackNoticeResponder.responses.clear()

        // when
        handleStackNoticeService(notice(connection.externalId, accountId = OTHER_ACCOUNT_ID))

        // then
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeStackNoticeResponder.responses.single().success shouldBe false
        awsConnectionRepository.findByExternalId(connection.externalId)!!.run {
            accountId shouldBe ACCOUNT_ID
            stackId shouldBe stackIdOf(connection.externalId)
        }
    }

    @Test
    fun `해제한 연결의 링크를 다른 계정에서 실행하면 지난 스택 기록을 두고 실패를 회신한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        handleStackNoticeService(notice(connection.externalId))
        mockMvc.delete("/clusters/aws/connections/${connection.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isOk() } }
        fakeEksAccessGateway.targets.clear()
        fakeStackNoticeResponder.responses.clear()

        // when
        handleStackNoticeService(notice(connection.externalId, accountId = OTHER_ACCOUNT_ID))

        // then
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeStackNoticeResponder.responses.single().success shouldBe false
        awsConnectionRepository.findByExternalId(connection.externalId)!!.run {
            accountId.shouldBeNull()
            lastAccountId shouldBe ACCOUNT_ID
            lastStackId shouldBe stackIdOf(connection.externalId)
        }
    }

    @Test
    fun `팀의 다른 연결이 이미 쓰는 계정이면 맡아 보지 않고 실패를 회신한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connected = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        handleStackNoticeService(notice(connected.externalId))
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        fakeEksAccessGateway.targets.clear()
        fakeStackNoticeResponder.responses.clear()

        // when
        handleStackNoticeService(notice(connection.externalId))

        // then
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeStackNoticeResponder.responses.single().success shouldBe false
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId.shouldBeNull()
    }

    @Test
    fun `CloudFormation 응답 주소가 아니면 회신하지 않고 버린다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))

        // when
        val done = handleStackNoticeService(notice(connection.externalId, responseUrl = "http://169.254.169.254/latest/meta-data"))

        // then
        done shouldBe true
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeStackNoticeResponder.responses.shouldBeEmpty()
        awsConnectionRepository.findByExternalId(connection.externalId)!!.accountId.shouldBeNull()
    }

    @Test
    fun `스택을 지우면 성공만 회신한다`() {
        // when
        val done = handleStackNoticeService(
            notice("kukie-0000000000000000", requestType = StackNoticeCommand.DELETE, physicalResourceId = "kukie-connection-x"),
        )

        // then
        done shouldBe true
        fakeStackNoticeResponder.responses shouldBe listOf(StackNoticeResult(true, "kukie-connection-x", ""))
    }

    @Test
    fun `해제한 뒤 스택을 다시 만들면 지난 스택 기록을 비우고 연결 상태로 돌아간다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        handleStackNoticeService(notice(connection.externalId))
        mockMvc.delete("/clusters/aws/connections/${connection.id}") {
            authorization(admin.accessToken)
        }.andExpect { status { isOk() } }
        fakeEksAccessGateway.roleExists = false
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect { jsonPath("$[0].stackDeleted") { value(true) } }

        // when
        handleStackNoticeService(notice(connection.externalId))

        // then
        awsConnectionRepository.findByExternalId(connection.externalId)!!.run {
            accountId shouldBe ACCOUNT_ID
            lastAccountId.shouldBeNull()
            lastStackId.shouldBeNull()
            lastStackDeletedAt.shouldBeNull()
        }
        mockMvc.get("/clusters/aws/connections?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].accountId") { value(ACCOUNT_ID) }
            jsonPath("$[0].stackDeleted") { value(null) }
        }
    }

    private fun notice(
        externalId: String,
        requestType: String = "Create",
        accountId: String = ACCOUNT_ID,
        roleArn: String = "arn:aws:iam::$accountId:role/KukieAccess-$externalId",
        responseUrl: String = RESPONSE_URL,
        physicalResourceId: String? = null,
        accessLevel: String? = null,
    ) = StackNoticeCommand(
        requestType = requestType,
        responseUrl = responseUrl,
        stackId = stackIdOf(externalId, accountId),
        requestId = "request-1",
        logicalResourceId = "KukieNotice",
        physicalResourceId = physicalResourceId,
        externalId = externalId,
        roleArn = roleArn,
        accessLevel = accessLevel,
    )

    private fun stackIdOf(externalId: String, accountId: String = ACCOUNT_ID) =
        "arn:aws:cloudformation:ap-northeast-2:$accountId:stack/kukie-access-$externalId/1a2b3c4d-0000-1111-2222-333344445555"

    private fun eksCredential(
        externalId: String,
        accountId: String = ACCOUNT_ID,
        clusterName: String = DEFAULT_CLUSTER_NAME,
    ) = EksCredential(
        accountId = accountId,
        externalId = externalId,
        region = DEFAULT_REGION,
        clusterName = clusterName,
    )

    private fun saveEks(
        teamId: UUID,
        userId: UUID,
        name: String,
        credential: EksCredential,
        namespace: String,
        accessLevel: AccessLevel = AccessLevel.LIMITED,
    ): UUID =
        clusterRepository.save(
            ClusterFixture.cluster(
                registeredBy = userId,
                teamId = teamId,
                name = name,
                credential = credentialCipher.encrypt(credential.toMap()),
                defaultNamespace = namespace,
                provider = ClusterProvider.EKS,
                accessLevel = accessLevel,
            ),
        ).id

    /** 스택 알림으로 연결을 마친 것처럼 계정·레벨을 채운다. */
    private fun connected(teamId: UUID, userId: UUID, accessLevel: AccessLevel): AwsConnection {
        val connection = AwsConnection(teamId = teamId, createdBy = userId)
        connection.connect(ACCOUNT_ID, LocalDateTime.now(), null, accessLevel)
        return awsConnectionRepository.save(connection)
    }

    companion object {
        private const val ACCOUNT_ID = "123456789012"
        private const val OTHER_ACCOUNT_ID = "210987654321"
        private const val RESPONSE_URL =
            "https://cloudformation-custom-resource-response-apnortheast2.s3.ap-northeast-2.amazonaws.com/arn%3Aaws/x?X-Amz-Signature=s"
    }
}
