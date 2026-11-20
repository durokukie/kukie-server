package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.exception.HostingLogNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingPastBuildLogService(
    private val hostingApps: HostingApps,
) {

    operator fun invoke(userId: UUID, app: String, generation: String, deploymentId: String): String =
        hostingApps.pastBuildLog(hostingApps.accountOf(userId), app, generation, deploymentId) ?: throw HostingLogNotFoundException()
}
