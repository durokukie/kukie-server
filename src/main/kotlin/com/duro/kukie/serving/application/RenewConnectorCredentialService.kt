package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ConnectorCredentialProperties
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.`in`.RenewConnectorCredentialCommand
import com.duro.kukie.serving.domain.ConnectorCredential
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.duro.kukie.serving.infrastructure.ConnectorTokens
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

class RenewedConnectorCredential(val token: String, val expiresAt: Instant) {
    override fun toString() = "RenewedConnectorCredential(REDACTED)"
}

@Service
class RenewConnectorCredentialService(
    private val credentials: ConnectorCredentials,
    private val tokens: ConnectorTokens,
    private val properties: ConnectorCredentialProperties,
) {
    @Transactional
    operator fun invoke(command: RenewConnectorCredentialCommand): RenewedConnectorCredential {
        val row = credentials.findForUpdate(command.clusterId) ?: throw ServingAccessException(ServingErrorCode.SERVING_CONNECTOR_CREDENTIAL_INVALID)
        val now = utcNow()
        val current = row.tokenHash == command.tokenSha256 && row.expiresAt.isAfter(now)
        // 직전 자격으로 다시 오면 앞선 갱신 응답을 잃은 것이다. 직전 자격은 그대로 두고 새 자격을 다시 준다.
        val retry = !current && row.previousTokenHash == command.tokenSha256 && row.previousActive(now)
        if (!current && !retry) throw ServingAccessException(ServingErrorCode.SERVING_CONNECTOR_CREDENTIAL_INVALID)
        if (row.rotatedAt?.isAfter(now.minus(properties.renewMinInterval)) == true) {
            throw ServingAccessException(ServingErrorCode.SERVING_CONNECTOR_RENEW_TOO_SOON)
        }
        val token = tokens.generate()
        val expires = now.plus(properties.lifetime)
        val previousExpires = if (current) minOf(row.expiresAt, now.plus(properties.overlap)) else row.previousExpiresAt
        val previousHash = if (current) row.tokenHash else row.previousTokenHash
        credentials.save(ConnectorCredential(row.clusterId, tokens.hash(token), row.models, expires, previousHash, previousExpires, now))
        return RenewedConnectorCredential(token, expires.toInstant(ZoneOffset.UTC))
    }
}
