package com.duro.kukie.report.application

import com.duro.kukie.actionplan.domain.PlanStatus

/** 대리 쌍을 가르지 않고 자른다. */
internal fun String.clipped(limit: Int): String {
    if (length <= limit) return this
    val end = if (this[limit - 1].isHighSurrogate()) limit - 1 else limit

    return take(end) + "…"
}

/** 보고서와 AI 자료에 적는 변경의 결과. */
internal fun PlanStatus.resultLabel(): String = when (this) {
    PlanStatus.APPLIED, PlanStatus.EFFECT_VERIFIED -> "적용"
    PlanStatus.FAILED -> "실패"
    PlanStatus.REJECTED -> "거절"
    PlanStatus.WAITING_APPROVAL -> "승인 대기"
    PlanStatus.DRAFT, PlanStatus.APPROVED, PlanStatus.EXECUTING -> "진행 중"
    PlanStatus.EXPIRED -> "만료"
    PlanStatus.STALE -> "무효"
    PlanStatus.UNKNOWN -> "확인 필요"
}
