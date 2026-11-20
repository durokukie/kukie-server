package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.presentation.dto.response.HostingAppRecordResponse
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ExportHostingAppRecordService(
    private val hostingApps: HostingApps,
) {

    /** 앱 정보, 지금 상태, 남아 있는 배포 기록 전부(30일)를 한 파일로. 서비스가 끝나면 돌려주지 않으므로 사용자가 받아 둔다. */
    operator fun invoke(userId: UUID, app: String): HostingAppRecordResponse {
        val owned = hostingApps.requireOwned(userId, app)
        return HostingAppRecordResponse.of(
            Instant.now().truncatedTo(ChronoUnit.SECONDS),
            owned,
            hostingApps.suspension(owned),
            hostingApps.runtime(owned),
            hostingApps.allDeployments(owned),
        )
    }
}
