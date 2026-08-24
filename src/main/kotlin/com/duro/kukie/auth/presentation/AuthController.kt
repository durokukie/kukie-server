package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.application.LogInService
import com.duro.kukie.auth.application.LogOutService
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.AuthUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/auth")
class AuthController(
    private val logInService: LogInService,
    private val logOutService: LogOutService,
) {
    @PostMapping("/login")
    fun logIn(
        @RequestBody @Valid request: LogInRequest,
    ): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(logInService.logIn(request))
    }

    @DeleteMapping("/logout")
    fun logOut(
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        logOutService.logOut(userId)

        return ResponseEntity.noContent().build()
    }
}
