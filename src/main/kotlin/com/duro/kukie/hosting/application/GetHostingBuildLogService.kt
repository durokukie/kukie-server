package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.exception.HostingLogNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingBuildLogService(
    private val hostingApps: HostingApps,
) {

    operator fun invoke(userId: UUID, app: String, deploymentId: String): String =
        hostingApps.buildLog(hostingApps.requireOwned(userId, app), deploymentId) ?: throw HostingLogNotFoundException()
}
