package com.duro.kukie.serving.application

import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ServingApiKeyPermission(private val accesses: ServingAccessRepository, private val permission: ServingAccessPermission) {
    fun requireOwner(accessId: UUID, userId: UUID): ServingAccess =
        accesses.findByIdOrNull(accessId)?.takeIf { it.userId == userId }
            ?: throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_FOUND)

    fun requireAdmin(clusterId: UUID, accessId: UUID, userId: UUID): ServingAccess {
        permission.requireAdmin(clusterId, userId)
        return accesses.findByIdOrNull(accessId)?.takeIf { it.clusterId == clusterId }
            ?: throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_FOUND)
    }
}
