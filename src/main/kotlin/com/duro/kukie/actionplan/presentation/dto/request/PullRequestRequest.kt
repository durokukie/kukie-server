package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.domain.PlanPullRequest
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class PullRequestRequest(
    @field:Positive
    val number: Long,
    @field:NotBlank
    val url: String,
) {
    fun toDomain() = PlanPullRequest(number = number, url = url.trim())
}
