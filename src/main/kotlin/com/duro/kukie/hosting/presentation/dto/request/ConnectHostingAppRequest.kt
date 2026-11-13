package com.duro.kukie.hosting.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class ConnectHostingAppRequest(
    @field:Schema(description = "연결할 레포. 목록의 installation.repositories 에 있는 fullName", example = "Recklehs/shop-frontend")
    @field:NotBlank
    val repository: String,
)
