package com.duro.kukie.user.presentation.dto.request

import com.duro.kukie.user.application.port.`in`.UpdateUserCommand
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateUserRequest(
    val name: String,
) {
    /** @NotBlank 는 no-break space 를 공백으로 보지 않으므로 Kotlin trim() 결과를 검사한다. */
    @get:JsonIgnore
    @get:NotBlank
    @get:Size(max = 50)
    @get:Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "이름에 줄바꿈이나 제어문자를 넣을 수 없습니다.")
    val trimmedName: String
        get() = name.trim()

    fun toCommand(userId: UUID) = UpdateUserCommand(
        userId = userId,
        name = trimmedName,
    )
}
