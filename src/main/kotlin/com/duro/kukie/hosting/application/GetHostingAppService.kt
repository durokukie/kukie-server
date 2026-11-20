package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.presentation.dto.response.HostingAppDetailResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingAppService(
    private val hostingApps: HostingApps,
    private val hostingFailureAnalyzer: HostingFailureAnalyzer,
    private val hostingStartRetries: HostingStartRetries,
) {

    operator fun invoke(userId: UUID, app: String): HostingAppDetailResponse {
        val owned = hostingApps.requireOwned(userId, app)
        val deployments = hostingApps.deployments(owned)
        // 마지막 배포가 시작하지 못했으면 여기서 재시도를 시작한다. 나머지는 이미 한 일만 읽는다
        val latest = deployments.firstOrNull()
        val startRetries = deployments
            .filter { it.failure?.code == START_FAILED }
            .mapNotNull { deployment ->
                val retry = if (deployment == latest) hostingStartRetries.follow(userId, owned, deployment) else hostingStartRetries.of(owned, deployment)
                retry?.let { deployment.deploymentId to it }
            }
            .toMap()

        return HostingAppDetailResponse.of(owned, hostingApps.suspension(owned), deployments, hostingFailureAnalyzer.enabled, startRetries)
    }

    companion object {
        private const val START_FAILED = "start_failed"
    }
}
