package com.duro.kukie.global.security

import com.duro.kukie.global.config.properties.AuthCookieProperties
import com.duro.kukie.global.config.properties.JwtProperties
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

class AuthCookiesTest {

    private val jwtProperties = JwtProperties("secret", Duration.ofMinutes(30), Duration.ofDays(30))

    private fun authCookies(secure: Boolean) = AuthCookies(AuthCookieProperties(secure = secure), jwtProperties)

    @Test
    fun `https 에서는 __Host- 이름으로 굽고 접두사 없는 옛 쿠키를 지운다`() {
        // given
        val response = MockHttpServletResponse()

        // when
        authCookies(secure = true).issue(response, "access", "refresh")

        // then
        response.cookies.map { listOf(it.name, it.value, it.maxAge, it.secure, it.isHttpOnly, it.path) } shouldContainExactly listOf(
            listOf("__Host-kukie_access", "access", 30 * 60, true, true, "/"),
            listOf("__Host-kukie_refresh", "refresh", 30 * 24 * 60 * 60, true, true, "/"),
            listOf("kukie_access", "", 0, true, true, "/"),
            listOf("kukie_refresh", "", 0, true, true, "/"),
        )
        response.getHeaders(HttpHeaders.SET_COOKIE).none { it.contains("Domain", ignoreCase = true) } shouldBe true
    }

    @Test
    fun `https 에서 지울 때도 굽는 이름과 같은 __Host- 이름을 쓴다`() {
        // given
        val response = MockHttpServletResponse()

        // when
        authCookies(secure = true).clear(response)

        // then
        response.cookies.map { it.name to it.maxAge } shouldContainExactlyInAnyOrder listOf(
            "__Host-kukie_access" to 0,
            "__Host-kukie_refresh" to 0,
            "kukie_access" to 0,
            "kukie_refresh" to 0,
        )
    }

    @Test
    fun `https 에서는 __Host- 쿠키만 읽고 접두사 없는 쿠키는 없는 것으로 본다`() {
        // given
        val cookies = authCookies(secure = true)

        // when & then
        cookies.accessToken(request(Cookie("__Host-kukie_access", "a"))) shouldBe "a"
        cookies.refreshToken(request(Cookie("__Host-kukie_refresh", "r"))) shouldBe "r"
        cookies.accessToken(request(Cookie("kukie_access", "a"))) shouldBe null
        cookies.refreshToken(request(Cookie("kukie_refresh", "r"))) shouldBe null
    }

    @Test
    fun `로컬 http 에서는 접두사 없는 이름으로 굽고 읽는다`() {
        // given
        val cookies = authCookies(secure = false)
        val issued = MockHttpServletResponse()
        val cleared = MockHttpServletResponse()

        // when
        cookies.issue(issued, "access", "refresh")
        cookies.clear(cleared)

        // then
        issued.cookies.map { it.name to it.secure } shouldContainExactly listOf("kukie_access" to false, "kukie_refresh" to false)
        cleared.cookies.map { it.name to it.maxAge } shouldContainExactly listOf("kukie_access" to 0, "kukie_refresh" to 0)
        cookies.accessToken(request(Cookie("kukie_access", "a"))) shouldBe "a"
        cookies.accessToken(request(Cookie("__Host-kukie_access", "a"))) shouldBe null
    }

    private fun request(vararg cookies: Cookie) = MockHttpServletRequest().apply {
        setCookies(*cookies)
        addHeader("Sec-Fetch-Site", "same-origin")
    }
}
