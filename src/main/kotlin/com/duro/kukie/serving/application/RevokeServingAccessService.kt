package com.duro.kukie.serving.application

import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RevokeServingAccessService(private val permission: ServingAccessPermission) {
    @Transactional
    operator fun invoke(clusterId: UUID, accessId: UUID, userId: UUID) {
        permission.lockCluster(clusterId)
        permission.requireAdmin(clusterId, userId)
        val access = permission.lockAccess(accessId, clusterId)
        if (access.clusterId != clusterId) throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_FOUND)
        access.revoke()
    }
}
