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
import com.duro.kukie.gitops.exception.InstallationNotVerifiedException
import com.duro.kukie.gitops.exception.InstallationTakenException
import com.duro.kukie.gitops.exception.InvalidInstallStateException
import com.duro.kukie.global.config.properties.GitHubAppProperties
import com.duro.kukie.global.util.utcNow
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * GitHub 가 설치 뒤 Setup URL 로 보낸 브라우저 요청을 처리한다. 로그인 쿠키가 실리지 않는 요청이라 팀은 state 로만 안다.
 * state 를 확인하기 전에는 GitHub 를 부르지 않는다 — 누구나 부를 수 있는 주소라 앱 rate limit 을 쓰게 하면 안 된다.
 *
 * installation_id 는 주소에 실려 와 고칠 수 있다. 그래서 우리 앱의 설치인지 GitHub 에 묻고, 이미 다른 팀에 묶인 설치는
 * 받지 않는다. 아직 묶이지 않은 설치는 설치한 사람이 그 설치에 접근할 수 있을 때만 묶는다(사용자 인증이 켜져 있으면
 * GitHub 의 /user/installations 로, 아니면 state 를 발급한 뒤 새로 생긴 설치인지로).
 * GitHub 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다.
 */
@Service
class HandleGitHubSetupService(
    private val installStateCodec: InstallStateCodec,
    private val installStateRepository: InstallStateRepository,
    private val gitHubAppGateway: GitHubAppGateway,
    private val gitHubInstallationRepository: GitHubInstallationRepository,
    private val gitHubInstallRequestRepository: GitHubInstallRequestRepository,
    private val gitHubAppProperties: GitHubAppProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    operator fun invoke(command: GitHubSetupCommand): GitHubSetupResult {
        // GitHub 설정에서 레포 고르기를 바꾸면 state 없이 온다. 누가 보냈는지 모르니 아무것도 읽거나 바꾸지 않는다
        if (command.state == null && command.setupAction == ACTION_UPDATE) {
            return GitHubSetupResult(GitHubSetupResult.Outcome.UPDATED, teamId = null)
        }
        val state = consumeState(command.state)

        return when (command.setupAction) {
            ACTION_REQUEST -> request(state)
            ACTION_INSTALL, ACTION_UPDATE ->
                install(state, command.installationId ?: throw InstallationNotFoundException(), command)
            else -> throw InvalidInstallStateException()
        }
    }

    /** 조직 관리자의 승인을 기다린다. 승인되면 관리자가 GitHub 에서 설치를 마치고, 팀은 설치를 한 번 더 눌러 묶는다. */
    private fun request(state: InstallState): GitHubSetupResult {
        gitHubInstallRequestRepository.save(GitHubInstallRequest(state.teamId, state.userId, utcNow()))
        return GitHubSetupResult(GitHubSetupResult.Outcome.REQUESTED, state.teamId)
    }

    private fun install(state: InstallState, installationId: Long, command: GitHubSetupCommand): GitHubSetupResult {
        val existing = gitHubInstallationRepository.findByInstallationId(installationId)
        if (existing != null && existing.teamId != state.teamId) throw InstallationTakenException()
        if (existing == null && gitHubAppProperties.verifiesInstaller) requireInstallerAccess(installationId, command.code)

        val info = gitHubAppGateway.findInstallation(installationId) ?: throw InstallationNotFoundException()
        if (existing == null && !gitHubAppProperties.verifiesInstaller) requireCreatedAfter(state, info, command.setupAction)

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

        val outcome = if (existing == null && command.setupAction == ACTION_INSTALL) {
            GitHubSetupResult.Outcome.INSTALLED
        } else {
            GitHubSetupResult.Outcome.UPDATED
        }
        return GitHubSetupResult(outcome, state.teamId)
    }

    /** 설치 화면의 사용자 인증으로 받은 사용자가 그 설치에 접근할 수 있어야 한다. */
    private fun requireInstallerAccess(installationId: Long, code: String?) {
        if (code.isNullOrBlank()) throw InstallationNotVerifiedException()
        if (installationId !in gitHubAppGateway.userInstallationIds(code)) throw InstallationNotVerifiedException()
    }

    /**
     * 사용자 인증이 꺼져 있을 때의 막이. 이 state 로 방금 새로 만든 설치만 묶는다 — Kukie 밖에서 미리 설치해 둔 남의 설치를
     * id 만 바꿔 가져가지 못하게 한다. 이미 있던 설치를 팀에 묶으려면 사용자 인증을 켜야 한다.
     */
    private fun requireCreatedAfter(state: InstallState, info: GitHubInstallationInfo, action: String?) {
        val earliest = state.issuedAt - CLOCK_SKEW
        val latest = minOf(state.issuedAt + INSTALL_WINDOW, clock.instant()) + CLOCK_SKEW
        if (action != ACTION_INSTALL || info.createdAt.isBefore(earliest) || info.createdAt.isAfter(latest)) {
            throw InstallationNotVerifiedException()
        }
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
        private val INSTALL_WINDOW: Duration = Duration.ofMinutes(10)
        private val CLOCK_SKEW: Duration = Duration.ofMinutes(1)
    }
}
