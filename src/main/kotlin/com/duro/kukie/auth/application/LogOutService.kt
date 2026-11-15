package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LogOutService(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    /** 지금 쓰는 세션만 끊는다. [sessionId] 가 없으면 세션 도입 전 액세스 토큰이라 옛 키를 지운다. */
    operator fun invoke(userId: UUID, sessionId: String?) {
        if (sessionId != null) {
            refreshTokenRepository.delete(userId, sessionId)
        } else {
            refreshTokenRepository.deleteLegacy(userId)
        }
    }
}
