package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.ExpiredTokenException
import com.duro.kukie.auth.exception.GitHubAccountTakenException
import com.duro.kukie.auth.exception.GitHubAlreadyLinkedException
import com.duro.kukie.auth.exception.GitHubLinkFailedException
import com.duro.kukie.auth.exception.InvalidCredentialsException
import com.duro.kukie.auth.exception.InvalidHandoffCodeException
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.auth.presentation.dto.request.ExchangeHandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.HandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.LinkGitHubAccountRequest
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.request.ResetPasswordRequest
import com.duro.kukie.auth.presentation.dto.request.SendPasswordResetCodeRequest
import com.duro.kukie.auth.presentation.dto.response.HandoffCodeResponse
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import com.duro.kukie.user.exception.UserNotFoundException
import com.duro.kukie.user.presentation.dto.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface AuthControllerDocs {

    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호를 통해 로그인하고 토큰을 발급받습니다. 토큰은 응답 본문과 httpOnly 쿠키(`__Host-kukie_access`, `__Host-kukie_refresh` — 로컬 http 에서는 접두사 없이)로 함께 내려갑니다.",
    )
    @ApiErrorResponses(InvalidCredentialsException::class)
    fun logIn(request: LogInRequest, response: HttpServletResponse): ResponseEntity<TokenResponse>

    @Operation(
        summary = "소셜 로그인",
        description = "OAuth 인가 코드로 로그인하고 토큰을 발급받습니다. 같은 이메일로 가입된 계정이 없으면 자동으로 가입한 뒤 로그인합니다. " +
            "토큰은 응답 본문과 httpOnly 쿠키로 함께 내려갑니다.",
    )
    @ApiErrorResponses(OAuthLogInFailedException::class)
    fun oAuthLogIn(provider: OAuthProvider, request: OAuthLogInRequest, response: HttpServletResponse): ResponseEntity<TokenResponse>

    @Operation(
        summary = "GitHub 계정 연결",
        description = "로그인한 회원에 GitHub 계정을 연결합니다. 호스팅 앱의 주인은 GitHub 계정이라, Google 이나 이메일로 가입한 회원은 " +
            "한 번 연결해야 자기 앱을 볼 수 있습니다. GitHub 로 로그인하는 회원은 로그인할 때 자동으로 연결됩니다. " +
            "GitHub OAuth 인가 코드를 로그인과 같은 방식으로 받아 넘기되 PKCE code verifier 가 필수입니다. " +
            "그 GitHub 계정이 다른 회원에 연결돼 있었다면 이 회원으로 옮깁니다. " +
            "이 회원에 이미 다른 GitHub 계정이 연결돼 있으면 409 이고, 연결을 끊은 뒤 다시 연결해야 합니다.",
    )
    @ApiErrorResponses(
        GitHubLinkFailedException::class,
        GitHubAlreadyLinkedException::class,
        GitHubAccountTakenException::class,
        UserNotFoundException::class,
    )
    fun linkGitHubAccount(userId: UUID, request: LinkGitHubAccountRequest): ResponseEntity<UserResponse>

    @Operation(
        summary = "GitHub 계정 연결 끊기",
        description = "회원에 연결된 GitHub 계정을 끊습니다. 그 계정의 호스팅 앱은 지워지지 않고, 이 회원에게 더 보이지 않을 뿐입니다. " +
            "연결된 계정이 없어도 204 입니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(UserNotFoundException::class)
    fun unlinkGitHubAccount(userId: UUID): ResponseEntity<Unit>

    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰을 이용해 액세스 토큰과 리프레시 토큰을 재발급 받습니다. 사용한 리프레시 토큰은 삭제됩니다. " +
            "리프레시 토큰은 본문에 없으면 `__Host-kukie_refresh` 쿠키에서 읽습니다(웹). 새 토큰도 본문과 쿠키로 함께 내려갑니다.",
    )
    @ApiErrorResponses(UnauthorizedException::class, InvalidTokenException::class, ExpiredTokenException::class)
    fun refresh(request: RefreshTokenRequest?, httpRequest: HttpServletRequest, response: HttpServletResponse): ResponseEntity<TokenResponse>

    @Operation(
        summary = "로그아웃",
        description = "지금 쓰는 로그인(세션)만 끊고 그 리프레시 토큰을 삭제합니다. 같은 계정의 다른 창·기기 로그인은 유지됩니다. 토큰 쿠키도 지웁니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    fun logOut(userId: UUID, httpRequest: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Unit>

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

    @Operation(
        summary = "비밀번호 재설정 코드 발송",
        description = "가입된 이메일이면 6자리 재설정 코드(3분 유효)를 메일로 보냅니다. 비밀번호가 없는 소셜 로그인 계정도 받아서 비밀번호를 처음 설정할 수 있습니다. " +
            "가입 여부를 드러내지 않도록 가입하지 않은 이메일에도 204 로 응답합니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    fun sendPasswordResetCode(request: SendPasswordResetCodeRequest): ResponseEntity<Unit>

    @Operation(
        summary = "비밀번호 재설정",
        description = "재설정 코드를 확인하고 새 비밀번호로 바꿉니다. 코드는 한 번 쓰면 지워집니다."
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(InvalidVerificationCodeException::class)
    fun resetPassword(request: ResetPasswordRequest): ResponseEntity<Unit>
}
