package com.duro.kukie.auth

import com.duro.kukie.auth.exception.AuthErrorCode
import com.duro.kukie.auth.domain.PkceS256
import com.duro.kukie.auth.presentation.dto.request.ExchangeHandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.HandoffCodeRequest
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.global.config.properties.AuthCookieProperties
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.support.FakeOAuthClient
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class AuthIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var fakeOAuthClient: FakeOAuthClient

    @Autowired
    private lateinit var cookies: AuthCookieProperties

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

    // ── 웹: 토큰을 httpOnly 쿠키로도 주고, 쿠키로 온 토큰도 읽는다 (#25) ──────────────

    @Test
    fun `로그인 응답은 토큰을 본문과 httpOnly 쿠키로 함께 준다`() {
        // given
        val user = userRepository.save(UserFixture.user())
        val request = LogInRequest(user.email, UserFixture.DEFAULT_PASSWORD)

        // when & then
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
            cookie {
                exists(cookies.accessName)
                exists(cookies.refreshName)
            }
        }
    }

    @Test
    fun `소셜 로그인 응답의 토큰 쿠키는 화면 코드가 못 읽고 https 에서만 같은 사이트 요청에 실린다`() {
        // given
        val request = OAuthLogInRequest("code", "http://127.0.0.1:3000/callback")

        // when & then
        mockMvc.post("/auth/oauth/github") {
            contentType = MediaType.APPLICATION_JSON
            content = request.toJson()
        }.andExpect {
            status { isOk() }
            cookie {
                httpOnly(cookies.accessName, true)
                secure(cookies.accessName, true)
                sameSite(cookies.accessName, "Lax")
                path(cookies.accessName, "/")
                maxAge(cookies.accessName, 30 * 60)
                httpOnly(cookies.refreshName, true)
                secure(cookies.refreshName, true)
                sameSite(cookies.refreshName, "Lax")
                path(cookies.refreshName, "/")
                maxAge(cookies.refreshName, 30 * 24 * 60 * 60)
            }
        }
    }

    @Test
    fun `액세스 토큰을 쿠키로만 보내도 인증된다`() {
        // given
        val user = loggedInUser()

        // when & then
        mockMvc.get("/users/me") {
            cookie(Cookie(cookies.accessName, user.accessToken))
            fromSameSite()
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(user.user.email) }
        }
    }

    @Test
    fun `헤더와 쿠키에 토큰이 둘 다 있으면 헤더의 토큰을 쓴다`() {
        // given
        val headerUser = loggedInUser(UserFixture.user(email = "header@example.com"))
        val cookieUser = loggedInUser(UserFixture.user(email = "cookie@example.com"))

        // when & then
        mockMvc.get("/users/me") {
            authorization(headerUser.accessToken)
            cookie(Cookie(cookies.accessName, cookieUser.accessToken))
            fromSameSite()
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(headerUser.user.email) }
        }
    }

    @Test
    fun `값이 빈 Authorization 헤더는 없는 것으로 치고 쿠키를 본다`() {
        // given — `Authorization:` 만 붙인 클라이언트가 쿠키 로그인을 통째로 잃지 않게
        val user = loggedInUser()

        // when & then
        mockMvc.get("/users/me") {
            header(HttpHeaders.AUTHORIZATION, "")
            cookie(Cookie(cookies.accessName, user.accessToken))
            fromSameSite()
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(user.user.email) }
        }
    }

    @Test
    fun `헤더가 있으면 모양이 틀려도 쿠키로 넘어가지 않는다`() {
        // given — Basic 을 보낸 클라이언트가 조용히 남의 쿠키 세션으로 돌면 안 된다
        val user = loggedInUser()

        // when & then
        mockMvc.get("/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Basic abc")
            cookie(Cookie(cookies.accessName, user.accessToken))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.UNAUTHORIZED.code) }
        }
    }

    @Test
    fun `다른 사이트에서 시작됐거나 출처를 모르는 요청은 쿠키로 인증하지 않는다`() {
        // given — SameSite=Lax 도 top-level GET 은 통과시키므로 브라우저의 Sec-Fetch-Site 로 한 번 더 거른다.
        // 헤더가 없어도 거부(fail-closed) — 통과시키면 그 브라우저에서는 검사가 없는 것과 같다
        val user = loggedInUser()

        // when & then
        mockMvc.get("/users/me") {
            cookie(Cookie(cookies.accessName, user.accessToken))
            header("Sec-Fetch-Site", "cross-site")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(AuthErrorCode.CROSS_SITE_COOKIE.code) }
        }
        mockMvc.get("/users/me") {
            cookie(Cookie(cookies.accessName, user.accessToken))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(AuthErrorCode.CROSS_SITE_COOKIE.code) }
        }
        // 같은 도메인의 다른 서브도메인(same-site)도 거부 — 웹은 이 서버와 같은 오리진이라 잃는 게 없다
        mockMvc.get("/users/me") {
            cookie(Cookie(cookies.accessName, user.accessToken))
            header("Sec-Fetch-Site", "same-site")
        }.andExpect { status { isForbidden() } }
        // 우리 페이지 · 주소창 직접 입력은 통과. 헤더 토큰은 cross-site 여도 통과
        for (site in listOf("same-origin", "none")) {
            mockMvc.get("/users/me") {
                cookie(Cookie(cookies.accessName, user.accessToken))
                header("Sec-Fetch-Site", site)
            }.andExpect { status { isOk() } }
        }
        mockMvc.get("/users/me") {
            authorization(user.accessToken)
            header("Sec-Fetch-Site", "cross-site")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `리프레시 토큰을 쿠키로만 보내도 재발급되고 새 토큰이 쿠키로 내려간다`() {
        // given
        val user = loggedInUser()

        // when & then
        mockMvc.post("/auth/refresh") {
            cookie(Cookie(cookies.refreshName, user.refreshToken))
            fromSameSite()
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
            cookie {
                exists(cookies.accessName)
                exists(cookies.refreshName)
                httpOnly(cookies.refreshName, true)
            }
        }

        // 회전: 쿠키로 쓴 옛 리프레시 토큰도 더는 안 된다
        refresh(user.refreshToken).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_TOKEN.code) }
        }
    }

    @Test
    fun `리프레시 쿠키도 우리 페이지에서 온 요청에만 쓴다`() {
        // given — /auth/refresh 는 @Authenticated 가 아니라 인터셉터를 안 지난다. 그래도 같은 문(AuthCookies)을 지나야 한다
        val user = loggedInUser()

        // when & then — 다른 서브도메인(same-site)이나 출처 없음이면 403, 회전도 일어나지 않는다
        for (site in listOf("same-site", "cross-site", null)) {
            mockMvc.post("/auth/refresh") {
                cookie(Cookie(cookies.refreshName, user.refreshToken))
                if (site != null) header("Sec-Fetch-Site", site)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value(AuthErrorCode.CROSS_SITE_COOKIE.code) }
            }
        }
        refresh(user.refreshToken).andExpect { status { isOk() } }
    }

    @Test
    fun `리프레시 토큰이 본문에도 쿠키에도 없으면 인증이 필요하다는 응답을 준다`() {
        mockMvc.post("/auth/refresh").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.UNAUTHORIZED.code) }
        }
    }

    @Test
    fun `로그아웃하면 토큰 쿠키를 지운다`() {
        // given
        val user = loggedInUser()

        // when & then
        mockMvc.delete("/auth/logout") {
            cookie(Cookie(cookies.accessName, user.accessToken))
            fromSameSite()
        }.andExpect {
            status { isNoContent() }
            cookie {
                maxAge(cookies.accessName, 0)
                maxAge(cookies.refreshName, 0)
                path(cookies.accessName, "/")
            }
        }
    }

    /** 브라우저가 우리 페이지에서 보낸 요청에 붙이는 표시. 쿠키 인증은 이게 있어야 받는다. */
    // ── 앱 넘겨주기 (DURO-109): 시스템 브라우저에서 로그인한 페이지 → 1회용 코드 → 앱이 토큰으로 ──

    @Test
    fun `로그인된 페이지가 받은 넘겨주기 코드를 앱이 PKCE 원본과 함께 내면 토큰과 쿠키를 받는다`() {
        val loggedIn = loggedInUser()
        val verifier = "app-made-random-verifier-0123456789abcdef"

        val code = issueHandoffCode(loggedIn.accessToken, PkceS256.challengeOf(verifier))

        val result = mockMvc.post("/auth/exchange") {
            contentType = MediaType.APPLICATION_JSON
            content = ExchangeHandoffCodeRequest(code, verifier).toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
            cookie { exists(cookies.accessName); httpOnly(cookies.accessName, true) }
            cookie { exists(cookies.refreshName); httpOnly(cookies.refreshName, true) }
        }.andReturn()

        // 받은 토큰이 실제로 그 사용자의 것이다
        val newAccessToken = result.response.cookies.first { it.name == cookies.accessName }.value
        mockMvc.get("/users/me") {
            authorization(newAccessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(loggedIn.user.id.toString()) }
        }
    }

    @Test
    fun `넘겨주기 코드는 웹 페이지의 쿠키 로그인으로도 받을 수 있다`() {
        val loggedIn = loggedInUser()

        mockMvc.post("/auth/handoff") {
            cookie(Cookie(cookies.accessName, loggedIn.accessToken))
            fromSameSite()
            contentType = MediaType.APPLICATION_JSON
            content = HandoffCodeRequest("challenge").toJson()
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { isNotEmpty() }
            jsonPath("$.expiresIn") { value(60) }
        }
    }

    @Test
    fun `로그인 없이는 넘겨주기 코드를 받을 수 없다`() {
        mockMvc.post("/auth/handoff") {
            contentType = MediaType.APPLICATION_JSON
            content = HandoffCodeRequest("challenge").toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.UNAUTHORIZED.code) }
        }
    }

    @Test
    fun `PKCE 원본이 맡긴 검증값과 다르면 코드를 교환할 수 없다`() {
        val loggedIn = loggedInUser()
        val code = issueHandoffCode(loggedIn.accessToken, PkceS256.challengeOf("the-real-verifier"))

        mockMvc.post("/auth/exchange") {
            contentType = MediaType.APPLICATION_JSON
            content = ExchangeHandoffCodeRequest(code, "someone-elses-verifier").toJson()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_HANDOFF_CODE.code) }
        }
    }

    @Test
    fun `넘겨주기 코드는 한 번만 쓸 수 있다 - 틀린 원본으로 한 번 시도한 뒤에도 사라진다`() {
        val loggedIn = loggedInUser()
        val verifier = "verifier-used-once"
        val code = issueHandoffCode(loggedIn.accessToken, PkceS256.challengeOf(verifier))

        exchange(code, verifier).andExpect { status { isOk() } }
        exchange(code, verifier).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_HANDOFF_CODE.code) }
        }

        // 틀린 원본으로 한 번 두드리면 코드가 소모된다 — 맞는 원본으로 다시 와도 안 된다
        val second = issueHandoffCode(loggedIn.accessToken, PkceS256.challengeOf(verifier))
        exchange(second, "wrong").andExpect { status { isUnauthorized() } }
        exchange(second, verifier).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `없는 넘겨주기 코드는 교환할 수 없다`() {
        exchange("never-issued", "whatever").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_HANDOFF_CODE.code) }
        }
    }

    private fun issueHandoffCode(accessToken: String, codeChallenge: String): String {
        val body = mockMvc.post("/auth/handoff") {
            authorization(accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = HandoffCodeRequest(codeChallenge).toJson()
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex("\"code\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun exchange(code: String, verifier: String) = mockMvc.post("/auth/exchange") {
        contentType = MediaType.APPLICATION_JSON
        content = ExchangeHandoffCodeRequest(code, verifier).toJson()
    }

    private fun MockHttpServletRequestDsl.fromSameSite() = header("Sec-Fetch-Site", "same-origin")

    private fun refresh(refreshToken: String) =
        mockMvc.post("/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = RefreshTokenRequest(refreshToken).toJson()
        }
}
