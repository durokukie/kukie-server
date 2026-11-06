package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorCredential
import com.duro.kukie.serving.domain.ConnectorCredentials
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.duro.kukie.serving.application.port.`in`.IssueConnectorCredentialCommand
import com.duro.kukie.serving.infrastructure.ConnectorTokens
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDateTime
import java.util.UUID

class IssuedConnectorCredential(val token: String, val expiresAt: Instant, val models: List<String>) {
    override fun toString() = "IssuedConnectorCredential(REDACTED)"
}

@Service
class IssueConnectorCredentialService(private val permission: ServingAccessPermission, private val credentials: ConnectorCredentials, private val tokens: ConnectorTokens) {
    @Transactional
    operator fun invoke(command: IssueConnectorCredentialCommand): IssuedConnectorCredential {
        permission.requireAdmin(command.clusterId, command.userId)
        permission.lockCluster(command.clusterId)
        val token = tokens.generate()
        val expires = utcNow().plusHours(24)
        val hash = tokens.hash(token)
        credentials.save(ConnectorCredential(command.clusterId, hash, command.models.distinct(), expires))
        return IssuedConnectorCredential(token, expires.toInstant(ZoneOffset.UTC), command.models.distinct())
    }
}
