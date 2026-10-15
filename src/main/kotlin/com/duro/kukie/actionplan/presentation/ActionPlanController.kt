package com.duro.kukie.actionplan.presentation

import com.duro.kukie.actionplan.application.GetActionPlanService
import com.duro.kukie.actionplan.application.GetActionPlansService
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.actionplan.presentation.dto.response.ActionPlanDetailResponse
import com.duro.kukie.actionplan.presentation.dto.response.ActionPlanSummaryResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/action-plans")
class ActionPlanController(
    private val getActionPlansService: GetActionPlansService,
    private val getActionPlanService: GetActionPlanService,
) : ActionPlanControllerDocs {

    @GetMapping
    override fun getActionPlans(
        @AuthUser userId: UUID,
        @RequestParam(required = false) clusterId: UUID?,
        @RequestParam(required = false) status: PlanStatus?,
    ): ResponseEntity<List<ActionPlanSummaryResponse>> {
        return ResponseEntity.ok(getActionPlansService(userId, clusterId, status))
    }

    @GetMapping("/{planId}")
    override fun getActionPlan(
        @PathVariable planId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<ActionPlanDetailResponse> {
        return ResponseEntity.ok(getActionPlanService(planId, userId))
    }
}
