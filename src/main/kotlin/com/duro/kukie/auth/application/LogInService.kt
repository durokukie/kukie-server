package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.dto.request.LogInRequest
import com.duro.kukie.auth.dto.response.TokenResponse
import com.duro.kukie.auth.exception.InvalidCredentialsException
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.user.domain.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LogInService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional(readOnly = true)
    fun logIn(request: LogInRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?.takeIf { passwordEncoder.matches(request.password, it.password) }
            ?: throw InvalidCredentialsException()

        val accessToken = jwtTokenProvider.generateAccessToken(user.id)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
        refreshTokenRepository.save(user.id, refreshToken)

        return TokenResponse(accessToken, refreshToken)
    }
}
