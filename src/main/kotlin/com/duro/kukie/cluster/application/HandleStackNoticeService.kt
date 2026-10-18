package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.StackNoticeResponder
import com.duro.kukie.cluster.application.port.out.StackNoticeResult
import com.duro.kukie.cluster.domain.AwsConnectionRepository
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
) {

    private val log = logger()
    private val clock: Clock = Clock.systemDefaultZone()

    /**
     * 처리를 끝냈으면 true — 보관함에서 지운다. 방금 만든 역할이 아직 안 보이면(IAM 전파) false 로 두어
     * 보관함이 잠시 뒤 다시 준다. 몇 번 실패하면 보관함이 dead 큐로 옮긴다.
     */
    operator fun invoke(notice: StackNoticeCommand): Boolean {
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
            log.info("스택 알림의 역할을 아직 맡을 수 없어 다시 받습니다: stack={}", notice.stackId)
            return false
        }

        connection.connect(accountId, LocalDateTime.now(clock))
        awsConnectionRepository.save(connection)
        stackNoticeResponder.respond(notice, StackNoticeResult(true, physicalId(notice), ""))
        return true
    }

    private fun fail(notice: StackNoticeCommand, reason: String): Boolean {
        stackNoticeResponder.respond(notice, StackNoticeResult(false, physicalId(notice), reason))
        return true
    }

    /** 역할 ARN 이 이 ExternalId 의 KukieAccess 역할일 때만 그 계정 번호. */
    private fun accountOf(roleArn: String?, externalId: String): String? {
        val pattern = Regex("arn:aws:iam::(\\d{12}):role/${Regex.escape(clusterAwsProperties.roleNamePrefix + externalId)}")
        return roleArn?.let(pattern::matchEntire)?.groupValues?.get(1)
    }

    /** 만들 때는 우리가 정하고, 바꾸거나 지울 때는 CloudFormation 이 준 값을 그대로 돌려준다(달라지면 교체로 본다). */
    private fun physicalId(notice: StackNoticeCommand): String =
        notice.physicalResourceId ?: "kukie-connection-${notice.externalId ?: "unknown"}"

    companion object {
        private const val UNKNOWN_CONNECTION = "Kukie 에서 시작한 AWS 연결이 아닙니다. Kukie 화면의 링크로 다시 만들어 주세요."
        private const val ROLE_MISMATCH = "역할 이름이 ExternalId 와 맞지 않습니다. Kukie 템플릿을 고치지 말고 그대로 써 주세요."
    }
}
