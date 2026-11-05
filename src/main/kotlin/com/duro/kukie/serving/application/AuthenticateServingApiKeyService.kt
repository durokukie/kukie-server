package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.serving.domain.ServingApiKeyRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.duro.kukie.serving.infrastructure.ServingKeyTokens
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthenticateServingApiKeyService(
    private val keys: ServingApiKeyRepository,
    private val accesses: ServingAccessRepository,
    private val tokens: ServingKeyTokens,
) {
    @Transactional(readOnly = true)
    operator fun invoke(token: String): AuthenticatedServingKey {
        if (!tokens.isValidFormat(token)) invalid()
        val key = keys.findByTokenHash(tokens.hash(token))?.takeIf { it.isUsable(utcNow()) } ?: invalid()
        val access = accesses.findByIdOrNull(key.accessId)?.takeIf { it.status == ServingAccessStatus.ACTIVE } ?: invalid()
        return AuthenticatedServingKey(key.id, access.id, access.clusterId, access.userId ?: invalid())
    }

    private fun invalid(): Nothing = throw ServingAccessException(ServingErrorCode.SERVING_KEY_INVALID)
}

data class AuthenticatedServingKey(val keyId: UUID, val accessId: UUID, val clusterId: UUID, val userId: UUID)
