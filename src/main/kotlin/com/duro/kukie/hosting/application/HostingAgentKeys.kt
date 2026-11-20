package com.duro.kukie.hosting.application

import com.duro.kukie.global.security.HostingAgentKeyAuthenticator
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.hosting.domain.HostingAgentKey
import com.duro.kukie.hosting.domain.HostingAgentKeyRepository
import com.duro.kukie.user.application.UserWithdrawn
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** 에이전트 키를 내주고 확인한다. 키는 해시로만 둔다. */
@Component
class HostingAgentKeys(
    private val hostingAgentKeyRepository: HostingAgentKeyRepository,
) : HostingAgentKeyAuthenticator {

    private val random = SecureRandom()

    /** 새 키를 낸다. 회원마다 MAX_PER_USER 개까지 두고, 넘으면 가장 오래 쓰지 않은 키를 지운다. */
    @Transactional
    fun issue(userId: UUID): String {
        val key = HostingAgentKeyAuthenticator.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        hostingAgentKeyRepository.save(HostingAgentKey(userId, HostingAgentLogins.hash(key)))
        hostingAgentKeyRepository.findAllByUserIdOrderByLastUsedAtDesc(userId)
            .drop(HostingAgentKey.MAX_PER_USER)
            .forEach(hostingAgentKeyRepository::delete)
        return key
    }

    @Transactional
    override fun userIdOf(key: String): UUID? {
        val found = hostingAgentKeyRepository.findByKeyHash(HostingAgentLogins.hash(key)) ?: return null
        val now = utcNow()
        if (found.expired(now)) {
            hostingAgentKeyRepository.delete(found)
            return null
        }
        if (found.staleUse(now)) hostingAgentKeyRepository.recordUse(found.id, now)
        return found.userId
    }

    @Transactional
    @EventListener
    fun on(event: UserWithdrawn) {
        hostingAgentKeyRepository.deleteByUserId(event.userId)
    }
}
