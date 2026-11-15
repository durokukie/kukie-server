package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.application.port.out.HostingUploadStore
import com.duro.kukie.hosting.domain.HostingAgentDeployRepository
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingDeployInProgressException
import com.duro.kukie.hosting.exception.HostingGitHubNotLinkedException
import com.duro.kukie.hosting.exception.HostingUploadMissingException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import com.duro.kukie.user.domain.GitHubAccount
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class StartHostingAgentDeployService(
    private val hostingApps: HostingApps,
    private val hostingAgentDeploys: HostingAgentDeploys,
    private val hostingAgentDeployRepository: HostingAgentDeployRepository,
    private val hostingUploadStore: HostingUploadStore,
    private val hostingCommandQueue: HostingCommandQueue,
    private val waitHostingAgentDeployService: WaitHostingAgentDeployService,
) {

    private val log = logger()

    /**
     * 올라온 폴더로 배포하라고 배포기에 보낸다. 배포 하나에 명령은 하나다: 겹쳐 불러도 먼저 정한 commandId 를 준다. 실패한 배포를
     * 다시 하려면 새 배포를 만든다.
     */
    operator fun invoke(userId: UUID, id: String): HostingCommandResponse {
        val deploy = hostingAgentDeploys.requireOwned(userId, id)
        deploy.commandId?.let { return HostingCommandResponse(it) }
        // 배포를 만든 뒤 GitHub 계정 연결이 풀리거나 다른 회원에게 옮겨졌으면 저장해 둔 계정으로 보내지 않는다
        val account = hostingApps.accountOf(userId)
        if (account == null || account.id != deploy.githubId || account.login.lowercase() != deploy.team) throw HostingGitHubNotLinkedException()
        if (hostingUploadStore.size(deploy.key) != deploy.bytes) throw HostingUploadMissingException()
        waitHostingAgentDeployService.settleActive(userId, deploy.team)
        // 다른 회원의 진행 중 표시(GitHub 계정을 옮긴 경우)는 이 회원이 끝낼 수 없으니 치운다
        hostingAgentDeployRepository.activeOf(deploy.team)
            ?.takeIf { it != deploy.id && hostingAgentDeploys.activeOf(userId, deploy.team) == null }
            ?.let { hostingAgentDeployRepository.clearActive(deploy.team, it) }
        if (!hostingAgentDeployRepository.claimActive(deploy.team, deploy.id, ACTIVE_TTL)) {
            throw HostingDeployInProgressException("진행 중인 배포가 있습니다: ${hostingAgentDeployRepository.activeOf(deploy.team)}. 그 배포가 끝난 뒤 다시 시도해 주세요.")
        }

        val command = HostingCommand.upload(GitHubAccount(deploy.githubId, deploy.login), deploy)
        var commandId = hostingAgentDeployRepository.claimStart(deploy.id, command.commandId, HostingAgentDeploys.RECORD_TTL)
        // 먼저 정한 쪽이 기록하기 전에 멈췄으면(정한 지 오래됐는데 기록에 commandId 가 없음) 그 표시를 풀고 다시 정한다
        if (commandId != command.commandId && hostingAgentDeploys.requireOwned(userId, id).commandId == null &&
            (hostingAgentDeployRepository.startClaimAge(deploy.id, HostingAgentDeploys.RECORD_TTL) ?: Duration.ZERO) > STALE_START
        ) {
            hostingAgentDeployRepository.releaseStart(deploy.id)
            commandId = hostingAgentDeployRepository.claimStart(deploy.id, command.commandId, HostingAgentDeploys.RECORD_TTL)
        }
        // 겹쳐 부른 쪽은 먼저 정한 명령을 받는다. 기록하고 보내는 것은 먼저 정한 쪽이 한다
        if (commandId != command.commandId) return HostingCommandResponse(commandId)
        // 보내기 전에 기록한다. 보낸 뒤 기록하지 못하면 보냈는지 알 수 없어 다시 불러도 고칠 수 없다. 기록하거나 보내지 못하면
        // 기록과 표시를 되돌려 다시 시작할 수 있게 한다. 기록한 뒤 보내기 전에 멈추면 wait 가 응답 없음(stuck)으로 끝낸다
        try {
            hostingAgentDeploys.save(deploy.copy(commandId = commandId, startedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)))
            hostingCommandQueue.send(command)
        } catch (exception: RuntimeException) {
            runCatching { hostingAgentDeploys.save(deploy) }
            hostingAgentDeployRepository.releaseStart(deploy.id)
            hostingAgentDeployRepository.clearActive(deploy.team, deploy.id)
            throw exception
        }
        log.info("에이전트 배포를 시작했습니다: user={}, app={}/{}, deploy={}, command={}", userId, deploy.team, deploy.app, deploy.id, commandId)

        return HostingCommandResponse(commandId)
    }

    companion object {
        // 배포기가 업로드 명령을 받아 주는 동안(2시간)과 서버가 대기를 응답 없음으로 끝내는 때를 덮는다
        private val ACTIVE_TTL: Duration = Duration.ofHours(3)

        // 정하고 기록하기까지는 Redis 호출 한 번 사이다. 이보다 오래 기록이 없으면 정한 쪽이 멈춘 것이다
        private val STALE_START: Duration = Duration.ofMinutes(1)
    }
}
