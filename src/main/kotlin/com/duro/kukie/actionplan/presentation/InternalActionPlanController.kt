package com.duro.kukie.actionplan.presentation

import com.duro.kukie.actionplan.application.CreatePlanService
import com.duro.kukie.actionplan.application.DecidePlanService
import com.duro.kukie.actionplan.application.FailPlanService
import com.duro.kukie.actionplan.application.GetPlanService
import com.duro.kukie.actionplan.application.GetRunPlansService
import com.duro.kukie.actionplan.application.MarkPlanUnknownService
import com.duro.kukie.actionplan.application.OfferPlanService
import com.duro.kukie.actionplan.application.RecordDryRunService
import com.duro.kukie.actionplan.application.RecordPlanResultService
import com.duro.kukie.actionplan.application.StartPlanService
import com.duro.kukie.actionplan.presentation.dto.request.CreatePlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.DecidePlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.FailPlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.OfferPlanRequest
import com.duro.kukie.actionplan.presentation.dto.request.RecordDryRunRequest
import com.duro.kukie.actionplan.presentation.dto.request.RecordPlanResultRequest
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.global.security.InternalApi
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@InternalApi
@RestController
@RequestMapping("/internal")
class InternalActionPlanController(
    private val createPlanService: CreatePlanService,
    private val getRunPlansService: GetRunPlansService,
    private val getPlanService: GetPlanService,
    private val recordDryRunService: RecordDryRunService,
    private val offerPlanService: OfferPlanService,
    private val decidePlanService: DecidePlanService,
    private val startPlanService: StartPlanService,
    private val recordPlanResultService: RecordPlanResultService,
    private val failPlanService: FailPlanService,
    private val markPlanUnknownService: MarkPlanUnknownService,
) : InternalActionPlanControllerDocs {

    @PostMapping("/runs/{runId}/plans")
    override fun createPlan(
        @PathVariable runId: UUID,
        @RequestBody @Valid request: CreatePlanRequest,
    ): ResponseEntity<PlanResponse> {
        val result = createPlanService(request.toCommand(runId))
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK

        return ResponseEntity.status(status).body(result.plan)
    }

    @GetMapping("/runs/{runId}/plans")
    override fun getRunPlans(
        @PathVariable runId: UUID,
        @RequestParam(required = false) toolCallId: String?,
    ): ResponseEntity<List<PlanResponse>> {
        return ResponseEntity.ok(getRunPlansService(runId, toolCallId))
    }

    @GetMapping("/plans/{planId}")
    override fun getPlan(
        @PathVariable planId: UUID,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(getPlanService(planId))
    }

    @PostMapping("/plans/{planId}/dry-run")
    override fun recordDryRun(
        @PathVariable planId: UUID,
        @RequestBody @Valid request: RecordDryRunRequest,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(recordDryRunService(request.toCommand(planId)))
    }

    @PostMapping("/plans/{planId}/offer")
    override fun offerPlan(
        @PathVariable planId: UUID,
        @RequestBody @Valid request: OfferPlanRequest,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(offerPlanService(request.toCommand(planId)))
    }

    @PostMapping("/plans/{planId}/decision")
    override fun decidePlan(
        @PathVariable planId: UUID,
        @RequestBody @Valid request: DecidePlanRequest,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(decidePlanService(request.toCommand(planId)))
    }

    @PostMapping("/plans/{planId}/start")
    override fun startPlan(
        @PathVariable planId: UUID,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(startPlanService(planId))
    }

    @PostMapping("/plans/{planId}/result")
    override fun recordPlanResult(
        @PathVariable planId: UUID,
        @RequestBody @Valid request: RecordPlanResultRequest,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(recordPlanResultService(request.toCommand(planId)))
    }

    @PostMapping("/plans/{planId}/fail")
    override fun failPlan(
        @PathVariable planId: UUID,
        @RequestBody @Valid request: FailPlanRequest,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(failPlanService(request.toCommand(planId)))
    }

    @PostMapping("/plans/{planId}/unknown")
    override fun markPlanUnknown(
        @PathVariable planId: UUID,
    ): ResponseEntity<PlanResponse> {
        return ResponseEntity.ok(markPlanUnknownService(planId))
    }
}
