package com.duro.kukie.user.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.user.exception.DuplicatedEmailException
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import com.duro.kukie.user.exception.UserNotFoundException
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import com.duro.kukie.user.presentation.dto.request.SendVerificationCodeRequest
import com.duro.kukie.user.presentation.dto.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface UserControllerDocs {

    @Operation(summary = "회원가입", description = "이메일을 이용해 회원가입합니다.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(DuplicatedEmailException::class, InvalidVerificationCodeException::class)
    fun createUser(request: CreateUserRequest): ResponseEntity<Unit>

    @Operation(summary = "회원가입 인증 코드 발송", description = "회원가입 시 필요한 인증 코드를 발송합니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(DuplicatedEmailException::class)
    fun sendVerificationCode(request: SendVerificationCodeRequest): ResponseEntity<Unit>

    @Operation(summary = "회원 정보 조회", description = "자신의 정보를 조회합니다.")
    @ApiErrorResponses(UserNotFoundException::class)
    fun getMe(userId: UUID): ResponseEntity<UserResponse>
}
