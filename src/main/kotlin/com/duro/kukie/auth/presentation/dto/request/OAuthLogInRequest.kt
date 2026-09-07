package com.duro.kukie.auth.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class OAuthLogInRequest(
    @Schema(description = "OAuth 제공자로부터 발급받은 인가 코드")
    @field:NotBlank
    val code: String,

    @Schema(description = "인가 코드를 발급받을 때 사용한 리다이렉트 URI")
    @field:NotBlank
    val redirectUri: String,

    @Schema(description = "PKCE code verifier (인가 요청에 code challenge를 사용한 경우 필수)")
    val codeVerifier: String? = null,
)
