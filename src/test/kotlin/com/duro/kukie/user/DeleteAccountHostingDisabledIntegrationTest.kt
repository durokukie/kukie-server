package com.duro.kukie.user

import com.duro.kukie.support.FakeHostingCommandQueue
import com.duro.kukie.support.FakeHostingStateStore
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.DeleteAccountIntegrationTest.Companion.GITHUB_ID
import com.duro.kukie.user.DeleteAccountIntegrationTest.Companion.hostingAppJson
import com.duro.kukie.user.domain.GitHubAccount
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.delete

@TestPropertySource(properties = ["hosting.state-bucket="])
class DeleteAccountHostingDisabledIntegrationTest : IntegrationTest() {

    @Autowired private lateinit var hostingStore: FakeHostingStateStore
    @Autowired private lateinit var hostingQueue: FakeHostingCommandQueue

    @Test
    fun `호스팅이 꺼진 서버에서는 탈퇴해도 호스팅 명령을 보내지 않는다`() {
        val me = loggedInUser(UserFixture.oAuthUser().apply { linkGitHub(GitHubAccount(GITHUB_ID, "Recklehs")) })
        hostingStore.files["apps/recklehs/shop.json"] = hostingAppJson("shop", GITHUB_ID)

        mockMvc.delete("/users/me") { authorization(me.accessToken) }.andExpect { status { isNoContent() } }

        hostingQueue.commands.shouldBeEmpty()
    }
}
