package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.OAuthLogInCommand
import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.domain.Email
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
    operator fun invoke(command: OAuthLogInCommand): TokenResponse {
        val profile = oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier)
        val user = findOrCreateUser(profile)

        val accessToken = jwtTokenProvider.generateAccessToken(user.id)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
        refreshTokenRepository.save(user.id, refreshToken)

        return TokenResponse(accessToken, refreshToken)
    }

    private fun findOrCreateUser(profile: OAuthProfile): User {
        val email = Email(profile.email)
        userRepository.findByEmail(email)?.let { return refreshed(it, profile) }

        return try {
            userRepository.saveAndFlush(newUser(profile, email, withGitHub = true))
        } catch (e: DataIntegrityViolationException) {
            when ((e.cause as? ConstraintViolationException)?.constraintName) {
                User.EMAIL_UNIQUE_CONSTRAINT -> userRepository.findByEmail(email) ?: throw e
                User.GITHUB_ID_UNIQUE_CONSTRAINT -> signUpWithoutGitHub(profile, email)
                else -> throw e
            }
        }
    }

    // 방금 다른 회원이 같은 GitHub 계정을 연결했다. 가입은 막지 않고 연결만 뺀다
    private fun signUpWithoutGitHub(profile: OAuthProfile, email: Email): User =
        try {
            userRepository.saveAndFlush(newUser(profile, email, withGitHub = false))
        } catch (e: DataIntegrityViolationException) {
            userRepository.findByEmail(email) ?: throw e
        }

    private fun newUser(profile: OAuthProfile, email: Email, withGitHub: Boolean): User =
        User(
            name = profile.name.take(NAME_MAX_LENGTH),
            email = email,
        ).apply {
            changeAvatar(profile.avatarUrl)
            if (withGitHub) linkGitHubIfFree(profile)
        }

    private fun refreshed(user: User, profile: OAuthProfile): User {
        val before = Triple(user.avatarUrl, user.githubId, user.githubLogin)
        user.changeAvatar(profile.avatarUrl)
        user.linkGitHubIfFree(profile)
        if (before == Triple(user.avatarUrl, user.githubId, user.githubLogin)) return user

        return try {
            userRepository.save(user)
        } catch (e: DataIntegrityViolationException) {
            if ((e.cause as? ConstraintViolationException)?.constraintName != User.GITHUB_ID_UNIQUE_CONSTRAINT) throw e
            // 방금 다른 회원이 같은 GitHub 계정을 연결했다. 로그인은 막지 않는다
            userRepository.findByEmail(user.email) ?: throw e
        }
    }

    // 로그인은 있던 연결을 바꾸거나 다른 회원에게서 가져오지 않는다. 그것은 회원이 직접 하는 연결만 한다
    private fun User.linkGitHubIfFree(profile: OAuthProfile) {
        val gitHub = profile.gitHub ?: return
        when (githubId) {
            gitHub.id -> linkGitHub(gitHub)
            null -> if (!userRepository.existsByGithubId(gitHub.id)) linkGitHub(gitHub)
            else -> Unit
        }
    }

    companion object {
        private const val NAME_MAX_LENGTH = 50
    }
}
