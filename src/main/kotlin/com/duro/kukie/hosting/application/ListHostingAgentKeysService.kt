package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.hosting.domain.HostingAgentKeyRepository
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentKeyResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ListHostingAgentKeysService(
    private val hostingAgentKeyRepository: HostingAgentKeyRepository,
) {

    /** 쓸 수 있는 키만, 최근에 쓴 것부터. */
    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): List<HostingAgentKeyResponse> {
        val now = utcNow()
        return hostingAgentKeyRepository.findAllByUserIdOrderByLastUsedAtDesc(userId)
            .filterNot { it.expired(now) }
            .map(HostingAgentKeyResponse::of)
    }
}
