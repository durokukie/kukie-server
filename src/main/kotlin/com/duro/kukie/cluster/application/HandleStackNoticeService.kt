package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.StackNoticeResponder
import com.duro.kukie.cluster.application.port.out.StackNoticeResult
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/**
 * 템플릿 v2 스택 알림으로 고객 계정을 알아낸다 — 고객이 계정 번호를 적지 않아도 된다.
 *
 * 알림은 누구나 보낼 수 있어 힌트로만 쓴다. ExternalId 로 팀을 찾고, 알림이 말한 역할을 그 ExternalId 로 실제로
 * 맡아 본 뒤에만 계정을 저장한다. AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다(저장은 save 한 번).
 */
@Service
class HandleStackNoticeService(
    private val awsConnectionRepository: AwsConnectionRepository,
    private val eksAccessGateway: EksAccessGateway,
    private val stackNoticeResponder: StackNoticeResponder,
    private val clusterAwsProperties: ClusterAwsProperties,
    private val clusterRepository: ClusterRepository,
    private val credentialCipher: CredentialCipher,
) {

    private val log = logger()
    private val clock: Clock = Clock.systemDefaultZone()

    /**
     * 처리를 끝냈으면 true — 보관함에서 지운다. 방금 만든 역할이 아직 안 보이면(IAM 전파) false 로 두어
     * 보관함이 잠시 뒤 다시 준다. 마지막 시도(lastAttempt)에도 못 맡으면 FAILED 를 회신해 고객 스택이
     * 한 시간 멈춰 있지 않게 한다(신뢰 정책을 고친 경우 등).
     */
    operator fun invoke(notice: StackNoticeCommand, lastAttempt: Boolean = false): Boolean {
        if (!stackNoticeResponder.accepts(notice.responseUrl)) {
            log.warn("CloudFormation 응답 주소가 아닌 스택 알림을 버립니다: stack={}", notice.stackId)
            return true
        }
        // 지울 때는 막을 이유가 없다. 연결 정보는 그대로 두고, 역할이 사라지면 다음 호출이 AWS_ROLE_NOT_ASSUMABLE 이 된다
        // (지우는 중에도 역할은 아직 있어서, 가짜 삭제 알림과 진짜를 가릴 수 없다).
        if (notice.requestType == StackNoticeCommand.DELETE) {
            stackNoticeResponder.respond(notice, StackNoticeResult(true, physicalId(notice), ""))
            return true
        }

        val connection = notice.externalId?.let(awsConnectionRepository::findByExternalId)
            ?: return fail(notice, UNKNOWN_CONNECTION)
        val accountId = accountOf(notice.roleArn, connection.externalId) ?: return fail(notice, ROLE_MISMATCH)
        try {
            eksAccessGateway.verify(AwsTarget(accountId = accountId, externalId = connection.externalId))
        } catch (_: AwsRoleNotAssumableException) {
            if (lastAttempt) return fail(notice, NOT_ASSUMABLE)
            log.info("스택 알림의 역할을 아직 맡을 수 없어 다시 받습니다: stack={}", notice.stackId)
            return false
        }

        connection.connect(accountId, LocalDateTime.now(clock), StackConsoleUrl.stackIdOf(notice.stackId, accountId))
        awsConnectionRepository.save(connection)
        stackNoticeResponder.respond(notice, StackNoticeResult(true, physicalId(notice), ""))
        if (notice.requestType == StackNoticeCommand.CREATE) reattachEksAccess(connection, accountId)
        return true
    }

    /**
     * 연결을 해제했다가 스택을 다시 만든 경우다. 해제할 때 지운 access entry 를 이 팀이 그 계정으로 등록해 둔 EKS 마다
     * 다시 붙인다(같은 EKS 의 namespace 는 registerSelf 가 합친다). 못 붙여도 알림 처리는 성공으로 둔다 —
     * 그 클러스터는 연결할 때 오류가 나고, 지우고 다시 등록하면 된다.
     */
    private fun reattachEksAccess(connection: AwsConnection, accountId: String) {
        for (cluster in clusterRepository.findAllByTeamIdAndProvider(connection.teamId, ClusterProvider.EKS)) {
            try {
                val credential = EksCredential.from(credentialCipher.decrypt(cluster.credential))
                if (credential.accountId != accountId || credential.externalId != connection.externalId) continue
                eksAccessGateway.registerSelf(credential.target, credential.region, credential.clusterName, cluster.defaultNamespace)
            } catch (exception: RuntimeException) {
                log.warn("EKS access entry 를 다시 붙이지 못했습니다: cluster={} reason={}", cluster.id, exception.javaClass.simpleName)
            }
        }
    }

    private fun fail(notice: StackNoticeCommand, reason: String): Boolean {
        stackNoticeResponder.respond(notice, StackNoticeResult(false, physicalId(notice), reason))
        return true
    }

    /** 역할 ARN 이 이 ExternalId 의 KukieAccess 역할일 때만 그 계정 번호. */
    private fun accountOf(roleArn: String?, externalId: String): String? {
        val match = roleArn?.let(ROLE_ARN::matchEntire) ?: return null
        val (accountId, roleName) = match.destructured
        return accountId.takeIf { roleName == clusterAwsProperties.roleNamePrefix + externalId }
    }

    /** 만들 때는 우리가 정하고, 바꾸거나 지울 때는 CloudFormation 이 준 값을 그대로 돌려준다(달라지면 교체로 본다). */
    private fun physicalId(notice: StackNoticeCommand): String =
        notice.physicalResourceId ?: "kukie-connection-${notice.externalId ?: "unknown"}"

    companion object {
        private val ROLE_ARN = Regex("arn:aws:iam::(\\d{12}):role/([\\w+=,.@-]+)")
        // 고객이 AWS 콘솔의 스택 이벤트에서 읽는다. CloudFormation 이 ASCII 밖의 글자를 ?로 바꿔 보여 줘서 영어로 쓴다
        private const val NOT_ASSUMABLE =
            "Kukie could not assume the KukieAccess role. Recreate the stack from the Kukie link without editing the template."
        private const val UNKNOWN_CONNECTION =
            "This ExternalId was not issued by Kukie. Start again from the AWS connect button in Kukie."
        private const val ROLE_MISMATCH =
            "The role name does not match the ExternalId. Use the Kukie template as is."
    }
}
