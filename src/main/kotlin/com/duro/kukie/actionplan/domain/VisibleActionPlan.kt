package com.duro.kukie.actionplan.domain

import java.util.UUID

data class VisibleActionPlan(
    val plan: ActionPlan,
    val clusterId: UUID,
    val ownerId: UUID,
    /** 대화를 만든 사람의 표시 이름 — 화면은 UUID 대신 이걸 그린다 */
    val ownerName: String,
)
