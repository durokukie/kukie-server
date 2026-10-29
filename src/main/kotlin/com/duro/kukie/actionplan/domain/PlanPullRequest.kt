package com.duro.kukie.actionplan.domain

/** GitOps 길의 계획이 승인 전에 연 매니페스트 레포 PR. */
data class PlanPullRequest(
    val number: Long,
    val url: String,
)
