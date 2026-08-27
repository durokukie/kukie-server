package com.duro.kukie.auth

import com.duro.kukie.auth.exception.AuthErrorCode
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.UserFixture
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class AuthIntegrationTest : IntegrationTest() {

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
