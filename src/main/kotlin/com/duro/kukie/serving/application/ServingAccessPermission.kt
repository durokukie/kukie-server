package com.duro.kukie.serving.application

import com.duro.kukie.cluster.application.ClusterPermission
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ServingAccessPermission(
    private val clusterPermission: ClusterPermission,
    private val clusterRepository: ClusterRepository,
    private val accesses: ServingAccessRepository,
) {
    fun requireAdmin(clusterId: UUID, userId: UUID): Cluster = clusterPermission.requireWritable(clusterId, userId)

    fun lockCluster(clusterId: UUID) {
        // 삭제와 초대 변경은 클러스터부터 같은 순서로 잠근다.
        clusterRepository.lockIdById(clusterId) ?: throw ClusterNotFoundException()
    }

    fun lockAccess(accessId: UUID, expectedClusterId: UUID? = null): ServingAccess {
        val clusterId = accesses.findClusterIdById(accessId) ?: notFound()
        if (expectedClusterId != null && clusterId != expectedClusterId) notFound()
        lockCluster(clusterId)
        return accesses.findByIdOrNull(accessId) ?: notFound()
    }

    fun requireActive(accessId: UUID, userId: UUID): ServingAccess =
        accesses.findByIdOrNull(accessId)?.takeIf {
            it.userId == userId && it.status == ServingAccessStatus.ACTIVE
        } ?: notFound()

    private fun notFound(): Nothing = throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_FOUND)
}
