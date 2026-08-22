package com.duro.kukie.auth.presentation

import com.duro.kukie.auth.application.LogInService
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val logInService: LogInService,
) {
    @PostMapping("/login")
    fun logIn(
        @RequestBody @Valid request: LogInRequest,
    ): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(logInService.logIn(request))
    }
}
