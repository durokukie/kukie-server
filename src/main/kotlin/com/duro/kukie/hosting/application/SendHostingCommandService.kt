package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingCommandCooldown
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingCommandTooSoonException
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
    operator fun invoke(userId: UUID, app: String, type: String): HostingCommandResponse {
        val command = HostingCommand.of(type, hostingApps.requireOwned(userId, app))
        if (!hostingCommandCooldown.tryStart(command)) throw HostingCommandTooSoonException()
        hostingCommandQueue.send(command)
        log.info("호스팅 명령을 보냈습니다: user={}, type={}, app={}/{}, command={}", userId, type, command.team, command.app, command.commandId)

        return HostingCommandResponse(command.commandId)
    }
}
