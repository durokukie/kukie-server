package com.duro.kukie.team.presentation.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateTeamRequest(
    @field:NotBlank
    @field:Size(max = 50)
    // 팀 이름은 메일 제목·본문에 그대로 실린다. 줄바꿈이나 제어문자가 정상인 이름은 없으므로 입구에서 막는다.
    @field:Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "팀 이름에 줄바꿈이나 제어문자를 넣을 수 없습니다.")
    val name: String,
)
