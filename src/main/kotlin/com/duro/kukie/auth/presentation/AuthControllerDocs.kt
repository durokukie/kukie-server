package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.ExpiredTokenException
import com.duro.kukie.auth.exception.InvalidCredentialsException
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.exception.OAuthClientNotConfiguredException
import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface AuthControllerDocs {

    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호를 통해 로그인하고 토큰을 발급받습니다. 토큰은 응답 본문과 httpOnly 쿠키(`kukie_access`, `kukie_refresh`)로 함께 내려갑니다.",
    )
    @ApiErrorResponses(InvalidCredentialsException::class)
    fun logIn(request: LogInRequest, response: HttpServletResponse): ResponseEntity<TokenResponse>

    @Operation(
        summary = "소셜 로그인",
        description = "OAuth 인가 코드로 로그인하고 토큰을 발급받습니다. 같은 이메일로 가입된 계정이 없으면 자동으로 가입한 뒤 로그인합니다. " +
            "redirectUri 가 `http://127.0.0.1:...`(앱 루프백)이면 데스크톱 클라이언트, 그 밖이면 웹 클라이언트로 코드를 교환합니다. " +
            "토큰은 응답 본문과 httpOnly 쿠키로 함께 내려갑니다.",
    )
    @ApiErrorResponses(OAuthLogInFailedException::class, OAuthClientNotConfiguredException::class)
    fun oAuthLogIn(provider: OAuthProvider, request: OAuthLogInRequest, response: HttpServletResponse): ResponseEntity<TokenResponse>

    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰을 이용해 액세스 토큰과 리프레시 토큰을 재발급 받습니다. 사용한 리프레시 토큰은 삭제됩니다. " +
            "리프레시 토큰은 본문에 없으면 `kukie_refresh` 쿠키에서 읽습니다(웹). 새 토큰도 본문과 쿠키로 함께 내려갑니다.",
    )
    @ApiErrorResponses(UnauthorizedException::class, InvalidTokenException::class, ExpiredTokenException::class)
    fun refresh(request: RefreshTokenRequest?, httpRequest: HttpServletRequest, response: HttpServletResponse): ResponseEntity<TokenResponse>

    @Operation(summary = "로그아웃", description = "로그아웃하고 리프레시 토큰을 삭제합니다. 토큰 쿠키도 지웁니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    fun logOut(userId: UUID, response: HttpServletResponse): ResponseEntity<Unit>
}
