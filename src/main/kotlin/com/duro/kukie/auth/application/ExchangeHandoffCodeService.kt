package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.HandoffCodeRepository
import com.duro.kukie.auth.domain.PkceS256
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.exception.InvalidHandoffCodeException
import com.duro.kukie.auth.presentation.dto.request.ExchangeHandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.JwtTokenProvider
import org.springframework.stereotype.Service

/**
 * 앱이 딥링크로 받은 1회용 코드를 토큰(과 쿠키)으로 바꾼다 (DURO-109). 코드는 꺼내는 순간 지워지고,
 * PKCE 원본이 시작 때 맡긴 검증값과 맞아야 한다 — 코드만 가로챈 쪽은 여기서 막힌다.
 */
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
