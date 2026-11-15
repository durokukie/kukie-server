package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.domain.RefreshTokenRotation
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.JwtTokenProvider
import org.springframework.stereotype.Service
import java.util.UUID

/** 보낸 리프레시 토큰의 세션만 돌린다. 다른 창·기기의 세션은 건드리지 않는다. */
@Service
class RefreshTokenService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val logInSessions: LogInSessions,
) {
    operator fun invoke(request: RefreshTokenRequest): TokenResponse {
        val subject = jwtTokenProvider.parseRefreshToken(request.refreshToken)
            ?: throw InvalidTokenException()
        val sessionId = subject.sessionId
            ?: return migrateLegacy(subject.userId, request.refreshToken)

        val newAccessToken = jwtTokenProvider.generateAccessToken(subject.userId, sessionId)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken(subject.userId, sessionId)

        return when (val rotation = refreshTokenRepository.rotate(subject.userId, sessionId, request.refreshToken, newRefreshToken)) {
            RefreshTokenRotation.Rotated -> TokenResponse(newAccessToken, newRefreshToken)
            is RefreshTokenRotation.RecentlyRotated -> TokenResponse(newAccessToken, rotation.currentToken)
            RefreshTokenRotation.Reused, RefreshTokenRotation.Missing -> throw InvalidTokenException()
        }
    }

    // 세션 도입 전에 받은 토큰. 옛 키와 맞으면 한 번 받아 주고 새 세션으로 옮긴다. 배포만으로 로그아웃되지 않게 한다
    private fun migrateLegacy(userId: UUID, refreshToken: String): TokenResponse {
        if (!refreshTokenRepository.takeLegacy(userId, refreshToken)) throw InvalidTokenException()
        return logInSessions.open(userId)
    }
}
