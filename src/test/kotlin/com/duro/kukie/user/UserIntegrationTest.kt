package com.duro.kukie.user

import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.exception.UserErrorCode
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import com.duro.kukie.user.presentation.dto.request.SendVerificationCodeRequest
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class UserIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @Test
    fun `인증 코드 발송부터 회원가입, 내 정보 조회까지 성공한다`() {
        sendVerificationCode().andExpect { status { isNoContent() } }
        signUp().andExpect { status { isCreated() } }

        val accessToken = logInAccessToken()

        mockMvc.get("/users/me") {
            authorization(accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(UserFixture.DEFAULT_EMAIL) }
            jsonPath("$.name") { value(UserFixture.DEFAULT_NAME) }
        }
    }

    @Test
    fun `잘못된 인증 코드로는 회원가입할 수 없다`() {
        val email = UserFixture.DEFAULT_EMAIL
        verificationCodeRepository.save(email, "123456")

        signUp(email = email, code = "000000").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(UserErrorCode.INVALID_VERIFICATION_CODE.code) }
        }
    }

    @Test
    fun `이미 가입된 이메일로는 인증 코드를 발송할 수 없다`() {
        val user = userRepository.save(UserFixture.user())

        sendVerificationCode(user.email).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(UserErrorCode.DUPLICATED_EMAIL.code) }
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

    private fun logInAccessToken(email: String = UserFixture.DEFAULT_EMAIL): String {
        val response = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = LogInRequest(email, UserFixture.DEFAULT_PASSWORD).toJson()
        }.andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        return JsonPath.read(response, "$.accessToken")
    }
}
