package com.duro.kukie.user.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.user.exception.DuplicatedEmailException
import com.duro.kukie.user.exception.InvalidCurrentPasswordException
import com.duro.kukie.user.exception.LastAdminOfTeamException
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import com.duro.kukie.user.exception.PasswordNotSetException
import com.duro.kukie.user.exception.SamePasswordException
import com.duro.kukie.user.exception.UserNotFoundException
import com.duro.kukie.user.presentation.dto.request.ChangePasswordRequest
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import com.duro.kukie.user.presentation.dto.request.SendVerificationCodeRequest
import com.duro.kukie.user.presentation.dto.request.UpdateUserRequest
import com.duro.kukie.user.presentation.dto.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletResponse
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

    @Operation(
        summary = "회원 정보 조회",
        description = "자신의 정보를 조회합니다. `hasPassword` 가 false 면 소셜 로그인으로만 가입해 비밀번호가 없는 계정입니다.",
    )
    @ApiErrorResponses(UserNotFoundException::class)
    fun getMe(userId: UUID): ResponseEntity<UserResponse>

    @Operation(summary = "회원 정보 수정", description = "자신의 정보를 변경합니다.")
    @ApiErrorResponses(UserNotFoundException::class)
    fun updateUser(userId: UUID, request: UpdateUserRequest): ResponseEntity<UserResponse>

    @Operation(
        summary = "비밀번호 변경",
        description = "현재 비밀번호를 확인하고 새 비밀번호로 바꿉니다. 비밀번호가 없는 계정은 비밀번호 재설정(이메일 인증)으로 설정합니다. " +
            "현재 비밀번호가 틀려도 로그인이 풀리지 않도록 400 으로 응답합니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        PasswordNotSetException::class,
        InvalidCurrentPasswordException::class,
        SamePasswordException::class,
        UserNotFoundException::class,
    )
    fun changePassword(userId: UUID, request: ChangePasswordRequest): ResponseEntity<Unit>

    @Operation(
        summary = "회원 탈퇴",
        description = "이름·이메일·비밀번호·GitHub 연결을 지우고(행은 익명화해 남긴다) 로그인을 모두 끊는다. 혼자인 팀은 같이 삭제하고, " +
            "개인 대화·알림을 지우고 서빙 접근을 취소한다. 팀 공유 대화와 등록한 클러스터는 남는다. " +
            "다른 팀원이 있는 팀의 유일한 관리자면 409 로 거절한다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(LastAdminOfTeamException::class, UserNotFoundException::class)
    fun deleteMe(userId: UUID, response: HttpServletResponse): ResponseEntity<Unit>
}
