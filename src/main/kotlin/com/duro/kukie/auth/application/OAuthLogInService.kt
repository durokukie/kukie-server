package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.OAuthLogInCommand
import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.lockByIdOrThrow
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class OAuthLogInService(
    private val oAuthClient: OAuthClient,
    private val userRepository: UserRepository,
    private val logInSessions: LogInSessions,
    private val transactionTemplate: TransactionTemplate,
) {

    // 외부 HTTP 호출을 트랜잭션 밖에서 수행하고, 동시 가입으로 유니크 제약이 충돌하면 재조회로 복구해야 하므로 @Transactional을 걸지 않는다
    operator fun invoke(command: OAuthLogInCommand): TokenResponse {
        val profile = oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier)
        val user = findOrCreateUser(profile)

        return logInSessions.open(user.id)
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
        if (!user.refresh(profile)) return user

        return try {
            // 연결 API 가 그사이 연결한 계정을 덮지 않게 회원 행을 잠그고 다시 판단한다
            transactionTemplate.execute {
                userRepository.saveAndFlush(userRepository.lockByIdOrThrow(user.id).apply { refresh(profile) })
            }
        } catch (e: DataIntegrityViolationException) {
            if ((e.cause as? ConstraintViolationException)?.constraintName != User.GITHUB_ID_UNIQUE_CONSTRAINT) throw e
            // 방금 다른 회원이 같은 GitHub 계정을 연결했다. 로그인은 막지 않는다
            userRepository.findByEmail(user.email) ?: throw e
        }
    }

    private fun User.refresh(profile: OAuthProfile): Boolean {
        val before = Triple(avatarUrl, githubId, githubLogin)
        changeAvatar(profile.avatarUrl)
        linkGitHubIfFree(profile)
        return before != Triple(avatarUrl, githubId, githubLogin)
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
