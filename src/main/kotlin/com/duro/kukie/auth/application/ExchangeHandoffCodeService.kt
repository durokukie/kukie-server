package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.HandoffCodeRepository
import com.duro.kukie.auth.domain.PkceS256
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.exception.InvalidHandoffCodeException
import com.duro.kukie.auth.presentation.dto.request.ExchangeHandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.JwtTokenProvider
import org.springframework.stereotype.Service

/** 앱 넘겨주기 코드를 PKCE 원본과 대조해 토큰으로 바꾼다. 코드는 꺼내는 순간 지워진다. */
@Service
class ExchangeHandoffCodeService(
    private val handoffCodeRepository: HandoffCodeRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    operator fun invoke(request: ExchangeHandoffCodeRequest): TokenResponse {
        val ticket = handoffCodeRepository.take(request.code) ?: throw InvalidHandoffCodeException()
        if (!PkceS256.matches(request.codeVerifier, ticket.codeChallenge)) throw InvalidHandoffCodeException()

        val accessToken = jwtTokenProvider.generateAccessToken(ticket.userId)
        val refreshToken = jwtTokenProvider.generateRefreshToken(ticket.userId)
        refreshTokenRepository.save(ticket.userId, refreshToken)

        return TokenResponse(accessToken, refreshToken)
    }
}
