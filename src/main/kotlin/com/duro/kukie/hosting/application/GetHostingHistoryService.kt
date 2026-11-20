package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.presentation.dto.response.HostingHistoryResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingHistoryService(
    private val hostingApps: HostingApps,
) {

    operator fun invoke(userId: UUID): HostingHistoryResponse {
        val account = hostingApps.accountOf(userId)
        return HostingHistoryResponse.of(hostingApps.pastApps(account, hostingApps.ownedBy(account)))
    }
}
