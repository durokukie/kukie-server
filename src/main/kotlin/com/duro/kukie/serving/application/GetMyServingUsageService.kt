package com.duro.kukie.serving.application

import com.duro.kukie.serving.application.port.out.ServingUsageQueries
import com.duro.kukie.serving.presentation.dto.response.ServingUsageResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class GetMyServingUsageService(private val permission: ServingApiKeyPermission, private val queries: ServingUsageQueries) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    operator fun invoke(accessId: UUID, userId: UUID, from: LocalDate?, to: LocalDate?): ServingUsageResponse {
        val access = permission.requireOwner(accessId, userId)
        val (start, end) = usageWindow(from, to)
        return queries.query(access.clusterId, userId, start, end, false)
    }
}
