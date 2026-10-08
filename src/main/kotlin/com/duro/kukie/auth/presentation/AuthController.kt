package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.application.ExchangeHandoffCodeService
import com.duro.kukie.auth.application.IssueHandoffCodeService
import com.duro.kukie.auth.application.LogInService
import com.duro.kukie.auth.application.LogOutService
import com.duro.kukie.auth.application.OAuthLogInService
import com.duro.kukie.auth.application.RefreshTokenService
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.auth.presentation.dto.request.ExchangeHandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.HandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.response.HandoffCodeResponse
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.AuthCookies
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 토큰은 요청 종류와 상관없이 JSON 본문과 httpOnly 쿠키로 함께 준다 (`AuthCookies`). */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val logInService: LogInService,
    private val oAuthLogInService: OAuthLogInService,
    private val logOutService: LogOutService,
    private val refreshTokenService: RefreshTokenService,
    private val issueHandoffCodeService: IssueHandoffCodeService,
    private val exchangeHandoffCodeService: ExchangeHandoffCodeService,
    private val authCookies: AuthCookies,
) : AuthControllerDocs {

    @PostMapping("/login")
    override fun logIn(
        @RequestBody @Valid request: LogInRequest,
        response: HttpServletResponse,
    ): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(logInService(request.toCommand()).alsoIssueCookies(response))
    }

    @PostMapping("/oauth/{provider}")
    override fun oAuthLogIn(
        @PathVariable provider: OAuthProvider,
        @RequestBody @Valid request: OAuthLogInRequest,
        response: HttpServletResponse,
    ): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(oAuthLogInService(request.toCommand(provider)).alsoIssueCookies(response))
    }

    /** 리프레시 토큰은 본문(앱)이 먼저, 없으면 쿠키(웹). 둘 다 없으면 인증 없음. */
    @PostMapping("/refresh")
    override fun refresh(
        @RequestBody(required = false) @Valid request: RefreshTokenRequest?,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<TokenResponse> {
        val refreshToken = request?.refreshToken
            ?: authCookies.refreshToken(httpRequest)
            ?: throw UnauthorizedException()

        return ResponseEntity.ok(refreshTokenService(RefreshTokenRequest(refreshToken)).alsoIssueCookies(response))
    }

    @Authenticated
    @DeleteMapping("/logout")
    override fun logOut(
        @AuthUser userId: UUID,
        response: HttpServletResponse,
    ): ResponseEntity<Unit> {
        logOutService(userId)
        authCookies.clear(response)

        return ResponseEntity.noContent().build()
    }

    /** 앱 넘겨주기 코드 발급. 시스템 브라우저에서 로그인한 웹 페이지가 부른다. */
    @Authenticated
    @PostMapping("/handoff")
    override fun issueHandoffCode(
        @AuthUser userId: UUID,
        @RequestBody @Valid request: HandoffCodeRequest,
    ): ResponseEntity<HandoffCodeResponse> {
        return ResponseEntity.ok(issueHandoffCodeService(request.toCommand(userId)))
    }

    /** 앱 넘겨주기 코드 + PKCE 원본 → 토큰. */
    @PostMapping("/exchange")
    override fun exchangeHandoffCode(
        @RequestBody @Valid request: ExchangeHandoffCodeRequest,
        response: HttpServletResponse,
    ): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(exchangeHandoffCodeService(request).alsoIssueCookies(response))
    }

    private fun TokenResponse.alsoIssueCookies(response: HttpServletResponse): TokenResponse {
        authCookies.issue(response, accessToken, refreshToken)
        return this
    }
}
