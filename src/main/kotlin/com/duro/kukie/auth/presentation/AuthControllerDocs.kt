package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.ExpiredTokenException
import com.duro.kukie.auth.exception.InvalidCredentialsException
import com.duro.kukie.auth.exception.InvalidHandoffCodeException
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.exception.OAuthClientNotConfiguredException
import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.auth.presentation.dto.request.ExchangeHandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.HandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.response.HandoffCodeResponse
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

    @Operation(
        summary = "앱 넘겨주기 코드 발급",
        description = "시스템 브라우저에서 로그인을 마친 웹 페이지가 부릅니다. 로그인된 사용자의 1회용 코드(60초)를 만들어 돌려주고, " +
            "페이지는 이를 `kukie://auth?code=…` 로 데스크톱 앱에 건넵니다. `codeChallenge` 는 앱이 만든 PKCE 검증값(base64url(sha256(verifier)))입니다.",
    )
    @ApiErrorResponses(UnauthorizedException::class, InvalidTokenException::class, ExpiredTokenException::class)
    fun issueHandoffCode(userId: UUID, request: HandoffCodeRequest): ResponseEntity<HandoffCodeResponse>

    @Operation(
        summary = "앱 넘겨주기 코드 교환",
        description = "데스크톱 앱이 딥링크로 받은 1회용 코드와 PKCE 원본(`codeVerifier`)을 내면 토큰을 발급합니다. 코드는 한 번 쓰면 지워지고, " +
            "원본의 해시가 발급 때 맡긴 검증값과 다르면 거절합니다. 토큰은 본문과 httpOnly 쿠키로 함께 내려갑니다.",
    )
    @ApiErrorResponses(InvalidHandoffCodeException::class)
    fun exchangeHandoffCode(request: ExchangeHandoffCodeRequest, response: HttpServletResponse): ResponseEntity<TokenResponse>
}
