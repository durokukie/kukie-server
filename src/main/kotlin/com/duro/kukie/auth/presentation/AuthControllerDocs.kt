package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.exception.ExpiredTokenException
import com.duro.kukie.auth.exception.InvalidCredentialsException
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface AuthControllerDocs {

    @Operation(summary = "로그인", description = "이메일과 비밀번호를 통해 로그인하고 토큰을 발급받습니다.")
    @ApiErrorResponses(InvalidCredentialsException::class)
    fun logIn(request: LogInRequest): ResponseEntity<TokenResponse>

    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰을 이용해 액세스 토큰과 리프레시 토큰을 재발급 받습니다. 사용한 리프레시 토큰은 삭제됩니다.",
    )
    @ApiErrorResponses(InvalidTokenException::class, ExpiredTokenException::class)
    fun refresh(request: RefreshTokenRequest): ResponseEntity<TokenResponse>

    @Operation(summary = "로그아웃", description = "로그아웃하고 리프레시 토큰을 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    fun logOut(userId: UUID): ResponseEntity<Unit>
}
