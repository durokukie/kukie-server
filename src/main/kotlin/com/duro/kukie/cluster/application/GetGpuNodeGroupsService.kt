package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupResponse
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupsResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetGpuNodeGroupsService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
) {

    /** AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(clusterId: UUID, userId: UUID): GpuNodeGroupsResponse {
        val cluster = clusterPermission.requireReadable(clusterId, userId)
        if (cluster.provider != ClusterProvider.EKS) return GpuNodeGroupsResponse.unsupported()

        val access = eksGpuAccess.of(cluster)
        val credential = access.credential
        val nodeGroups = eksAccessGateway.listNodegroupStates(credential.target, credential.region, credential.clusterName)
            .filter { it.gpu }
            .map(GpuNodeGroupResponse::from)
        return GpuNodeGroupsResponse(supported = true, control = access.control, nodeGroups = nodeGroups)
    }
}
