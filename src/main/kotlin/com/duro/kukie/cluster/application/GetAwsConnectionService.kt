package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.findByTeamIdOrThrow
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionStatusResponse
import org.springframework.stereotype.Service
import java.util.UUID

/** 화면이 스택을 만드는 동안 계정이 알려졌는지 묻는다. */
@Service
class GetAwsConnectionService(
    private val awsConnectionRepository: AwsConnectionRepository,
    private val clusterPermission: ClusterPermission,
) {

    operator fun invoke(teamId: UUID, userId: UUID): AwsConnectionStatusResponse {
        clusterPermission.requireTeamAdmin(teamId, userId)
        return AwsConnectionStatusResponse.from(awsConnectionRepository.findByTeamIdOrThrow(teamId))
    }
}
