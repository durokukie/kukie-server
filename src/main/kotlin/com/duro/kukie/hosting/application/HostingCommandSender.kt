package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingCommandCooldown
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingCommandTooSoonException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Component
import java.util.UUID

/** 명령을 큐에 넣는다. 같은 대상에 같은 명령을 방금 보냈으면 넣지 않는다. 누구의 것인지는 부르는 쪽이 먼저 확인한다. */
@Component
class HostingCommandSender(
    private val hostingCommandQueue: HostingCommandQueue,
    private val hostingCommandCooldown: HostingCommandCooldown,
) {

    private val log = logger()

    fun send(userId: UUID, command: HostingCommand): HostingCommandResponse {
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
