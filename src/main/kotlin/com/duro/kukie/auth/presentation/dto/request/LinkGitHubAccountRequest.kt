package com.duro.kukie.auth.presentation.dto.request

import com.duro.kukie.auth.application.port.`in`.LinkGitHubAccountCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class LinkGitHubAccountRequest(
    @Schema(description = "GitHub 로부터 발급받은 인가 코드")
    @field:NotBlank
    val code: String,

    @Schema(description = "인가 코드를 발급받을 때 사용한 리다이렉트 URI")
    @field:NotBlank
    val redirectUri: String,

    @Schema(description = "PKCE code verifier. 연결에서는 필수입니다 — 이 브라우저가 시작한 인가만 받기 위해서입니다.")
    @field:NotBlank
    val codeVerifier: String,
) {
    fun toCommand(userId: UUID) = LinkGitHubAccountCommand(
        userId = userId,
        code = code,
        redirectUri = redirectUri,
        codeVerifier = codeVerifier,
    )
}
