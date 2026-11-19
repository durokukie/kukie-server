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
        val account = hostingApps.accountOf(userId)
        val apps = hostingApps.ownedBy(account)

        val skipped = hostingApps.skippedPush(account, apps)
        val suspensions = apps.associate { it.app to hostingApps.suspension(it) }
        return HostingAppsResponse.of(githubApp, account, hostingApps.allowed(account), apps, suspensions, skipped, hostingApps.repoList(account).takeIf { account.gitHub })
    }
}
