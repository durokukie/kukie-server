package com.duro.kukie.serving.presentation.dto.request

import com.duro.kukie.serving.application.port.`in`.IssueConnectorCredentialCommand
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class IssueConnectorCredentialRequest(
    @field:NotEmpty @field:Size(max = 100)
    val models: List<@Size(min = 1, max = 200) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]*") String>,
) {
    @get:AssertTrue(message = "모델 이름이 올바르지 않습니다.")
    val validModels: Boolean get() = models.all { it.length in 1..200 && it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")) }

    fun toCommand(clusterId: UUID, userId: UUID) = IssueConnectorCredentialCommand(clusterId, userId, models)
}

