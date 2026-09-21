package com.duro.kukie.auth.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class HandoffCodeRequest(
    @field:Schema(description = "앱이 만든 PKCE 검증값 — base64url(sha256(verifier)). 앱만 아는 verifier 의 해시라 공개돼도 된다")
    @field:NotBlank
    val codeChallenge: String,
)
