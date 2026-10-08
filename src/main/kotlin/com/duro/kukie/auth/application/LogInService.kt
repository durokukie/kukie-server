package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.LogInCommand
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
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
    operator fun invoke(command: LogInCommand): TokenResponse {
        val user = userRepository.findByEmail(command.email)
            ?.takeIf { it.matchesPassword(command.password, passwordEncoder) }
            ?: throw InvalidCredentialsException()

        val accessToken = jwtTokenProvider.generateAccessToken(user.id)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
        refreshTokenRepository.save(user.id, refreshToken)

        return TokenResponse(accessToken, refreshToken)
    }
}
