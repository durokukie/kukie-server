package com.duro.kukie.auth.presentation.dto.request

import com.duro.kukie.auth.application.port.`in`.IssueHandoffCodeCommand
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import jakarta.validation.constraints.NotBlank

data class HandoffCodeRequest(
    @field:Schema(description = "앱이 만든 PKCE 검증값 — base64url(sha256(verifier)). 앱만 아는 verifier 의 해시라 공개돼도 된다")
    @field:NotBlank
    val codeChallenge: String,
) {
    fun toCommand(userId: UUID) = IssueHandoffCodeCommand(
        userId = userId,
        codeChallenge = codeChallenge,
    )
}
