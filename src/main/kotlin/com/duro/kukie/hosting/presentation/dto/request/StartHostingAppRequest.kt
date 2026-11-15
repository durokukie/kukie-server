package com.duro.kukie.hosting.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class StartHostingAppRequest(
    @field:Schema(description = "배포할 레포. 목록의 installation.repositories 에 있는 fullName", example = "Recklehs/shop")
    @field:NotBlank
    val repository: String,
)
