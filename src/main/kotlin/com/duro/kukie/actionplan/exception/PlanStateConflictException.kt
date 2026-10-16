package com.duro.kukie.actionplan.exception

import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.global.exception.BusinessException

class PlanStateConflictException(
    status: PlanStatus? = null,
) : BusinessException(
    ActionPlanErrorCode.PLAN_STATE_CONFLICT,
    status?.let { "지금 상태($it)에서는 할 수 없는 동작입니다." } ?: ActionPlanErrorCode.PLAN_STATE_CONFLICT.message,
)
