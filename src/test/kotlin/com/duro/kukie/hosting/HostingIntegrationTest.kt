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
import org.springframework.http.MediaType
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
    fun `자동으로 알아내 빌드한 배포는 알아낸 것과 시작 명령을 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        store.files["deployments/recklehs/shop/$GENERATION/20261112T011744Z-0123456.json"] = """
            {
              "version": 1, "deploymentId": "20261112T011744Z-0123456", "phase": "succeeded", "trigger": "deploy",
              "commit": { "sha": "0123456789abcdef" },
              "config": { "port": 3000, "size": "S", "source": "file", "start": "gunicorn app:app", "manual": true },
              "build": { "method": "auto", "providers": ["python"], "versions": { "python": "3.13.18" }, "start": "gunicorn app:app" },
              "imageReused": false, "hasLog": true, "failure": null,
              "startedAt": "2026-11-12T01:17:44Z", "updatedAt": "2026-11-12T01:21:51Z"
            }
        """.trimIndent()
        putDeployment("shop", GENERATION, "20261111T011744Z-0123456", "succeeded")

        // when & then
        mockMvc.get("/hosting/apps/shop") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.deployments[0].build.method") { value("auto") }
            jsonPath("$.deployments[0].build.providers[0]") { value("python") }
            jsonPath("$.deployments[0].build.versions.python") { value("3.13.18") }
            jsonPath("$.deployments[0].build.start") { value("gunicorn app:app") }
            jsonPath("$.deployments[0].config.start") { value("gunicorn app:app") }
            // kukie.json 이 자동 배포를 껐다
            jsonPath("$.deployments[0].config.manual") { value(true) }
            jsonPath("$.app.config.manual") { value(null) }
            // 배포기가 build 를 적기 전의 기록에는 없다
            jsonPath("$.deployments[1].build") { value(null) }
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
            repo?.fullName shouldBe "Recklehs/shop"
            team shouldBe "recklehs"
            app shouldBe "shop"
            generation shouldBe GENERATION
            owner.id shouldBe GITHUB.id
        }
    }

    @Test
    fun `App 에 허용한 레포를 목록과 함께 준다`() {
        // given
        val member = linkedUser()
        putRepos("Recklehs/shop", "Recklehs/blog")

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.installation.allowed") { value(true) }
            jsonPath("$.installation.repositories.length()") { value(2) }
            jsonPath("$.installation.repositories[0].fullName") { value("Recklehs/shop") }
            jsonPath("$.installation.repositories[0].private") { value(true) }
        }
    }

    @Test
    fun `App 을 설치하지 않았거나 다른 계정의 목록이면 설치가 없다고 한다`() {
        // given
        val member = linkedUser()

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.installation") { value(null) }
        }

        putRepos("Recklehs/shop", ownerId = 999)
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            jsonPath("$.installation") { value(null) }
        }
    }

    @Test
    fun `허용한 레포를 고르면 배포기에 배포 시작 명령을 보낸다`() {
        // given
        val member = linkedUser()
        putRepos("Recklehs/shop")

        // when & then
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "recklehs/SHOP"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.commandId") { value(queue.commands.single().commandId) }
        }

        with(queue.commands.single()) {
            type shouldBe HostingCommand.DEPLOY
            repo?.fullName shouldBe "Recklehs/shop"
            team shouldBe "recklehs"
            app shouldBe null
            generation shouldBe null
            owner.id shouldBe GITHUB.id
        }
    }

    @Test
    fun `허용하지 않은 레포나 다른 계정의 목록으로는 배포를 시작하지 않는다`() {
        // given
        val member = linkedUser()
        putRepos("Recklehs/shop")

        // when & then
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/secret"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("HOSTING_REPOSITORY_NOT_FOUND") }
        }

        putRepos("Recklehs/shop", ownerId = 999)
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/shop"}"""
        }.andExpect {
            status { isNotFound() }
        }

        queue.commands.shouldBeEmpty()
    }

    @Test
    fun `아직 호스팅을 쓸 수 없는 계정은 배포를 시작할 수 없다`() {
        // given
        val member = linkedUser()
        putRepos("Recklehs/shop", allowed = false)

        // when & then
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/shop"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("HOSTING_NOT_ALLOWED") }
        }

        // 목록에 없는 레포여도 까닭은 계정이다
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/none"}"""
        }.andExpect {
            status { isForbidden() }
        }

        queue.commands.shouldBeEmpty()
    }

    @Test
    fun `배포 시작은 계정마다 곧바로 다시 보낼 수 없다`() {
        // given
        val member = linkedUser()
        putRepos("Recklehs/shop", "Recklehs/blog")

        // when & then
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/shop"}"""
        }.andExpect { status { isAccepted() } }
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/shop"}"""
        }.andExpect { status { isTooManyRequests() } }
        mockMvc.post("/hosting/apps") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/blog"}"""
        }.andExpect { status { isTooManyRequests() } }
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

    @Test
    fun `업로드로 만든 앱은 레포 없이 목록에 나온다`() {
        // given
        val member = linkedUser()
        putUploadApp("my-shop")

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.apps.length()") { value(1) }
            jsonPath("$.apps[0].app") { value("my-shop") }
            jsonPath("$.apps[0].repository") { value(null) }
            jsonPath("$.apps[0].uploaded") { value(true) }
        }
    }

    @Test
    fun `거절된 업로드 명령은 레포 없이 명령 id 와 함께 알려 준다`() {
        // given
        val member = linkedUser()
        store.files["apps/_skipped/recklehs.json"] =
            """{ "version": 1, "sender": { "id": ${GITHUB.id}, "login": "Recklehs" }, "commandId": "c51b", "app": "my-shop", "reason": "source_mismatch", "at": "2026-11-13T01:00:00Z" }"""

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.skippedPush.repository") { value(null) }
            jsonPath("$.skippedPush.app") { value("my-shop") }
            jsonPath("$.skippedPush.commandId") { value("c51b") }
            jsonPath("$.skippedPush.reason") { value("source_mismatch") }
        }
    }

    @Test
    fun `거절된 뒤 같은 이름의 업로드 앱이 배포됐으면 지난 거절은 주지 않는다`() {
        // given
        val member = linkedUser()
        putUploadApp("my-shop")
        store.files["apps/_skipped/recklehs.json"] =
            """{ "version": 1, "sender": { "id": ${GITHUB.id}, "login": "Recklehs" }, "commandId": "c51b", "app": "my-shop", "reason": "internal", "at": "2026-11-10T01:00:00Z" }"""

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.skippedPush") { value(null) }
        }
    }

    @Test
    fun `업로드 앱은 다시 배포할 수 없다`() {
        // given
        val member = linkedUser()
        putUploadApp("my-shop")

        // when & then
        mockMvc.post("/hosting/apps/my-shop/redeploy") {
            authorization(member.accessToken)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_APP_SOURCE_MISMATCH") }
        }
        queue.commands.shouldBeEmpty()
    }

    @Test
    fun `업로드 앱의 삭제 명령에는 레포가 없다`() {
        // given
        val member = linkedUser()
        putUploadApp("my-shop")

        // when & then
        mockMvc.delete("/hosting/apps/my-shop") {
            authorization(member.accessToken)
        }.andExpect { status { isAccepted() } }

        with(queue.commands.single()) {
            type shouldBe HostingCommand.DELETE
            repo shouldBe null
            app shouldBe "my-shop"
        }
    }

    @Test
    fun `업로드 앱에 내 레포를 연결하면 연결 명령을 보낸다`() {
        // given
        val member = linkedUser()
        putUploadApp("my-shop")
        putRepos("Recklehs/shop-frontend")

        // when & then
        mockMvc.post("/hosting/apps/my-shop/connect") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "recklehs/SHOP-frontend"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.commandId") { exists() }
        }

        with(queue.commands.single()) {
            type shouldBe HostingCommand.CONNECT
            repo?.fullName shouldBe "Recklehs/shop-frontend"
            team shouldBe "recklehs"
            app shouldBe "my-shop"
            generation shouldBe GENERATION
            owner.id shouldBe GITHUB.id
            owner.login shouldBe "Recklehs"
        }
    }

    @Test
    fun `레포가 적힌 거절은 같은 이름의 다른 앱이 배포돼도 가리지 않는다`() {
        // given
        val member = linkedUser()
        putUploadApp("shop")
        store.files["apps/_skipped/recklehs.json"] =
            """{ "version": 1, "sender": { "id": ${GITHUB.id}, "login": "Recklehs" }, "repo": { "fullName": "Recklehs/shop" }, "commandId": "c41a", "app": "shop", "reason": "internal", "at": "2026-11-10T01:00:00Z" }"""

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.skippedPush.commandId") { value("c41a") }
        }
    }

    @Test
    fun `레포를 이미 다른 앱이 써서 거절한 것은 그 앱이 배포돼도 가리지 않는다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        store.files["apps/_skipped/recklehs.json"] =
            """{ "version": 1, "sender": { "id": ${GITHUB.id}, "login": "Recklehs" }, "repo": { "fullName": "Recklehs/shop" }, "commandId": "c61c", "app": "my-shop", "reason": "repository_in_use", "at": "2026-11-10T01:00:00Z" }"""

        // when & then
        mockMvc.get("/hosting/apps") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.skippedPush.reason") { value("repository_in_use") }
        }
    }

    @Test
    fun `남의 업로드 앱에는 연결할 수 없고 같은 앱에 곧바로 다시 연결할 수 없다`() {
        // given
        val member = linkedUser()
        store.files["apps/recklehs/theirs.json"] = appJson("theirs", ownerId = 999)
            .replace(""""repo": { "id": 987654321, "fullName": "Recklehs/theirs" },""", """"source": { "type": "upload" },""")
        putUploadApp("my-shop")
        putRepos("Recklehs/blog")

        // when & then
        mockMvc.post("/hosting/apps/theirs/connect") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/blog"}"""
        }.andExpect { status { isNotFound() } }
        repeat(2) { attempt ->
            mockMvc.post("/hosting/apps/my-shop/connect") {
                authorization(member.accessToken)
                contentType = MediaType.APPLICATION_JSON
                content = """{"repository": "Recklehs/blog"}"""
            }.andExpect { status { if (attempt == 0) isAccepted() else isTooManyRequests() } }
        }
        queue.commands.size shouldBe 1
    }

    @Test
    fun `레포로 만든 앱에는 레포를 연결할 수 없다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        putRepos("Recklehs/blog")

        // when & then
        mockMvc.post("/hosting/apps/shop/connect") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/blog"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_APP_SOURCE_MISMATCH") }
        }
        queue.commands.shouldBeEmpty()
    }

    @Test
    fun `이미 다른 앱이 쓰는 레포는 연결할 수 없다`() {
        // given
        val member = linkedUser()
        putUploadApp("my-shop")
        putApp("shop")
        putRepos("Recklehs/shop")

        // when & then
        mockMvc.post("/hosting/apps/my-shop/connect") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/shop"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_REPOSITORY_IN_USE") }
        }
        queue.commands.shouldBeEmpty()
    }

    @Test
    fun `App 에 허용하지 않은 레포나 허용 목록 밖 계정은 연결할 수 없다`() {
        // given
        val member = linkedUser()
        putUploadApp("my-shop")
        putRepos("Recklehs/blog")

        // when & then
        mockMvc.post("/hosting/apps/my-shop/connect") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/other"}"""
        }.andExpect { status { isNotFound() } }

        putRepos("Recklehs/blog", allowed = false)
        mockMvc.post("/hosting/apps/my-shop/connect") {
            authorization(member.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"repository": "Recklehs/blog"}"""
        }.andExpect { status { isForbidden() } }
        queue.commands.shouldBeEmpty()
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

    private fun putUploadApp(app: String) {
        store.files["apps/recklehs/$app.json"] = appJson(app)
            .replace(""""repo": { "id": 987654321, "fullName": "Recklehs/$app" },""", """"source": { "type": "upload" },""")
    }

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

    private fun putRepos(vararg repos: String, ownerId: Long = GITHUB.id, allowed: Boolean = true) {
        val items = repos.joinToString(",") { """{ "id": 1, "fullName": "$it", "private": true }""" }
        store.files["apps/_repos/recklehs.json"] =
            """{ "version": 1, "owner": { "id": $ownerId, "login": "Recklehs", "type": "User" }, "allowed": $allowed, "repos": [$items], "at": "2026-11-12T01:17:44Z" }"""
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
