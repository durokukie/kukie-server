package com.duro.kukie.actionplan.domain

enum class PlanFailureReason {
    PERMISSION_DENIED,
    CONCURRENT_MODIFICATION,
    DRY_RUN_FAILED,
    EXECUTION_FAILED,
    VERIFICATION_FAILED,
    VERIFICATION_TIMEOUT,
}
