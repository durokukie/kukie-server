package com.duro.kukie.auth.presentation.dto.request

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh Token은 필수입니다.")
    val refreshToken: String,
)
