package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionResponse
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/**
 * 팀의 AWS 연결 목록. 화면이 스택을 만드는 동안 계정이 알려졌는지, 연결을 해제한 뒤에는 고객이 스택을 지웠는지 묻는다.
 *
 * 스택 삭제 알림은 역할을 지우기 전에 오므로(CloudFormation 은 의존 관계의 반대 순서로 지운다) 삭제의 증거가 되지 못한다.
 * 그래서 해제 전 계정의 역할을 우리 ExternalId 로 직접 맡아 본다. AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다.
 */
@Service
class GetAwsConnectionsService(
    private val awsConnectionRepository: AwsConnectionRepository,
    private val clusterPermission: ClusterPermission,
    private val clusterAwsProperties: ClusterAwsProperties,
    private val eksAccessGateway: EksAccessGateway,
) {

    private val log = logger()
    private val clock: Clock = Clock.systemDefaultZone()

    operator fun invoke(teamId: UUID, userId: UUID): List<AwsConnectionResponse> {
        clusterPermission.requireTeamAdmin(teamId, userId)
        return awsConnectionRepository.findAllByTeamIdOrderByCreatedAt(teamId).map { connection ->
            val stackDeleted = if (connection.connected) null else stackDeleted(connection)
            AwsConnectionResponse.of(connection, clusterAwsProperties, stackDeleted)
        }
    }

    /**
     * 해제한 스택이 지워졌는지. 확인한 적이 있으면 AWS 에 다시 묻지 않고, 모르면(스로틀링·네트워크 오류) null.
     *
     * 화면은 고객이 스택을 지우기를 기다리며 10초마다 묻는다 — 해제한 연결마다 호출당 AssumeRole 한 번이고, 지워진 것을 확인하면 그친다.
     */
    private fun stackDeleted(connection: AwsConnection): Boolean? {
        if (connection.lastStackDeletedAt != null) return true
        val lastAccountId = connection.lastAccountId ?: return null
        val exists = try {
            eksAccessGateway.roleExists(AwsTarget(accountId = lastAccountId, externalId = connection.externalId))
        } catch (exception: RuntimeException) {
            log.warn("해제한 스택의 역할을 확인하지 못했습니다: team={} reason={}", connection.teamId, exception.javaClass.simpleName)
            return null
        }
        if (exists) return false
        // 그 사이 다시 연결됐으면 적지 않는다. 이번 응답은 확인한 그대로 준다
        awsConnectionRepository.markLastStackDeleted(connection.id, lastAccountId, LocalDateTime.now(clock))
        return true
    }
}
