package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingAgentKeyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RevokeHostingAgentKeyService(
    private val hostingAgentKeyRepository: HostingAgentKeyRepository,
) {

    /** 남의 키나 이미 끊은 키여도 같은 답을 준다. */
    @Transactional
    operator fun invoke(userId: UUID, keyId: UUID) {
        hostingAgentKeyRepository.deleteByIdAndUserId(keyId, userId)
    }
}
