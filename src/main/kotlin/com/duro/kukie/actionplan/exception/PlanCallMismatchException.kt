package com.duro.kukie.actionplan.exception

import com.duro.kukie.global.exception.BusinessException

class PlanCallMismatchException(
    fields: List<String> = emptyList(),
) : BusinessException(
    ActionPlanErrorCode.PLAN_CALL_MISMATCH,
    if (fields.isEmpty()) ActionPlanErrorCode.PLAN_CALL_MISMATCH.message else "승인하려는 호출이 계획과 다릅니다: ${fields.joinToString(", ")}",
)
