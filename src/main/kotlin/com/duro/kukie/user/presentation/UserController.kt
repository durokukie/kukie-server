package com.duro.kukie.user.presentation

import com.duro.kukie.user.application.CreateUserService
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
    private val createUserService: CreateUserService,
) {
    @PostMapping
    fun createUser(
        @RequestBody @Valid request: CreateUserRequest,
    ): ResponseEntity<Unit> {
        createUserService.createUser(request)

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}