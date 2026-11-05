package com.duro.kukie.serving.application.port.out

import com.duro.kukie.serving.presentation.dto.response.ServingUsageResponse
import java.time.LocalDate
import java.util.UUID

interface ServingUsageQueries {
    fun query(clusterId: UUID, userId: UUID?, from: LocalDate, to: LocalDate, admin: Boolean): ServingUsageResponse
}
