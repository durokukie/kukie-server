package com.duro.kukie.team.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateTeamRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,
)
