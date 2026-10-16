package com.duro.kukie.actionplan.presentation

import com.duro.kukie.actionplan.exception.ActionPlanNotFoundException
import com.duro.kukie.actionplan.exception.PlanCallMismatchException
import com.duro.kukie.actionplan.exception.PlanStateConflictException
import com.duro.kukie.actionplan.presentation.dto.request.CreatePlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.DecidePlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.FailPlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.OfferPlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.RecordDryRunRequest
import com.duro.kukie.actionplan.presentation.dto.request.RecordPlanResultRequest
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.auth.exception.InvalidInternalTokenException
import com.duro.kukie.chat.exception.RunNotFoundException
import com.duro.kukie.global.docs.ApiErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface InternalActionPlanControllerDocs {

    @Operation(
        summary = "[내부] Action Plan 생성",
        description = "DRAFT 로 만들고 표시용 code(ap-YYMMDD-HHMM-<tool>[-N])를 붙입니다. 같은 run 에 같은 toolCallId 가 있으면 그 계획을 200 으로 줍니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(InvalidInternalTokenException::class, RunNotFoundException::class)
    fun createPlan(runId: UUID, request: CreatePlanRequest): ResponseEntity<PlanResponse>

    @Operation(summary = "[내부] run 의 Action Plan 목록", description = "만든 순입니다. toolCallId 를 주면 0~1개입니다.")
    @ApiErrorResponses(InvalidInternalTokenException::class, RunNotFoundException::class)
    fun getRunPlans(runId: UUID, toolCallId: String?): ResponseEntity<List<PlanResponse>>

    @Operation(summary = "[내부] Action Plan 조회")
    @ApiErrorResponses(InvalidInternalTokenException::class, ActionPlanNotFoundException::class)
    fun getPlan(planId: UUID): ResponseEntity<PlanResponse>

    @Operation(
        summary = "[내부] 예행 결과 기록",
        description = "DRAFT 이고 예행 결과가 없을 때만. status 가 failed 면 FAILED(DRY_RUN_FAILED) 가 됩니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, ActionPlanNotFoundException::class, PlanStateConflictException::class)
    fun recordDryRun(planId: UUID, request: RecordDryRunRequest): ResponseEntity<PlanResponse>

    @Operation(
        summary = "[내부] 승인 요청",
        description = "예행을 통과한(succeeded·unsupported) DRAFT 에 판단 가이드를 붙여 WAITING_APPROVAL 로 바꿉니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, ActionPlanNotFoundException::class, PlanStateConflictException::class)
    fun offerPlan(planId: UUID, request: OfferPlanRequest): ResponseEntity<PlanResponse>

    @Operation(
        summary = "[내부] 결정",
        description = "승인은 실행하려는 호출(tool·args·command·risk·target)이 계획과 같아야 APPROVED 가 됩니다 (다르면 PLAN_CALL_MISMATCH). " +
            "거절은 REJECTED 이고, 이미 거절한 계획에 거절이 다시 오면 그대로 200 입니다.",
    )
    @ApiErrorResponses(
        InvalidInternalTokenException::class,
        ActionPlanNotFoundException::class,
        PlanStateConflictException::class,
        PlanCallMismatchException::class,
    )
    fun decidePlan(planId: UUID, request: DecidePlanRequest): ResponseEntity<PlanResponse>

    @Operation(summary = "[내부] 실행 시작", description = "APPROVED → EXECUTING. kubectl 을 부르기 직전에 부릅니다.")
    @ApiErrorResponses(InvalidInternalTokenException::class, ActionPlanNotFoundException::class, PlanStateConflictException::class)
    fun startPlan(planId: UUID): ResponseEntity<PlanResponse>

    @Operation(
        summary = "[내부] 실행 결과 기록",
        description = "APPROVED·EXECUTING 에서 결과를 남깁니다. 성공이고 exitCode 가 0 이면 APPLIED, 아니면 FAILED(EXECUTION_FAILED) 입니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, ActionPlanNotFoundException::class, PlanStateConflictException::class)
    fun recordPlanResult(planId: UUID, request: RecordPlanResultRequest): ResponseEntity<PlanResponse>

    @Operation(summary = "[내부] 실패", description = "열린 계획(DRAFT·WAITING_APPROVAL·APPROVED·EXECUTING)을 사유와 함께 FAILED 로 닫습니다.")
    @ApiErrorResponses(InvalidInternalTokenException::class, ActionPlanNotFoundException::class, PlanStateConflictException::class)
    fun failPlan(planId: UUID, request: FailPlanRequest): ResponseEntity<PlanResponse>

    @Operation(summary = "[내부] 확인 불가", description = "결과 없이 APPROVED·EXECUTING 인 계획을 UNKNOWN 으로 닫습니다.")
    @ApiErrorResponses(InvalidInternalTokenException::class, ActionPlanNotFoundException::class, PlanStateConflictException::class)
    fun markPlanUnknown(planId: UUID): ResponseEntity<PlanResponse>
}
