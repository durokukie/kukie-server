package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ServingApiKeyRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RevokeAdminServingApiKeyService(
    private val accessPermission: ServingAccessPermission,
    private val permission: ServingApiKeyPermission,
    private val keys: ServingApiKeyRepository,
) {
    @Transactional
    operator fun invoke(clusterId: UUID, accessId: UUID, userId: UUID, keyId: UUID) {
        accessPermission.lockAccess(accessId, clusterId)
        permission.requireAdmin(clusterId, accessId, userId)
        val key = keys.findByIdAndAccessId(keyId, accessId) ?: throw ServingAccessException(ServingErrorCode.SERVING_KEY_NOT_FOUND)
        key.revoke(utcNow())
    }
}
