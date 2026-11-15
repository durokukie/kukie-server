package com.duro.kukie.hosting.presentation.dto.request

import com.duro.kukie.hosting.application.port.`in`.CreateHostingAgentDeployCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.util.UUID

/** 폼(application/x-www-form-urlencoded)으로 받는다. */
data class CreateHostingAgentDeployRequest(
    @field:Schema(description = "앱 이름. 영소문자로 시작, 영소문자·숫자·하이픈, 30자 이하", example = "my-shop")
    @field:NotBlank
    val name: String?,
    @field:Schema(description = "올릴 .tar.gz 의 크기(바이트). 50MB 까지", example = "1834512")
    @field:NotNull
    @field:Positive
    val bytes: Long?,
) {
    fun toCommand(userId: UUID) = CreateHostingAgentDeployCommand(userId, name!!, bytes!!)
}
