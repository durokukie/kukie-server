package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.hosting.presentation.dto.response.HostingAppsResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingAppsService(
    private val hostingApps: HostingApps,
    private val hostingProperties: HostingProperties,
) {

    operator fun invoke(userId: UUID): HostingAppsResponse {
        val githubApp = hostingProperties.githubApp
        val account = hostingApps.accountOf(userId) ?: return HostingAppsResponse.notLinked(githubApp)
        val apps = hostingApps.ownedBy(account)

        return HostingAppsResponse.of(githubApp, account, apps, hostingApps.skippedPush(account, apps), hostingApps.repoList(account))
    }
}
