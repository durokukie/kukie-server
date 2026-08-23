package com.duro.kukie.user.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.user.application.CreateUserService
import com.duro.kukie.user.application.GetUserService
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import com.duro.kukie.user.presentation.dto.response.UserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val createUserService: CreateUserService,
    private val getUserService: GetUserService,
) {
    @PostMapping
    fun createUser(
        @RequestBody @Valid request: CreateUserRequest,
    ): ResponseEntity<Unit> {
        createUserService.createUser(request)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @Authenticated
    @GetMapping("/me")
    fun getMe(
        @AuthUser userId: UUID,
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(getUserService.getUser(userId))
    }
}
