package com.duro.kukie.team.presentation.dto.request

import com.duro.kukie.team.application.port.`in`.CreateTeamCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateTeamRequest(
    @field:NotBlank
    @field:Size(max = 50)
    @field:Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "팀 이름에 줄바꿈이나 제어문자를 넣을 수 없습니다.")
    val name: String,
) {
    fun toCommand(userId: UUID) = CreateTeamCommand(
        userId = userId,
        name = name,
    )
}
