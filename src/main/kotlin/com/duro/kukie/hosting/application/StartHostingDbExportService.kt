package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StartHostingDbExportService(
    private val hostingApps: HostingApps,
    private val hostingCommandSender: HostingCommandSender,
) {

    /** 팀 DB 를 pg_dump 로 내보내라고 배포기에 요청한다. 팀이 있는(가입한) 계정만, 팀마다 10분에 한 번. */
    operator fun invoke(userId: UUID): HostingCommandResponse {
        val account = hostingApps.accountOf(userId)
        if (!hostingApps.allowed(account)) throw HostingNotAllowedException()

        return hostingCommandSender.send(userId, HostingCommand.dbExport(account))
    }
}
