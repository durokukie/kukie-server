package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingDbExport
import com.duro.kukie.hosting.domain.HostingFailure
import com.duro.kukie.hosting.presentation.dto.response.HostingDbExportResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingDbExportsResponse
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class GetHostingDbExportsService(
    private val hostingApps: HostingApps,
) {

    /** 최근 내보내기 10건. 끝난 것에는 10분 동안 쓸 수 있는 내려받기 주소를 붙인다. */
    operator fun invoke(userId: UUID): HostingDbExportsResponse {
        val account = hostingApps.accountOf(userId)
        val now = Instant.now()
        val exports = hostingApps.dbExports(account).map { export ->
            val shown = if (export.phase == RUNNING && export.updatedAt < now.minus(STALE_AFTER)) stale(export) else export
            HostingDbExportResponse.of(shown, shown.takeIf { it.phase == SUCCEEDED }?.let { hostingApps.dbExportDownloadUrl(account, it) })
        }
        return HostingDbExportsResponse(exports)
    }

    // 배포기가 내보내는 중에 재시작하면 running 으로 남는다
    private fun stale(export: HostingDbExport) =
        export.copy(phase = FAILED, failure = HostingFailure(STALE, "the deployer did not finish this export"))

    companion object {
        private const val RUNNING = "running"
        private const val SUCCEEDED = "succeeded"
        private const val FAILED = "failed"
        private const val STALE = "stale"
        private val STALE_AFTER: Duration = Duration.ofMinutes(30)
    }
}
