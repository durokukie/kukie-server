package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.GetEksCandidatesCommand
import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.findByTeamIdOrThrow
import com.duro.kukie.cluster.presentation.dto.response.EksClusterCandidateResponse
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import org.springframework.stereotype.Service

@Service
class GetEksCandidatesService(
    private val awsConnectionRepository: AwsConnectionRepository,
    private val clusterPermission: ClusterPermission,
    private val eksAccessGateway: EksAccessGateway,
    private val clusterAwsProperties: ClusterAwsProperties,
) {

    /** AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: GetEksCandidatesCommand): List<EksClusterCandidateResponse> {
        clusterPermission.requireTeamAdmin(command.teamId, command.userId)
        val connection = awsConnectionRepository.findByTeamIdOrThrow(command.teamId)
        val target = AwsTarget(accountId = command.accountId, externalId = connection.externalId)

        return eksAccessGateway.listClusters(target, clusterAwsProperties.regions).map(EksClusterCandidateResponse::from)
    }
}
