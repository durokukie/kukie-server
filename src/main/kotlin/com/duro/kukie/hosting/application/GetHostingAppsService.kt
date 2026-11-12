package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.presentation.dto.response.HostingAppsResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingAppsService(
    private val hostingApps: HostingApps,
) {

    operator fun invoke(userId: UUID): HostingAppsResponse {
        val account = hostingApps.accountOf(userId) ?: return HostingAppsResponse.notLinked()
        val apps = hostingApps.ownedBy(account)

        return HostingAppsResponse.of(account, apps, hostingApps.skippedPush(account, apps), hostingApps.repoList(account))
    }
}
