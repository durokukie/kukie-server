package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.application.LogInService
import com.duro.kukie.auth.application.LogOutService
import com.duro.kukie.auth.application.OAuthLogInService
import com.duro.kukie.auth.application.RefreshTokenService
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
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

/**
 * 토큰은 두 길로 나간다 — JSON 본문(앱이 쓴다)과 httpOnly 쿠키(웹이 쓴다). 요청이 어느 쪽인지 가르지 않고
 * 항상 둘 다 준다: 앱은 쿠키를 무시하므로 해가 없고, 분기가 없어 경로가 하나다 (`AuthCookies`).
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val logInService: LogInService,
    private val oAuthLogInService: OAuthLogInService,
    private val logOutService: LogOutService,
    private val refreshTokenService: RefreshTokenService,
    private val authCookies: AuthCookies,
) : AuthControllerDocs {

    @PostMapping("/login")
    override fun logIn(
        @RequestBody @Valid request: LogInRequest,
        response: HttpServletResponse,
    ): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(logInService(request).alsoIssueCookies(response))
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

    private fun TokenResponse.alsoIssueCookies(response: HttpServletResponse): TokenResponse {
        authCookies.issue(response, accessToken, refreshToken)
        return this
    }
}
