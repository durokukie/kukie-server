package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorCredentials
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RevokeConnectorCredentialService(private val permission: ServingAccessPermission, private val credentials: ConnectorCredentials) {
    @Transactional
    operator fun invoke(clusterId: UUID, userId: UUID) {
        permission.requireAdmin(clusterId, userId)
        permission.lockCluster(clusterId)
        credentials.revoke(clusterId, utcNow())
    }
}
