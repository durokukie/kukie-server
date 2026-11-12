package com.duro.kukie.serving.application.port.out

import com.duro.kukie.serving.presentation.dto.response.ServingUsageOverviewResponse
import com.duro.kukie.serving.presentation.dto.response.ServingUsageResponse
import java.time.LocalDate
import java.util.UUID

interface ServingUsageQueries {
    fun query(clusterId: UUID, userId: UUID?, from: LocalDate, to: LocalDate, admin: Boolean): ServingUsageResponse

    /** 현재 시를 포함한 최근 24개 UTC 시간의 요약. model을 주면 사용량과 거절을 그 모델로 좁힌다. */
    fun overview(clusterId: UUID, model: String?): ServingUsageOverviewResponse
}
