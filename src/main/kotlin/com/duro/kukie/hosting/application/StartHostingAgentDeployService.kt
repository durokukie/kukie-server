package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.application.port.out.HostingUploadStore
import com.duro.kukie.hosting.domain.HostingAgentDeployRepository
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingDeployInProgressException
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
    private val hostingAgentDeploys: HostingAgentDeploys,
    private val hostingAgentDeployRepository: HostingAgentDeployRepository,
    private val hostingUploadStore: HostingUploadStore,
    private val hostingCommandQueue: HostingCommandQueue,
) {

    private val log = logger()

    /**
     * 올라온 폴더로 배포하라고 배포기에 보낸다. 배포 하나에 명령은 하나다: 겹쳐 불러도 먼저 정한 commandId 를 준다. 실패한 배포를
     * 다시 하려면 새 배포를 만든다.
     */
    operator fun invoke(userId: UUID, id: String): HostingCommandResponse {
        val deploy = hostingAgentDeploys.requireOwned(userId, id)
        deploy.commandId?.let { return HostingCommandResponse(it) }
        if (hostingUploadStore.size(deploy.key) != deploy.bytes) throw HostingUploadMissingException()
        // 다른 회원의 진행 중 표시(GitHub 계정을 옮긴 경우)는 이 회원이 끝낼 수 없으니 치운다
        hostingAgentDeployRepository.activeOf(deploy.team)
            ?.takeIf { it != deploy.id && hostingAgentDeploys.activeOf(userId, deploy.team) == null }
            ?.let { hostingAgentDeployRepository.clearActive(deploy.team, it) }
        if (!hostingAgentDeployRepository.claimActive(deploy.team, deploy.id, ACTIVE_TTL)) {
            throw HostingDeployInProgressException("진행 중인 배포가 있습니다: ${hostingAgentDeployRepository.activeOf(deploy.team)}. 그 배포가 끝난 뒤 다시 시도해 주세요.")
        }

        val command = HostingCommand.upload(GitHubAccount(deploy.githubId, deploy.login), deploy)
        val commandId = hostingAgentDeployRepository.claimStart(deploy.id, command.commandId, HostingAgentDeploys.RECORD_TTL)
        if (commandId != command.commandId) return HostingCommandResponse(commandId)
        try {
            hostingCommandQueue.send(command)
        } catch (exception: RuntimeException) {
            hostingAgentDeployRepository.releaseStart(deploy.id)
            hostingAgentDeployRepository.clearActive(deploy.team, deploy.id)
            throw exception
        }
        hostingAgentDeploys.save(deploy.copy(commandId = commandId, startedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)))
        log.info("에이전트 배포를 시작했습니다: user={}, app={}/{}, deploy={}, command={}", userId, deploy.team, deploy.app, deploy.id, commandId)

        return HostingCommandResponse(commandId)
    }

    companion object {
        // 배포기가 업로드 명령을 받아 주는 동안(2시간)과 서버가 대기를 응답 없음으로 끝내는 때를 덮는다
        private val ACTIVE_TTL: Duration = Duration.ofHours(3)
    }
}
