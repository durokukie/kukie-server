package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class OAuthLogInService(
    private val oAuthClient: OAuthClient,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    // 외부 HTTP 호출을 트랜잭션 밖에서 수행하고, 동시 가입으로 유니크 제약이 충돌하면 재조회로 복구해야 하므로 @Transactional을 걸지 않는다
    operator fun invoke(provider: OAuthProvider, request: OAuthLogInRequest): TokenResponse {
        val profile = oAuthClient.fetchProfile(provider, request.code, request.redirectUri, request.codeVerifier)
        val user = findOrCreateUser(profile)

        val accessToken = jwtTokenProvider.generateAccessToken(user.id)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
        refreshTokenRepository.save(user.id, refreshToken)

        return TokenResponse(accessToken, refreshToken)
    }

    private fun findOrCreateUser(profile: OAuthProfile): User {
        userRepository.findByEmail(profile.email)?.let { return it }

        val user = User(
            name = profile.name.take(NAME_MAX_LENGTH),
            email = profile.email,
        )

        return try {
            userRepository.saveAndFlush(user)
        } catch (e: DataIntegrityViolationException) {
            val cause = e.cause
            if (cause is ConstraintViolationException && cause.constraintName == User.EMAIL_UNIQUE_CONSTRAINT) {
                userRepository.findByEmail(profile.email) ?: throw e
            } else {
                throw e
            }
        }
    }

    companion object {
        private const val NAME_MAX_LENGTH = 50
    }
}
