package com.duro.kukie.serving.application

import com.duro.kukie.serving.application.port.out.ServingUsageQueries
import com.duro.kukie.serving.presentation.dto.response.ServingUsageOverviewResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetAdminServingUsageOverviewService(private val permission: ServingAccessPermission, private val queries: ServingUsageQueries) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    operator fun invoke(clusterId: UUID, userId: UUID, model: String?): ServingUsageOverviewResponse {
        permission.requireAdmin(clusterId, userId)
        return queries.overview(clusterId, model?.takeIf { it.isNotBlank() })
    }
}
