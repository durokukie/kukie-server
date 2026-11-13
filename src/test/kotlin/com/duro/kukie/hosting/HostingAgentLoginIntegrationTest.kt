package com.duro.kukie.hosting

import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.support.FakeHostingStateStore
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.CompletableFuture

class HostingAgentLoginIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var store: FakeHostingStateStore

    @Autowired
    private lateinit var jwt: JwtTokenProvider

    @Test
    fun `로그인을 시작하면 코드와 허용 화면 주소, 토큰을 받을 값을 준다`() {
        // when
        val login = startLogin()

        // then
        login.code shouldMatch Regex("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")
        login.loginUrl shouldBe "http://localhost:5173/agent"
        login.pollToken shouldStartWith "pt_"
    }

    @Test
    fun `로그인 시작은 서버 전체에서 1분에 120번까지다`() {
        // when & then
        repeat(120) { startLogin() }
        mockMvc.post("/hosting/agent/login").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("HOSTING_TOO_MANY_REQUESTS") }
        }
    }

    @Test
    fun `허용하면 에이전트는 호스팅 에이전트 토큰을 한 번 받는다`() {
        // given
        val member = linkedUser()
        allow("recklehs")
        val login = startLogin()

        // when
        mockMvc.post("/hosting/agent/login/${login.code.lowercase().replace("-", "")}/approve") {
            authorization(member.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        val body = token(login).andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_PLAIN) }
            header { string("Cache-Control", "no-store") }
        }.andReturn().response.contentAsString
        body shouldStartWith "Authorization: Bearer "
        jwt.getUserIdFromHostingAgentToken(body.removePrefix("Authorization: Bearer ").trim()) shouldBe member.user.id
        token(login).andExpect {
            status { isGone() }
            jsonPath("$.code") { value("HOSTING_AGENT_EXPIRED") }
        }
    }

    @Test
    fun `기다리는 중에 허용되면 그 요청이 토큰을 받는다`() {
        // given
        val member = linkedUser()
        allow("recklehs")
        val login = startLogin()

        // when
        val waiting = CompletableFuture.supplyAsync { token(login).andReturn().response }
        Thread.sleep(300)
        mockMvc.post("/hosting/agent/login/${login.code}/approve") { authorization(member.accessToken) }

        // then
        val response = waiting.get()
        response.status shouldBe 200
        response.contentAsString shouldStartWith "Authorization: Bearer "
    }

    @Test
    fun `허용 목록 파일이 없으면 App 을 설치한 계정의 허용 여부로 본다`() {
        // given
        val member = linkedUser()
        store.files["apps/_repos/recklehs.json"] =
            """{ "version": 1, "owner": { "id": ${GITHUB.id}, "login": "Recklehs", "type": "User" }, "allowed": true, "repos": [], "at": "2026-11-12T01:17:44Z" }"""
        val login = startLogin()

        // when & then
        mockMvc.post("/hosting/agent/login/${login.code}/approve") {
            authorization(member.accessToken)
        }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `에이전트 토큰으로는 다른 호스팅 API 도 로그인 허용도 부를 수 없다`() {
        // given
        val member = linkedUser()
        val agentToken = jwt.generateHostingAgentToken(member.user.id)
        val login = startLogin()

        // when & then
        mockMvc.get("/hosting/apps") { authorization(agentToken) }.andExpect { status { isUnauthorized() } }
        mockMvc.post("/hosting/agent/login/${login.code}/approve") { authorization(agentToken) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `허용 목록 밖 계정은 허용할 수 없고 에이전트도 그렇게 안다`() {
        // given
        val member = linkedUser()
        allow("someone-else")
        val login = startLogin()

        // when & then
        mockMvc.post("/hosting/agent/login/${login.code}/approve") {
            authorization(member.accessToken)
        }.andExpect { status { isForbidden() } }
        token(login).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("HOSTING_NOT_ALLOWED") }
        }
    }

    @Test
    fun `GitHub 계정을 연결하지 않았으면 허용할 수 없다`() {
        // given
        val member = loggedInUser()
        val login = startLogin()

        // when & then
        mockMvc.post("/hosting/agent/login/${login.code}/approve") {
            authorization(member.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_GITHUB_NOT_LINKED") }
        }
    }

    @Test
    fun `허용 전에는 기다렸다가 아직이라고 답하고 모르는 값은 만료로 답한다`() {
        // given
        val login = startLogin()

        // when & then
        token(login).andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("pending") }
        }
        token(Login("", "", "pt_unknown")).andExpect {
            status { isGone() }
            jsonPath("$.code") { value("HOSTING_AGENT_EXPIRED") }
        }
    }

    @Test
    fun `거부하면 에이전트는 거부됐다는 답을 받는다`() {
        // given
        val member = linkedUser()
        val login = startLogin()

        // when
        mockMvc.post("/hosting/agent/login/${login.code}/deny") {
            authorization(member.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        token(login).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("HOSTING_AGENT_DENIED") }
        }
    }

    @Test
    fun `허용 화면은 코드로 요청 시각을 보여 준다`() {
        // given
        val member = linkedUser()
        val login = startLogin()

        // when & then
        mockMvc.get("/hosting/agent/login/${login.code.lowercase()}") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value(login.code) }
            jsonPath("$.requestedAt") { exists() }
        }
    }

    @Test
    fun `처리한 요청이나 모르는 코드는 만료로 답한다`() {
        // given
        val member = linkedUser()
        allow("recklehs")
        val login = startLogin()
        mockMvc.post("/hosting/agent/login/${login.code}/approve") { authorization(member.accessToken) }

        // when & then
        mockMvc.post("/hosting/agent/login/${login.code}/deny") { authorization(member.accessToken) }.andExpect { status { isGone() } }
        mockMvc.get("/hosting/agent/login/ZZZZ-ZZZZ") { authorization(member.accessToken) }.andExpect { status { isGone() } }
    }

    @Test
    fun `코드 조회는 회원마다 1분에 20번까지다`() {
        // given
        val member = linkedUser()

        // when & then
        repeat(20) {
            mockMvc.get("/hosting/agent/login/ZZZZ-ZZZZ") { authorization(member.accessToken) }.andExpect { status { isGone() } }
        }
        mockMvc.get("/hosting/agent/login/ZZZZ-ZZZZ") { authorization(member.accessToken) }.andExpect { status { isTooManyRequests() } }
    }

    private fun startLogin(): Login {
        val body = mockMvc.post("/hosting/agent/login").andExpect { status { isOk() } }.andReturn().response.contentAsString
        val json = JsonMapper.builder().build().readTree(body)
        return Login(json.path("code").stringValue(), json.path("loginUrl").stringValue(), json.path("pollToken").stringValue())
    }

    private fun token(login: Login): ResultActionsDsl = mockMvc.post("/hosting/agent/token") {
        contentType = MediaType.APPLICATION_FORM_URLENCODED
        param("pollToken", login.pollToken)
    }

    private fun linkedUser(): LoggedInUser = loggedInUser(UserFixture.oAuthUser().apply { linkGitHub(GITHUB) })

    private fun allow(vararg logins: String) {
        store.files["apps/_allowed.json"] = """{ "version": 1, "logins": [${logins.joinToString(",") { "\"$it\"" }}] }"""
    }

    private data class Login(val code: String, val loginUrl: String, val pollToken: String)

    companion object {
        private val GITHUB = GitHubAccount(id = 127021195, login = "Recklehs")
    }
}
