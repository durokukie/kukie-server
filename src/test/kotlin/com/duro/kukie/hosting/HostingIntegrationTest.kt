package com.duro.kukie.hosting

import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.support.FakeHostingCommandQueue
import com.duro.kukie.support.FakeHostingStateStore
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class HostingIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var store: FakeHostingStateStore

    @Autowired
    private lateinit var queue: FakeHostingCommandQueue

    @Test
    fun `연결한 GitHub 계정이 주인인 앱만 목록에 나온다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        putApp("stolen", ownerId = 999)

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.githubLogin") { value("Recklehs") }
            jsonPath("$.appLimit") { value(3) }
            jsonPath("$.apps.length()") { value(1) }
            jsonPath("$.apps[0].app") { value("shop") }
            jsonPath("$.apps[0].repository") { value("Recklehs/shop") }
            jsonPath("$.apps[0].url") { value("https://shop-recklehs.apps.dev.durokukie.com") }
            jsonPath("$.apps[0].config.port") { value(3000) }
            jsonPath("$.apps[0].latest.phase") { value("succeeded") }
            jsonPath("$.apps[0].latest.at") { value("2026-11-10T01:19:36Z") }
            jsonPath("$.apps[0].owner") { doesNotExist() }
            jsonPath("$.skippedPush") { value(null) }
        }
    }

    @Test
    fun `GitHub 계정을 연결하지 않은 회원의 목록은 비어 있다`() {
        // given
        val member = loggedInUser()
        putApp("shop")

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.githubLogin") { value(null) }
            jsonPath("$.apps.length()") { value(0) }
        }
    }

    @Test
    fun `모르는 버전이거나 깨진 앱 파일은 목록에서 뺀다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        store.files["apps/recklehs/next.json"] = appJson("next").replace("\"version\": 1", "\"version\": 2")
        store.files["apps/recklehs/broken.json"] = "{"

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.apps.length()") { value(1) }
        }
    }

    @Test
    fun `배포를 시작하지 못한 push 를 알려 준다`() {
        // given
        val member = linkedUser()
        putSkipped(repo = "Recklehs/fourth", at = "2026-11-10T02:00:00Z")

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.skippedPush.repository") { value("Recklehs/fourth") }
            jsonPath("$.skippedPush.reason") { value("app_limit") }
        }
    }

    @Test
    fun `그 뒤에 같은 레포가 배포됐으면 지난 push 는 알리지 않는다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        putSkipped(repo = "recklehs/SHOP", at = "2026-11-10T01:00:00Z")

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.skippedPush") { value(null) }
        }
    }

    @Test
    fun `다른 사람이 push 한 기록은 알리지 않는다`() {
        // given
        val member = linkedUser()
        putSkipped(repo = "Recklehs/fourth", at = "2026-11-10T02:00:00Z", senderId = 999)

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.skippedPush") { value(null) }
        }
    }

    @Test
    fun `앱 상세는 지금 세대의 배포를 최신순으로 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        putDeployment("shop", GENERATION, "20261109T000000Z-aaaaaaa", phase = "failed", failure = """{ "code": "build_failed", "message": "build x failed" }""")
        putDeployment("shop", GENERATION, "20261110T011744Z-0123456", phase = "succeeded")
        putDeployment("shop", "20250101T000000Z", "20250101T000001Z-bbbbbbb", phase = "succeeded")
        store.files["deployments/recklehs/shop/$GENERATION/20261110T011744Z-0123456.log"] = "=== source ==="

        // when & then
        mockMvc.get("/hosting/apps/shop") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.app.app") { value("shop") }
            jsonPath("$.deployments.length()") { value(2) }
            jsonPath("$.deployments[0].deploymentId") { value("20261110T011744Z-0123456") }
            jsonPath("$.deployments[0].trigger") { value("push") }
            jsonPath("$.deployments[0].failure") { value(null) }
            jsonPath("$.deployments[1].failure.code") { value("build_failed") }
            jsonPath("$.deployments[1].startedAt") { value("2026-11-10T01:17:44Z") }
        }
    }

    @Test
    fun `다른 계정이 주인인 앱은 찾을 수 없다`() {
        // given
        val member = linkedUser()
        putApp("stolen", ownerId = 999)

        // when & then
        mockMvc.get("/hosting/apps/stolen") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("HOSTING_APP_NOT_FOUND") }
        }
    }

    @Test
    fun `앱 이름 모양이 아닌 값으로는 버킷을 읽지 않는다`() {
        // given
        val member = linkedUser()
        store.files["apps/recklehs/Shop_1.json"] = appJson("Shop_1")

        // when & then
        mockMvc.get("/hosting/apps/Shop_1") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `빌드 로그를 글로 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        store.files["deployments/recklehs/shop/$GENERATION/20261110T011744Z-0123456.log"] = "=== source ===\n빌드 끝"

        // when & then
        mockMvc.get("/hosting/apps/shop/deployments/20261110T011744Z-0123456/log") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            content { contentType("text/plain;charset=UTF-8") }
            content { string("=== source ===\n빌드 끝") }
        }
    }

    @Test
    fun `빌드 로그가 없으면 찾을 수 없다고 한다`() {
        // given
        val member = linkedUser()
        putApp("shop")

        // when & then
        mockMvc.get("/hosting/apps/shop/deployments/20261110T011744Z-0123456/log") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("HOSTING_LOG_NOT_FOUND") }
        }
    }

    @Test
    fun `다시 배포를 요청하면 배포기에 명령을 보낸다`() {
        // given
        val member = linkedUser()
        putApp("shop")

        // when & then
        mockMvc.post("/hosting/apps/shop/redeploy") {
            authorization(member.accessToken)
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.commandId") { value(queue.commands.single().commandId) }
        }

        with(queue.commands.single()) {
            version shouldBe 1
            type shouldBe HostingCommand.REDEPLOY
            repo.fullName shouldBe "Recklehs/shop"
            team shouldBe "recklehs"
            app shouldBe "shop"
            generation shouldBe GENERATION
            owner.id shouldBe GITHUB.id
        }
    }

    @Test
    fun `삭제를 요청하면 앱의 세대를 넣은 명령을 보낸다`() {
        // given
        val member = linkedUser()
        putApp("shop")

        // when & then
        mockMvc.delete("/hosting/apps/shop") {
            authorization(member.accessToken)
        }.andExpect {
            status { isAccepted() }
        }

        with(queue.commands.single()) {
            type shouldBe HostingCommand.DELETE
            generation shouldBe GENERATION
        }
    }

    @Test
    fun `다른 계정이 주인인 앱에는 명령을 보내지 않는다`() {
        // given
        val member = linkedUser()
        putApp("stolen", ownerId = 999)

        // when & then
        mockMvc.delete("/hosting/apps/stolen") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
        }
        mockMvc.post("/hosting/apps/stolen/redeploy") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
        }

        queue.commands.shouldBeEmpty()
    }

    @Test
    fun `명령을 보내지 못했으면 곧바로 다시 보낼 수 있다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        queue.shouldFail = true
        mockMvc.post("/hosting/apps/shop/redeploy") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.code") { value("HOSTING_UNAVAILABLE") }
        }
        queue.shouldFail = false

        // when & then
        mockMvc.post("/hosting/apps/shop/redeploy") {
            authorization(member.accessToken)
        }.andExpect {
            status { isAccepted() }
        }
    }

    @Test
    fun `같은 앱에 같은 명령을 곧바로 다시 보낼 수 없다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        mockMvc.post("/hosting/apps/shop/redeploy") { authorization(member.accessToken) }

        // when & then
        mockMvc.post("/hosting/apps/shop/redeploy") {
            authorization(member.accessToken)
        }.andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("HOSTING_COMMAND_TOO_SOON") }
        }
        mockMvc.delete("/hosting/apps/shop") {
            authorization(member.accessToken)
        }.andExpect {
            status { isAccepted() }
        }
        queue.commands.map { it.type } shouldBe listOf(HostingCommand.REDEPLOY, HostingCommand.DELETE)
    }

    @Test
    fun `같은 레포의 앱이 있어도 그 뒤에 건너뛴 push 는 알려 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        putSkipped(repo = "Recklehs/shop", at = "2026-11-10T02:00:00Z")

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.skippedPush.repository") { value("Recklehs/shop") }
        }
    }

    @Test
    fun `배포 이력은 최신 20건까지만 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        (10..31).forEach { putDeployment("shop", GENERATION, "202611${it}T000000Z-aaaaaaa", phase = "succeeded") }

        // when & then
        mockMvc.get("/hosting/apps/shop") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.deployments.length()") { value(20) }
            jsonPath("$.deployments[0].deploymentId") { value("20261131T000000Z-aaaaaaa") }
            jsonPath("$.deployments[19].deploymentId") { value("20261112T000000Z-aaaaaaa") }
        }
    }

    @Test
    fun `배포 id 모양이 아닌 값으로는 로그를 찾지 않는다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        store.files["deployments/recklehs/shop/$GENERATION/latest.log"] = "log"

        // when & then
        mockMvc.get("/hosting/apps/shop/deployments/latest/log") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("HOSTING_LOG_NOT_FOUND") }
        }
    }

    @Test
    fun `다른 계정이 주인인 앱의 빌드 로그는 볼 수 없다`() {
        // given
        val member = linkedUser()
        putApp("stolen", ownerId = 999)
        store.files["deployments/recklehs/stolen/$GENERATION/20261110T011744Z-0123456.log"] = "secret"

        // when & then
        mockMvc.get("/hosting/apps/stolen/deployments/20261110T011744Z-0123456/log") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("HOSTING_APP_NOT_FOUND") }
        }
    }

    @Test
    fun `상태 버킷을 읽지 못하면 잠시 뒤 다시 시도하라고 한다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        store.shouldFail = true

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("HOSTING_UNAVAILABLE") }
        }
    }

    @Test
    fun `로그인하지 않으면 볼 수 없다`() {
        // when & then
        mockMvc.get("/hosting/apps").andExpect {
            status { isUnauthorized() }
        }
    }

    private fun linkedUser(): LoggedInUser = loggedInUser(UserFixture.oAuthUser().apply { linkGitHub(GITHUB) })

    private fun putApp(app: String, ownerId: Long = GITHUB.id) {
        store.files["apps/recklehs/$app.json"] = appJson(app, ownerId)
    }

    private fun appJson(app: String, ownerId: Long = GITHUB.id) = """
        {
          "version": 1,
          "owner": { "id": $ownerId, "login": "Recklehs", "type": "User" },
          "repo": { "id": 987654321, "fullName": "Recklehs/$app" },
          "team": "recklehs",
          "app": "$app",
          "generation": "$GENERATION",
          "url": "https://$app-recklehs.apps.dev.durokukie.com",
          "config": { "port": 3000, "size": "S", "source": "default" },
          "latest": {
            "deploymentId": "20261110T011744Z-0123456",
            "phase": "succeeded",
            "commit": { "sha": "0123456789abcdef", "message": "feat: 첫 화면" },
            "at": "2026-11-10T01:19:36Z"
          },
          "addedLater": true
        }
    """.trimIndent()

    private fun putDeployment(app: String, generation: String, id: String, phase: String, failure: String = "null") {
        store.files["deployments/recklehs/$app/$generation/$id.json"] = """
            {
              "version": 1,
              "deploymentId": "$id",
              "phase": "$phase",
              "trigger": "push",
              "commit": { "sha": "0123456789abcdef" },
              "config": { "port": 3000, "size": "S", "source": "default" },
              "imageReused": false,
              "hasLog": false,
              "failure": $failure,
              "startedAt": "2026-11-10T01:17:44Z",
              "updatedAt": "2026-11-10T01:17:51Z"
            }
        """.trimIndent()
    }

    private fun putSkipped(repo: String, at: String, senderId: Long = GITHUB.id) {
        store.files["apps/_skipped/recklehs.json"] =
            """{ "version": 1, "sender": { "id": $senderId, "login": "Recklehs" }, "repo": { "id": 1, "fullName": "$repo" }, "reason": "app_limit", "at": "$at" }"""
    }

    companion object {
        private const val GENERATION = "20261101T031500Z"
        private val GITHUB = GitHubAccount(id = 127021195, login = "Recklehs")
    }
}
