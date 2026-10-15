package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.DecidePlanCommand
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import java.util.UUID

data class DecidePlanRequest(
    val approved: Boolean,

    val userId: UUID,

    @field:Valid
    val call: PlanCallRequest? = null,
) {
    @get:JsonIgnore
    @get:AssertTrue
    val callPresentWhenApproved: Boolean
        get() = !approved || call != null

    fun toCommand(planId: UUID) = DecidePlanCommand(
        planId = planId,
        approved = approved,
        userId = userId,
        call = call?.toPlanCall(),
    )
}
