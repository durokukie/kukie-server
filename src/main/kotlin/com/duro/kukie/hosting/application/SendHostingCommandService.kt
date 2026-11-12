package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingCommandCooldown
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingCommandTooSoonException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingRepositoryNotFoundException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SendHostingCommandService(
    private val hostingApps: HostingApps,
    private val hostingCommandQueue: HostingCommandQueue,
    private val hostingCommandCooldown: HostingCommandCooldown,
) {

    private val log = logger()

    /** 배포기는 누가 요청했는지 보지 않으므로, 큐에 넣기 전에 여기서 앱의 주인인지 확인한다. */
    operator fun invoke(userId: UUID, app: String, type: String): HostingCommandResponse =
        send(userId, HostingCommand.of(type, hostingApps.requireOwned(userId, app)))

    /** 회원이 GitHub App 에 허용한 레포 가운데 하나로 앱을 시작한다. 그 뒤로는 그 레포에 push 하면 다시 배포된다. */
    fun deploy(userId: UUID, repository: String): HostingCommandResponse {
        val account = hostingApps.accountOf(userId) ?: throw HostingRepositoryNotFoundException()
        val repos = hostingApps.repoList(account) ?: throw HostingRepositoryNotFoundException()
        val repo = repos.repos.firstOrNull { it.fullName.equals(repository, ignoreCase = true) } ?: throw HostingRepositoryNotFoundException()
        if (!repos.allowed) throw HostingNotAllowedException()

        return send(userId, HostingCommand.deploy(account, repo.fullName))
    }

    private fun send(userId: UUID, command: HostingCommand): HostingCommandResponse {
        if (!hostingCommandCooldown.tryStart(command)) throw HostingCommandTooSoonException()
        try {
            hostingCommandQueue.send(command)
        } catch (exception: RuntimeException) {
            hostingCommandCooldown.release(command)
            throw exception
        }
        log.info("호스팅 명령을 보냈습니다: user={}, type={}, app={}/{}, command={}", userId, command.type, command.team, command.target, command.commandId)

        return HostingCommandResponse(command.commandId)
    }
}
