package com.duro.kukie.auth.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class ExchangeHandoffCodeRequest(
    @field:Schema(description = "kukie://auth?code=… 로 받은 1회용 코드")
    @field:NotBlank
    val code: String,
    @field:Schema(description = "코드를 받을 때 맡긴 검증값의 원본 (PKCE verifier)")
    @field:NotBlank
    val codeVerifier: String,
)
