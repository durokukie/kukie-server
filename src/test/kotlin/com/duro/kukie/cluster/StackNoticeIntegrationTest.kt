package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.HandleStackNoticeService
import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.StackNoticeResult
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.support.FakeEksAccessGateway
import com.duro.kukie.support.FakeStackNoticeResponder
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get

class StackNoticeIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var handleStackNoticeService: HandleStackNoticeService

    @Autowired
    private lateinit var awsConnectionRepository: AwsConnectionRepository

    @Autowired
    private lateinit var fakeEksAccessGateway: FakeEksAccessGateway

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
        }
        fakeStackNoticeResponder.responses shouldBe
            listOf(StackNoticeResult(true, "kukie-connection-${connection.externalId}", ""))
        mockMvc.get("/clusters/aws/connection?teamId=$teamId") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.externalId") { value(connection.externalId) }
            jsonPath("$.accountId") { value(ACCOUNT_ID) }
        }
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
        stackId = "arn:aws:cloudformation:ap-northeast-2:$ACCOUNT_ID:stack/kukie-access-$externalId/1",
        requestId = "request-1",
        logicalResourceId = "KukieNotice",
        physicalResourceId = physicalResourceId,
        externalId = externalId,
        roleArn = roleArn,
    )

    companion object {
        private const val ACCOUNT_ID = "123456789012"
        private const val RESPONSE_URL =
            "https://cloudformation-custom-resource-response-apnortheast2.s3.ap-northeast-2.amazonaws.com/arn%3Aaws/x?X-Amz-Signature=s"
    }
}
