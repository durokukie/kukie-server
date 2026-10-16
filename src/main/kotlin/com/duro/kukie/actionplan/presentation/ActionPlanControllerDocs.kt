package com.duro.kukie.actionplan.presentation

import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.actionplan.exception.ActionPlanNotFoundException
import com.duro.kukie.actionplan.presentation.dto.response.ActionPlanDetailResponse
import com.duro.kukie.actionplan.presentation.dto.response.ActionPlanSummaryResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface ActionPlanControllerDocs {

    @Operation(
        summary = "Action Plan 목록 조회",
        description = "읽을 수 있는 대화의 계획을 최근에 바뀐 순으로 조회합니다. clusterId·status 로 좁힐 수 있습니다.",
    )
    fun getActionPlans(userId: UUID, clusterId: UUID?, status: PlanStatus?): ResponseEntity<List<ActionPlanSummaryResponse>>

    @Operation(
        summary = "Action Plan 조회",
        description = "계획 하나를 사람이 읽는 Markdown 전문과 함께 조회합니다. 계획이 속한 대화를 읽을 수 없으면 404 입니다.",
    )
    @ApiErrorResponses(ActionPlanNotFoundException::class)
    fun getActionPlan(planId: UUID, userId: UUID): ResponseEntity<ActionPlanDetailResponse>
}
