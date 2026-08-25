package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.JwtTokenProvider
import org.springframework.stereotype.Service

@Service
class RefreshTokenService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    operator fun invoke(request: RefreshTokenRequest): TokenResponse {
        val userId = jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken)
            ?: throw InvalidTokenException()

        val savedRefreshToken = refreshTokenRepository.findByUserId(userId)

        if (savedRefreshToken != request.refreshToken) {
            refreshTokenRepository.deleteByUserId(userId)
            throw InvalidTokenException()
        }

        val newAccessToken = jwtTokenProvider.generateAccessToken(userId)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken(userId)

        refreshTokenRepository.save(userId, newRefreshToken)

        return TokenResponse(newAccessToken, newRefreshToken)
    }
}
