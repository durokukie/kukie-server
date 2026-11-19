package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.presentation.dto.response.HostingAppDetailResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingAppService(
    private val hostingApps: HostingApps,
    private val hostingFailureAnalyzer: HostingFailureAnalyzer,
) {

    operator fun invoke(userId: UUID, app: String): HostingAppDetailResponse {
        val owned = hostingApps.requireOwned(userId, app)

        return HostingAppDetailResponse.of(owned, hostingApps.deployments(owned), hostingFailureAnalyzer.enabled)
    }
}
