package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.StartAwsConnectionCommand
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionResponse
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import org.springframework.stereotype.Service

/** AWS 계정 하나를 더 연결할 링크를 준다. 아직 스택 알림을 받지 못한 연결이 있으면 그 연결을 다시 준다. */
@Service
class StartAwsConnectionService(
    private val awsConnectionRepository: AwsConnectionRepository,
    private val clusterPermission: ClusterPermission,
    private val clusterAwsProperties: ClusterAwsProperties,
) {

    operator fun invoke(command: StartAwsConnectionCommand): AwsConnectionResponse {
        clusterPermission.requireTeamAdmin(command.teamId, command.userId)
        // ponytail: 동시에 누르면 기다리는 연결이 둘 생길 수 있다. 둘 다 다음 [계정 추가] 에서 다시 쓰여 막지 않는다
        val connection = awsConnectionRepository
            .findFirstByTeamIdAndAccountIdIsNullAndLastAccountIdIsNullOrderByCreatedAt(command.teamId)
            ?: awsConnectionRepository.save(AwsConnection(teamId = command.teamId, createdBy = command.userId))

        return AwsConnectionResponse.of(connection, clusterAwsProperties, stackDeleted = null)
    }
}
