package com.duro.kukie.auth

import com.duro.kukie.auth.exception.AuthErrorCode
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.support.FakeOAuthClient
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class AuthIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var fakeOAuthClient: FakeOAuthClient

    @Test
    fun `정상적으로 로그인한다`() {
        val user = userRepository.save(UserFixture.user())
        val request = LogInRequest(user.email, UserFixture.DEFAULT_PASSWORD)

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
        }
    }

    @Test
    fun `이메일이 존재하지 않으면 로그인할 수 없다`() {
        val request = LogInRequest(UserFixture.DEFAULT_EMAIL, UserFixture.DEFAULT_PASSWORD)

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_CREDENTIALS.code) }
        }
    }

    @Test
    fun `비밀번호가 일치하지 않으면 로그인할 수 없다`() {
        val user = userRepository.save(UserFixture.user())
        val request = LogInRequest(user.email, "wrong-password")

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_CREDENTIALS.code) }
        }
    }

    @Test
    fun `소셜 로그인 시 가입되지 않은 이메일이면 자동으로 가입하고 토큰을 발급한다`() {
        val request = OAuthLogInRequest("code", "http://127.0.0.1:3000/callback")

        mockMvc.post("/auth/oauth/github") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
        }

        val user = userRepository.findByEmail(FakeOAuthClient.DEFAULT_PROFILE.email).shouldNotBeNull()
        user.name shouldBe FakeOAuthClient.DEFAULT_PROFILE.name
        user.password shouldBe null
    }

    @Test
    fun `소셜 로그인 시 이미 가입된 이메일이면 기존 계정으로 로그인한다`() {
        val user = userRepository.save(UserFixture.user(email = FakeOAuthClient.DEFAULT_PROFILE.email))
        val request = OAuthLogInRequest("code", "http://127.0.0.1:3000/callback")

        mockMvc.post("/auth/oauth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
        }

        userRepository.count() shouldBe 1
        userRepository.findByEmail(user.email).shouldNotBeNull().id shouldBe user.id
    }

    @Test
    fun `지원하지 않는 소셜 로그인 제공자면 예외가 발생한다`() {
        val request = OAuthLogInRequest("code", "http://127.0.0.1:3000/callback")

        mockMvc.post("/auth/oauth/naver") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
    }

    @Test
    fun `소셜 로그인에 실패한다`() {
        fakeOAuthClient.shouldFail = true
        val request = OAuthLogInRequest("code", "http://127.0.0.1:3000/callback")

        mockMvc.post("/auth/oauth/github") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.OAUTH_LOGIN_FAILED.code) }
        }
    }

    @Test
    fun `토큰 없이 인증이 필요한 API를 호출하면 예외가 발생한다`() {
        mockMvc.get("/users/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.UNAUTHORIZED.code) }
        }
    }

    @Test
    fun `리프레시 토큰은 재발급 시 교체되고 이전 토큰은 더 이상 사용할 수 없다`() {
        val user = loggedInUser()

        refresh(user.refreshToken).andExpect { status { isOk() } }

        refresh(user.refreshToken).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_TOKEN.code) }
        }
    }

    @Test
    fun `로그아웃하면 리프레시 토큰을 사용할 수 없다`() {
        val user = loggedInUser()

        mockMvc.delete("/auth/logout") {
            authorization(user.accessToken)
        }.andExpect { status { isNoContent() } }

        refresh(user.refreshToken).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_TOKEN.code) }
        }
    }

    private fun refresh(refreshToken: String) =
        mockMvc.post("/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = RefreshTokenRequest(refreshToken).toJson()
        }
}
