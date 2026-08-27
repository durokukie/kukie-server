package com.duro.kukie.user

import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.exception.UserErrorCode
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import com.duro.kukie.user.presentation.dto.request.SendVerificationCodeRequest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class UserIntegrationTest : IntegrationTest() {

    private val email = UserFixture.DEFAULT_EMAIL

    @Test
    fun `정상적으로 인증 코드를 전송한다`() {
        val request = SendVerificationCodeRequest(email)

        mockMvc.post("/users/verification-code") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `이미 가입된 이메일로는 인증 코드를 발송할 수 없다`() {
        val user = userRepository.save(UserFixture.user())

        sendVerificationCode(user.email).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(UserErrorCode.DUPLICATED_EMAIL.code) }
        }
    }

    @Test
    fun `인증을 성공하고 정상적으로 회원 가입을 한다`() {
        sendVerificationCode().andExpect { status { isNoContent() } }
        val code = fakeVerificationCodeSender.lastCodeFor(email)
        val request = CreateUserRequest(
            name = UserFixture.DEFAULT_NAME,
            email = email,
            password = UserFixture.DEFAULT_PASSWORD,
            verificationCode = code,
        )

        mockMvc.post("/users") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `잘못된 인증 코드로는 회원가입할 수 없다`() {
        sendVerificationCode().andExpect { status { isNoContent() } }
        val actualCode = fakeVerificationCodeSender.lastCodeFor(email)
        val wrongCode = if (actualCode != "000000") "000000" else "999999"

        signUp(code = wrongCode).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(UserErrorCode.INVALID_VERIFICATION_CODE.code) }
        }
    }

    @Test
    fun `로그인 한 유저가 정상적으로 정보를 조회한다`() {
        val user = loggedInUser()

        mockMvc.get("/users/me") {
            authorization(user.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(user.user.id.toString()) }
            jsonPath("$.name") { value(user.user.name) }
            jsonPath("$.email") { value(user.user.email) }
        }
    }

    private fun sendVerificationCode(email: String = UserFixture.DEFAULT_EMAIL): ResultActionsDsl {
        val request = SendVerificationCodeRequest(email)

        return mockMvc.post("/users/verification-code") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }
    }

    private fun signUp(
        email: String = UserFixture.DEFAULT_EMAIL,
        code: String = fakeVerificationCodeSender.lastCodeFor(email),
    ): ResultActionsDsl {
        val request = CreateUserRequest(
                name = UserFixture.DEFAULT_NAME,
                email = email,
                password = UserFixture.DEFAULT_PASSWORD,
                verificationCode = code,
        )

        return mockMvc.post("/users") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }
    }
}
