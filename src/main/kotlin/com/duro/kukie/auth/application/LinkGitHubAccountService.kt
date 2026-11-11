package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.LinkGitHubAccountCommand
import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.GitHubAccountTakenException
import com.duro.kukie.auth.exception.GitHubAlreadyLinkedException
import com.duro.kukie.auth.exception.GitHubLinkFailedException
import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.presentation.dto.response.UserResponse
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class LinkGitHubAccountService(
    private val oAuthClient: OAuthClient,
    private val userRepository: UserRepository,
) {

    // 외부 HTTP 호출을 트랜잭션 밖에서 수행해야 하므로 @Transactional을 걸지 않는다
    operator fun invoke(command: LinkGitHubAccountCommand): UserResponse {
        val gitHub = try {
            oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier).gitHub
        } catch (e: OAuthLogInFailedException) {
            null
        } ?: throw GitHubLinkFailedException()

        val user = userRepository.findByIdOrThrow(command.userId)
        if (user.githubId != null && user.githubId != gitHub.id) throw GitHubAlreadyLinkedException()

        // 이 GitHub 계정의 주인임을 방금 증명했으므로, 다른 회원에 남아 있던 연결은 이 회원으로 옮긴다
        userRepository.unlinkGitHubFromOthers(gitHub.id, user.id)
        user.linkGitHub(gitHub)

        return try {
            UserResponse.from(userRepository.saveAndFlush(user))
        } catch (e: DataIntegrityViolationException) {
            if ((e.cause as? ConstraintViolationException)?.constraintName != User.GITHUB_ID_UNIQUE_CONSTRAINT) throw e
            throw GitHubAccountTakenException()
        }
    }
}
