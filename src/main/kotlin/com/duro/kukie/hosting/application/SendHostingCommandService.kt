package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingAppSourceMismatchException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SendHostingCommandService(
    private val hostingApps: HostingApps,
    private val hostingCommandSender: HostingCommandSender,
) {

    /**
     * 배포기는 누가 요청했는지 보지 않으므로, 큐에 넣기 전에 여기서 앱의 주인인지 확인한다.
     * 업로드로 만든 앱은 다시 읽을 레포가 없어 재배포하지 않는다. 다시 올리거나 레포를 연결한다.
     */
    operator fun invoke(userId: UUID, app: String, type: String): HostingCommandResponse {
        val owned = hostingApps.requireOwned(userId, app)
        if (type == HostingCommand.REDEPLOY && owned.uploaded) throw HostingAppSourceMismatchException()

        return hostingCommandSender.send(userId, HostingCommand.of(type, owned))
    }
}
