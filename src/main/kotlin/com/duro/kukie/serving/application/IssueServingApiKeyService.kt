package com.duro.kukie.serving.application

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.domain.ServingApiKey
import com.duro.kukie.serving.domain.ServingApiKeyRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.duro.kukie.serving.infrastructure.ServingKeyTokens
import com.duro.kukie.serving.presentation.dto.response.IssuedServingApiKeyResponse
import com.duro.kukie.serving.presentation.dto.response.ServingApiKeyResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IssueServingApiKeyService(
    private val permission: ServingAccessPermission,
    private val keys: ServingApiKeyRepository,
    private val tokens: ServingKeyTokens,
) {
    @Transactional
    operator fun invoke(command: IssueServingApiKeyCommand): IssuedServingApiKeyResponse {
        if (command.name.isBlank() || command.name.length > 80 || command.expiresInDays !in 1..365) throw BusinessException(GlobalErrorCode.BAD_REQUEST)
        permission.lockAccess(command.accessId)
        permission.requireActive(command.accessId, command.userId)
        val now = utcNow()
        if (keys.countByAccessIdAndRevokedAtIsNullAndExpiresAtAfter(command.accessId, now) >= 5) {
            throw ServingAccessException(ServingErrorCode.SERVING_KEY_LIMIT_REACHED)
        }
        val token = tokens.generate()
        val key = keys.saveAndFlush(ServingApiKey(command.accessId, command.name.trim(), tokens.hash(token), token.take(12), now.plusDays(command.expiresInDays.toLong())))
        return IssuedServingApiKeyResponse(ServingApiKeyResponse.of(key), token)
    }
}
