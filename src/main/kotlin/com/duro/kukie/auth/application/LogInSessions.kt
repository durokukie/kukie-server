package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.JwtTokenProvider
import org.springframework.stereotype.Component
import java.util.UUID

/** 로그인할 때마다 새 세션을 열고 그 세션의 토큰을 낸다. 로그인 경로는 모두 여기를 지난다. */
@Component
class LogInSessions(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    fun open(userId: UUID): TokenResponse {
        val sessionId = UUID.randomUUID().toString()
        val accessToken = jwtTokenProvider.generateAccessToken(userId, sessionId)
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId, sessionId)
        refreshTokenRepository.create(userId, sessionId, refreshToken)

        return TokenResponse(accessToken, refreshToken)
    }
}
