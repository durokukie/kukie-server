package com.duro.kukie.gitops.application

import com.duro.kukie.gitops.application.port.`in`.GitHubSetupCommand
import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.application.port.out.GitHubInstallationInfo
import com.duro.kukie.gitops.application.port.out.InstallStateCodec
import com.duro.kukie.gitops.domain.GitHubInstallRequest
import com.duro.kukie.gitops.domain.GitHubInstallRequestRepository
import com.duro.kukie.gitops.domain.GitHubInstallation
import com.duro.kukie.gitops.domain.GitHubInstallationRepository
import com.duro.kukie.gitops.domain.InstallState
import com.duro.kukie.gitops.domain.InstallStateRepository
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.gitops.exception.InstallationNotFoundException
import com.duro.kukie.gitops.exception.InstallationTakenException
import com.duro.kukie.gitops.exception.InvalidInstallStateException
import com.duro.kukie.global.util.utcNow
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * GitHub 가 설치 뒤 Setup URL 로 보낸 브라우저 요청을 처리한다. 로그인 쿠키가 실리지 않는 요청이라 팀은 state 로만 안다.
 *
 * installation_id 는 주소에 실려 와 고칠 수 있다. 그래서 우리 앱의 설치인지 GitHub 에 묻고, 이미 다른 팀에 묶인 설치는
 * 받지 않는다(처음 묶은 팀이 갖는다). GitHub 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다.
 */
@Service
class HandleGitHubSetupService(
    private val installStateCodec: InstallStateCodec,
    private val installStateRepository: InstallStateRepository,
    private val gitHubAppGateway: GitHubAppGateway,
    private val gitHubInstallationRepository: GitHubInstallationRepository,
    private val gitHubInstallRequestRepository: GitHubInstallRequestRepository,
) {

    operator fun invoke(command: GitHubSetupCommand): GitHubSetupResult = when (command.setupAction) {
        ACTION_REQUEST -> request(consumeState(command.state))
        ACTION_INSTALL, ACTION_UPDATE -> {
            val installationId = command.installationId ?: throw InstallationNotFoundException()
            if (command.state != null) {
                install(consumeState(command.state), installationId, command.setupAction)
            } else {
                refreshBound(installationId)
            }
        }
        else -> throw InvalidInstallStateException()
    }

    /** 조직 관리자의 승인을 기다린다. 승인되면 관리자가 GitHub 에서 설치를 마치고, 팀은 설치를 한 번 더 눌러 묶는다. */
    private fun request(state: InstallState): GitHubSetupResult {
        gitHubInstallRequestRepository.save(GitHubInstallRequest(state.teamId, state.userId, utcNow()))
        return GitHubSetupResult(GitHubSetupResult.Outcome.REQUESTED, state.teamId)
    }

    private fun install(state: InstallState, installationId: Long, action: String): GitHubSetupResult {
        val existing = gitHubInstallationRepository.findByInstallationId(installationId)
        if (existing != null && existing.teamId != state.teamId) throw InstallationTakenException()

        val info = gitHubAppGateway.findInstallation(installationId) ?: throw InstallationNotFoundException()
        val repositories = repositoriesOf(info)
        val installation = existing?.apply { refresh(info.account, info.repositorySelection, repositories, info.suspended) }
            ?: GitHubInstallation(
                teamId = state.teamId,
                installationId = installationId,
                installedBy = state.userId,
                account = info.account,
                repositorySelection = info.repositorySelection,
                repositories = repositories,
                suspended = info.suspended,
            )
        try {
            gitHubInstallationRepository.saveAndFlush(installation)
        } catch (_: DataIntegrityViolationException) {
            // 같은 설치를 두 팀이 동시에 묶으려 했다
            throw InstallationTakenException()
        }
        gitHubInstallRequestRepository.deleteById(state.teamId)

        val outcome = if (existing == null && action == ACTION_INSTALL) {
            GitHubSetupResult.Outcome.INSTALLED
        } else {
            GitHubSetupResult.Outcome.UPDATED
        }
        return GitHubSetupResult(outcome, state.teamId)
    }

    /** GitHub 설정에서 레포 고르기를 바꾸면 state 없이 온다. 이미 묶인 설치의 정보만 GitHub 에서 다시 읽는다. */
    private fun refreshBound(installationId: Long): GitHubSetupResult {
        val installation = gitHubInstallationRepository.findByInstallationId(installationId)
            ?: throw InstallationNotFoundException()
        val info = gitHubAppGateway.findInstallation(installationId) ?: throw InstallationNotFoundException()
        installation.refresh(info.account, info.repositorySelection, repositoriesOf(info), info.suspended)
        gitHubInstallationRepository.save(installation)

        return GitHubSetupResult(GitHubSetupResult.Outcome.UPDATED, installation.teamId)
    }

    private fun repositoriesOf(info: GitHubInstallationInfo): List<String> =
        if (info.suspended) {
            emptyList()
        } else {
            gitHubAppGateway.installationRepositories(info.installationId).map(RepoName::fullName)
        }

    private fun consumeState(token: String?): InstallState {
        val state = token?.let(installStateCodec::decode) ?: throw InvalidInstallStateException()
        val teamId = installStateRepository.take(state.nonce) ?: throw InvalidInstallStateException()
        if (teamId != state.teamId) throw InvalidInstallStateException()

        return state
    }

    companion object {
        private const val ACTION_INSTALL = "install"
        private const val ACTION_UPDATE = "update"
        private const val ACTION_REQUEST = "request"
    }
}
