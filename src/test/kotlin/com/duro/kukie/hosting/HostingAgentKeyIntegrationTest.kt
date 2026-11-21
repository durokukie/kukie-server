package com.duro.kukie.hosting

import com.duro.kukie.hosting.application.HostingAgentKeys
import com.duro.kukie.hosting.domain.HostingAgentKey
import com.duro.kukie.hosting.domain.HostingAgentKeyRepository
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import java.util.UUID

class HostingAgentKeyIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var keys: HostingAgentKeys

    @Autowired
    private lateinit var keyRepository: HostingAgentKeyRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `에이전트 키로 에이전트 API 를 부를 수 있다`() {
        // given
        val member = loggedInUser()
        val key = keys.issue(member.user.id)

        // when & then
        waitFor(key).andExpect { status { isNotFound() } }
    }

    @Test
    fun `에이전트 키로는 다른 호스팅 API 도 키 목록도 부를 수 없다`() {
        // given
        val member = loggedInUser()
        val key = keys.issue(member.user.id)

        // when & then
        mockMvc.get("/hosting/apps") { authorization(key) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/hosting/agent-keys") { authorization(key) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `모르는 키는 401 이다`() {
        // when & then
        waitFor("hk_unknown").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `30일 쓰지 않은 키는 401 이고 지워진다`() {
        // given
        val member = loggedInUser()
        val key = keys.issue(member.user.id)
        jdbcTemplate.update("update tbl_hosting_agent_key set last_used_at = last_used_at - interval '31 days'")

        // when & then
        waitFor(key).andExpect { status { isUnauthorized() } }
        keyRepository.count() shouldBe 0
    }

    @Test
    fun `1분 넘게 지난 사용 시각은 키를 쓰면 다시 적는다`() {
        // given
        val member = loggedInUser()
        val key = keys.issue(member.user.id)
        jdbcTemplate.update("update tbl_hosting_agent_key set last_used_at = last_used_at - interval '2 minutes'")
        val before = keyRepository.findAll().single().lastUsedAt

        // when
        waitFor(key)

        // then
        keyRepository.findAll().single().lastUsedAt.isAfter(before.plusSeconds(90)) shouldBe true
    }

    @Test
    fun `회원마다 키는 10개까지이고 가장 오래 쓰지 않은 키부터 지운다`() {
        // given
        val member = loggedInUser()
        val oldest = keys.issue(member.user.id)
        jdbcTemplate.update("update tbl_hosting_agent_key set last_used_at = last_used_at - interval '1 day'")

        // when
        repeat(HostingAgentKey.MAX_PER_USER) { keys.issue(member.user.id) }

        // then
        keyRepository.findAll() shouldHaveSize HostingAgentKey.MAX_PER_USER
        keys.userIdOf(oldest) shouldBe null
    }

    @Test
    fun `연결한 에이전트를 보고 끊으면 그 키는 곧바로 401 이다`() {
        // given
        val member = loggedInUser()
        val key = keys.issue(member.user.id)
        val id = keyRepository.findAll().single().id

        // when
        mockMvc.get("/hosting/agent-keys") { authorization(member.accessToken) }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(id.toString()) }
            jsonPath("$[0].expiresAt") { exists() }
        }
        mockMvc.delete("/hosting/agent-keys/$id") { authorization(member.accessToken) }.andExpect { status { isNoContent() } }

        // then
        waitFor(key).andExpect { status { isUnauthorized() } }
        mockMvc.get("/hosting/agent-keys") { authorization(member.accessToken) }.andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `남의 키는 끊지 못한다`() {
        // given
        val owner = loggedInUser()
        val stranger = loggedInUser(UserFixture.user(email = "stranger@kukie.dev"))
        val key = keys.issue(owner.user.id)
        val id = keyRepository.findAll().single().id

        // when
        mockMvc.delete("/hosting/agent-keys/$id") { authorization(stranger.accessToken) }.andExpect { status { isNoContent() } }

        // then
        keys.userIdOf(key) shouldBe owner.user.id
    }

    private fun waitFor(key: String) = mockMvc.get("/hosting/agent/deploys/${UUID.randomUUID()}/wait") { authorization(key) }
}
