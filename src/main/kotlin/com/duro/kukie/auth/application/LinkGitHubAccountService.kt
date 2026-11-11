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
import com.duro.kukie.user.domain.lockByIdOrThrow
import com.duro.kukie.user.presentation.dto.response.UserResponse
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class LinkGitHubAccountService(
    private val oAuthClient: OAuthClient,
    private val userRepository: UserRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    // 외부 HTTP 호출은 트랜잭션 밖에서 하고, 옮기기와 연결은 한 트랜잭션으로 묶어 실패하면 이전 연결이 그대로 남게 한다
    operator fun invoke(command: LinkGitHubAccountCommand): UserResponse {
        val gitHub = try {
            oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier).gitHub
        } catch (e: OAuthLogInFailedException) {
            null
        } ?: throw GitHubLinkFailedException()

        return try {
            transactionTemplate.execute {
                // 같은 회원의 연결 요청이 겹쳐도 나중 요청이 앞선 연결을 보고 거절되게 회원 행을 잠근다
                val user = userRepository.lockByIdOrThrow(command.userId)
                if (user.githubId != null && user.githubId != gitHub.id) throw GitHubAlreadyLinkedException()

                // 이 GitHub 계정의 주인임을 방금 증명했으므로, 다른 회원에 남아 있던 연결은 이 회원으로 옮긴다
                userRepository.unlinkGitHubFromOthers(gitHub.id, user.id)
                user.linkGitHub(gitHub)
                UserResponse.from(userRepository.saveAndFlush(user))
            }
        } catch (e: DataIntegrityViolationException) {
            if ((e.cause as? ConstraintViolationException)?.constraintName != User.GITHUB_ID_UNIQUE_CONSTRAINT) throw e
            throw GitHubAccountTakenException()
        }
    }
}
