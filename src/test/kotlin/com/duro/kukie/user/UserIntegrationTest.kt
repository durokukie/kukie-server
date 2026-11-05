package com.duro.kukie.user

import com.duro.kukie.auth.exception.AuthErrorCode
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.support.FakeVerificationCodeSender
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.exception.UserErrorCode
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.user.presentation.dto.request.ChangePasswordRequest
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import com.duro.kukie.user.presentation.dto.request.SendVerificationCodeRequest
import com.duro.kukie.user.presentation.dto.request.UpdateUserRequest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class UserIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var fakeVerificationCodeSender: FakeVerificationCodeSender

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

        sendVerificationCode(user.email.value).andExpect {
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
            jsonPath("$.email") { value(user.user.email.value) }
            jsonPath("$.hasPassword") { value(true) }
        }
    }

    @Test
    fun `로그인 한 유저가 이름을 변경한다`() {
        // given
        val user = loggedInUser()

        // when
        mockMvc.put("/users/me") {
            authorization(user.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateUserRequest("새 이름").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(user.user.id.toString()) }
            jsonPath("$.name") { value("새 이름") }
        }

        // then
        mockMvc.get("/users/me") {
            authorization(user.accessToken)
        }.andExpect {
            jsonPath("$.name") { value("새 이름") }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    fun `빈 이름이나 50자를 넘는 이름으로는 변경할 수 없다`(name: String) {
        // given
        val user = loggedInUser()

        // when & then
        mockMvc.put("/users/me") {
            authorization(user.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = UpdateUserRequest(name).toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
    }

    @Test
    fun `로그인하지 않으면 이름을 변경할 수 없다`() {
        // when & then
        mockMvc.put("/users/me") {
            contentType = MediaType.APPLICATION_JSON
            content = UpdateUserRequest("새 이름").toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.UNAUTHORIZED.code) }
        }
    }

    @Test
    fun `로그인 한 유저가 현재 비밀번호를 확인하고 비밀번호를 바꾼다`() {
        // given
        val user = loggedInUser()

        // when
        changePassword(user.accessToken, UserFixture.DEFAULT_PASSWORD, "new-password1")
            .andExpect { status { isNoContent() } }

        // then
        logIn("new-password1").andExpect { status { isOk() } }
        logIn(UserFixture.DEFAULT_PASSWORD).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `현재 비밀번호가 틀리면 400 으로 거절하고 로그인은 유지된다`() {
        // given
        val user = loggedInUser()

        // when & then
        changePassword(user.accessToken, "wrong-password", "new-password1").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(UserErrorCode.INVALID_CURRENT_PASSWORD.code) }
        }
        mockMvc.get("/users/me") {
            authorization(user.accessToken)
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `로그인하지 않으면 비밀번호를 변경할 수 없다`() {
        // when & then
        mockMvc.put("/users/me/password") {
            contentType = MediaType.APPLICATION_JSON
            content = ChangePasswordRequest(UserFixture.DEFAULT_PASSWORD, "new-password1").toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.UNAUTHORIZED.code) }
        }
    }

    private fun changePassword(accessToken: String, current: String, new: String): ResultActionsDsl =
        mockMvc.put("/users/me/password") {
            authorization(accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = ChangePasswordRequest(current, new).toJson()
        }

    private fun logIn(password: String): ResultActionsDsl =
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = LogInRequest(email, password).toJson()
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

    companion object {
        @JvmStatic
        fun invalidNames() = listOf("", "   ", "가".repeat(51))
    }
}
