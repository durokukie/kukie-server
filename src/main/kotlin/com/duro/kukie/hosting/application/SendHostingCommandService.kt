package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SendHostingCommandService(
    private val hostingApps: HostingApps,
    private val hostingCommandSender: HostingCommandSender,
) {

    /** 배포기는 누가 요청했는지 보지 않으므로, 큐에 넣기 전에 여기서 앱의 주인인지 확인한다. */
    operator fun invoke(userId: UUID, app: String, type: String): HostingCommandResponse =
        hostingCommandSender.send(userId, HostingCommand.of(type, hostingApps.requireOwned(userId, app)))
}
