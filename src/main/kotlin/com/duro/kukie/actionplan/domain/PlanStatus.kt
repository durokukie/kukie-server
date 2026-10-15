package com.duro.kukie.actionplan.domain

enum class PlanStatus(val open: Boolean) {
    DRAFT(true),
    WAITING_APPROVAL(true),
    APPROVED(true),
    EXECUTING(true),
    APPLIED(false),
    EFFECT_VERIFIED(false),
    REJECTED(false),
    EXPIRED(false),
    STALE(false),
    FAILED(false),
    UNKNOWN(false),
    ;

    companion object {
        val OPEN: List<PlanStatus> = entries.filter { it.open }
    }
}
