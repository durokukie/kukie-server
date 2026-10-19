package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.EksCredential
import com.duro.kukie.cluster.application.HandleStackNoticeService
import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.StackNoticeResult
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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import java.net.URLEncoder
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
        awsConnectionRepository.findByTeamId(teamId)!!.run {
            accountId shouldBe ACCOUNT_ID
            connectedAt.shouldNotBeNull()
            stackId shouldBe stackIdOf(connection.externalId)
        }
        fakeStackNoticeResponder.responses shouldBe
            listOf(StackNoticeResult(true, "kukie-connection-${connection.externalId}", ""))
        mockMvc.get("/clusters/aws/connection?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.externalId") { value(connection.externalId) }
            jsonPath("$.accountId") { value(ACCOUNT_ID) }
            jsonPath("$.stackConsoleUrl") {
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
    fun `스택을 바꾼 알림에는 access entry 를 다시 붙이지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = awsConnectionRepository.save(AwsConnection(teamId = teamId, createdBy = admin.user.id))
        saveEks(teamId, admin.user.id, "데모", eksCredential(connection.externalId), "kukie-demo")

        // when
        handleStackNoticeService(notice(connection.externalId, requestType = "Update", physicalResourceId = "kukie-connection-x"))

        // then
        fakeEksAccessGateway.accessChanges.shouldBeEmpty()
        awsConnectionRepository.findByTeamId(teamId)!!.accountId shouldBe ACCOUNT_ID
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
        awsConnectionRepository.findByTeamId(teamId)!!.accountId.shouldBeNull()
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
        awsConnectionRepository.findByTeamId(teamId)!!.accountId.shouldBeNull()
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
        awsConnectionRepository.findByTeamId(teamId)!!.accountId.shouldBeNull()
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
        awsConnectionRepository.findByTeamId(teamId)!!.accountId.shouldBeNull()
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
    fun `연결을 시작하지 않은 팀의 상태는 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.get("/clusters/aws/connection?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.AWS_CONNECTION_NOT_STARTED.code) }
        }
    }

    @Test
    fun `구성원은 연결 상태를 볼 수 없다`() {
        // given
        val member = loggedInUser()
        val teamId = savedTeam(member.user.id to TeamRole.MEMBER)

        // when & then
        mockMvc.get("/clusters/aws/connection?teamId=$teamId") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    private fun notice(
        externalId: String,
        requestType: String = "Create",
        roleArn: String = "arn:aws:iam::$ACCOUNT_ID:role/KukieAccess-$externalId",
        responseUrl: String = RESPONSE_URL,
        physicalResourceId: String? = null,
    ) = StackNoticeCommand(
        requestType = requestType,
        responseUrl = responseUrl,
        stackId = stackIdOf(externalId),
        requestId = "request-1",
        logicalResourceId = "KukieNotice",
        physicalResourceId = physicalResourceId,
        externalId = externalId,
        roleArn = roleArn,
    )

    private fun stackIdOf(externalId: String) =
        "arn:aws:cloudformation:ap-northeast-2:$ACCOUNT_ID:stack/kukie-access-$externalId/1a2b3c4d-0000-1111-2222-333344445555"

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

    private fun saveEks(teamId: UUID, userId: UUID, name: String, credential: EksCredential, namespace: String) {
        clusterRepository.save(
            ClusterFixture.cluster(
                registeredBy = userId,
                teamId = teamId,
                name = name,
                credential = credentialCipher.encrypt(credential.toMap()),
                defaultNamespace = namespace,
                provider = ClusterProvider.EKS,
            ),
        )
    }

    companion object {
        private const val ACCOUNT_ID = "123456789012"
        private const val RESPONSE_URL =
            "https://cloudformation-custom-resource-response-apnortheast2.s3.ap-northeast-2.amazonaws.com/arn%3Aaws/x?X-Amz-Signature=s"
    }
}
