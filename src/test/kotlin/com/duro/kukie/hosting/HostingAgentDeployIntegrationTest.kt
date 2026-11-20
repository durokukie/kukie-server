package com.duro.kukie.hosting

import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.hosting.application.HostingAgentDeploys
import com.duro.kukie.hosting.domain.HostingAgentDeployRepository
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.support.FakeHostingCommandQueue
import com.duro.kukie.support.FakeHostingFailureAnalyzer
import com.duro.kukie.support.FakeHostingStateStore
import com.duro.kukie.support.FakeHostingUploadStore
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

class HostingAgentDeployIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var store: FakeHostingStateStore

    @Autowired
    private lateinit var queue: FakeHostingCommandQueue

    @Autowired
    private lateinit var analyzer: FakeHostingFailureAnalyzer

    @Autowired
    private lateinit var uploads: FakeHostingUploadStore

    @Autowired
    private lateinit var jwt: JwtTokenProvider

    @Autowired
    private lateinit var deploys: HostingAgentDeployRepository

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Autowired
    private lateinit var users: UserRepository

    @Test
    fun `배포를 만들면 서버가 정한 키로 올릴 주소를 준다`() {
        // given
        val agent = agent()

        // when & then
        create(agent, "my-shop", 50L * 1024 * 1024).andExpect {
            status { isCreated() }
            jsonPath("$.deployId") { value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{40}")) }
            jsonPath("$.app") { value("my-shop") }
            jsonPath("$.existing") { value(false) }
            jsonPath("$.upload.url") { value(org.hamcrest.Matchers.startsWith("https://uploads.test/uploads/recklehs/my-shop/")) }
            jsonPath("$.upload.contentType") { value("application/gzip") }
        }
    }

    @Test
    fun `같은 이름의 업로드 앱이 있으면 새 버전이라고 알려 준다`() {
        // given
        val agent = agent()
        putUploadApp("my-shop")

        // when & then
        create(agent, "my-shop", 1234).andExpect {
            status { isCreated() }
            jsonPath("$.existing") { value(true) }
            jsonPath("$.lastDeployedAt") { value("2026-11-10T01:19:36Z") }
        }
    }

    @Test
    fun `규칙에 맞지 않는 이름은 고친 이름을 제안한다`() {
        // given
        val agent = agent()

        // when & then
        create(agent, "My Shop", 1234).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("HOSTING_INVALID_APP_NAME") }
            jsonPath("$.message") { value(org.hamcrest.Matchers.endsWith("예: my-shop")) }
        }
        create(agent, "2048-game", 1234).andExpect { jsonPath("$.message") { value(org.hamcrest.Matchers.endsWith("예: app-2048-game")) } }
        create(agent, "go", 1234).andExpect { jsonPath("$.message") { value(org.hamcrest.Matchers.endsWith("예: app-go")) } }
    }

    @Test
    fun `계정 이름과 합쳐 너무 긴 이름은 줄인 이름을 제안한다`() {
        // given
        val login = "a-very-long-github-login-name"
        val agent = agent(GitHubAccount(5, login))
        val name = "shop-" + "x".repeat(25)

        // when & then
        create(agent, name, 1234).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(org.hamcrest.Matchers.endsWith("예: " + name.take(50 - login.length))) }
        }
    }

    @Test
    fun `큰 업로드와 레포 앱 이름과 넷째 앱과 남이 쓰던 이름은 거절한다`() {
        // given
        val agent = agent()
        putApp("shop")
        putApp("blog")
        putUploadApp("notes")
        store.files["apps/recklehs/old.json"] = appJson("old", """"source": { "type": "upload" }""", null, "20261110T011744Z-0123456", "succeeded").replace("${GITHUB.id}", "999")

        // when & then
        create(agent, "my-shop", 50L * 1024 * 1024 + 1).andExpect { status { isPayloadTooLarge() } }
        create(agent, "shop", 1234).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_APP_SOURCE_MISMATCH") }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("Recklehs/shop")) }
        }
        create(agent, "fourth", 1234).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_APP_LIMIT") }
        }
        create(agent, "old", 1234).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("HOSTING_INVALID_APP_NAME") }
        }
    }

    @Test
    fun `정지된 업로드 앱에는 새 버전을 만들지 않는다`() {
        // given
        val agent = agent()
        putUploadApp("notes")
        store.files["apps/_suspended/recklehs/notes.json"] = """{ "version": 1, "team": "recklehs", "app": "notes", "code": "admin", "at": "2026-11-17T03:00:00Z" }"""

        // when & then
        create(agent, "notes", 1234).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_APP_SUSPENDED") }
        }
    }

    @Test
    fun `진행 중인 배포가 있으면 새 배포를 만들지 않는다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        start(agent, id)

        // when & then
        create(agent, "my-shop", 1234).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_DEPLOY_IN_PROGRESS") }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString(id)) }
        }
    }

    @Test
    fun `올라온 파일로 시작하면 업로드 명령을 한 번만 보낸다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")

        // when
        val first = start(agent, id)
        val again = start(agent, id)

        // then
        first shouldBe again
        with(queue.commands.single()) {
            type shouldBe HostingCommand.UPLOAD
            commandId shouldBe first
            team shouldBe "recklehs"
            app shouldBe "my-shop"
            repo shouldBe null
            owner.login shouldBe "Recklehs"
            source?.key shouldBe "uploads/recklehs/my-shop/$id.tar.gz"
            source?.sha shouldBe id
        }
    }

    @Test
    fun `GitHub 계정을 연결하지 않은 회원은 Kukie 계정의 팀으로 배포한다`() {
        // given
        val member = loggedInUser(UserFixture.oAuthUser(email = "kukie@test.com"))
        val kukie = HostingIdentity.kukie(member.user.id)
        store.files["apps/_members/${kukie.id}.json"] =
            """{ "version": 1, "owner": { "id": ${kukie.id}, "login": "${kukie.login}" }, "at": "2026-11-18T03:00:00Z" }"""
        val agent = Agent(member, jwt.generateHostingAgentToken(member.user.id))
        val id = deployId(agent, "my-shop", 1234)
        uploads.sizes["uploads/${kukie.team}/my-shop/$id.tar.gz"] = 1234

        // when
        val commandId = start(agent, id)

        // then
        with(queue.commands.single()) {
            this.commandId shouldBe commandId
            type shouldBe HostingCommand.UPLOAD
            team shouldBe kukie.team
            owner.id shouldBe kukie.id
            owner.login shouldBe kukie.login
            source?.key shouldBe "uploads/${kukie.team}/my-shop/$id.tar.gz"
        }
    }

    @Test
    fun `올라오지 않았거나 크기가 다르면 시작하지 않는다`() {
        // given
        val agent = agent()
        val id = deployId(agent, "my-shop", 1234)

        // when & then
        mockMvc.post("/hosting/agent/deploys/$id/start") { authorization(agent.token) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_UPLOAD_MISSING") }
        }
        uploads.sizes["uploads/recklehs/my-shop/$id.tar.gz"] = 999
        mockMvc.post("/hosting/agent/deploys/$id/start") { authorization(agent.token) }.andExpect { status { isConflict() } }
        queue.commands.shouldBeEmpty()
    }

    @Test
    fun `다른 배포가 진행 중이면 시작하지 않는다`() {
        // given
        val agent = agent()
        val first = uploaded(agent, "my-shop")
        val second = uploaded(agent, "blog")
        start(agent, first)

        // when & then
        mockMvc.post("/hosting/agent/deploys/$second/start") { authorization(agent.token) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_DEPLOY_IN_PROGRESS") }
        }
        queue.commands.size shouldBe 1
    }

    @Test
    fun `배포기가 시작하기 전에는 대기 중이고 시작하면 그 배포의 단계를 따라간다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)

        // when & then
        waitFor(agent, id).andExpect {
            status { isOk() }
            jsonPath("$.done") { value(false) }
            jsonPath("$.status") { value("queued") }
        }
        putUploadApp("my-shop", commandId = commandId, deploymentId = DEPLOYMENT_ID, phase = "succeeded")
        putDeployment("my-shop", DEPLOYMENT_ID, "succeeded")
        waitFor(agent, id).andExpect {
            jsonPath("$.done") { value(true) }
            jsonPath("$.status") { value("succeeded") }
            jsonPath("$.url") { value("https://my-shop-recklehs.apps.dev.durokukie.com") }
            jsonPath("$.next") { value(org.hamcrest.Matchers.containsString("GitHub 레포를 연결")) }
        }
        // 끝났으니 다음 배포를 만들 수 있다
        create(agent, "my-shop", 1234).andExpect { status { isCreated() } }
    }

    @Test
    fun `배포기가 시작 명령을 고쳐 배포했으면 바꾼 명령을 알려 준다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        putUploadApp("my-shop", commandId = commandId, deploymentId = DEPLOYMENT_ID, phase = "succeeded")
        putDeployment(
            "my-shop", DEPLOYMENT_ID, "succeeded",
            build = """{ "method": "auto", "providers": ["python"], "start": "gunicorn main:app", "corrected": "gunicorn app:app" }""",
        )

        // when & then
        waitFor(agent, id).andExpect {
            jsonPath("$.status") { value("succeeded") }
            jsonPath("$.build.corrected") { value("gunicorn app:app") }
            jsonPath("$.build.correctionNotice") { doesNotExist() }
            jsonPath("$.hint") { value(org.hamcrest.Matchers.containsString("'gunicorn main:app' 로 바꿔 배포했어요")) }
        }
    }

    @Test
    fun `배포기가 같은 명령을 다시 하면 새 배포를 따라간다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        putUploadApp("my-shop", commandId = commandId, deploymentId = DEPLOYMENT_ID, phase = "building")
        putDeployment("my-shop", DEPLOYMENT_ID, "building")
        waitFor(agent, id).andExpect { jsonPath("$.status") { value("building") } }

        // when
        putUploadApp("my-shop", commandId = commandId, deploymentId = RETRIED_ID, phase = "succeeded")
        putDeployment("my-shop", RETRIED_ID, "succeeded")

        // then
        waitFor(agent, id).andExpect { jsonPath("$.status") { value("succeeded") } }
    }

    @Test
    fun `실패하면 까닭과 안내와 로그 끝을 주고 진행 중 표시를 푼다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        putUploadApp("my-shop", commandId = commandId, deploymentId = DEPLOYMENT_ID, phase = "failed")
        putDeployment("my-shop", DEPLOYMENT_ID, "failed", failure = """{ "code": "start_failed", "message": "container exited right after start (exit code 1)" }""", hasLog = true)
        store.files["deployments/recklehs/my-shop/$GENERATION/$DEPLOYMENT_ID.log"] = "x".repeat(20000) + "=== app ===\nError: Cannot find module 'express'\n"

        // when & then
        waitFor(agent, id).andExpect {
            jsonPath("$.done") { value(true) }
            jsonPath("$.status") { value("failed") }
            jsonPath("$.failure.code") { value("start_failed") }
            jsonPath("$.hint") { value(org.hamcrest.Matchers.containsString("PORT")) }
            jsonPath("$.logTail") { value(org.hamcrest.Matchers.endsWith("Cannot find module 'express'\n")) }
            jsonPath("$.logTail.length()") { doesNotExist() }
        }
        create(agent, "my-shop", 1234).andExpect { status { isCreated() } }
    }

    @Test
    fun `시작하지 못한 자동 감지 빌드는 AI 가 고른 명령으로 한 번 더 배포하고 그 배포를 따라간다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        putUploadApp("my-shop", commandId = commandId, deploymentId = DEPLOYMENT_ID, phase = "failed")
        putDeployment(
            "my-shop", DEPLOYMENT_ID, "failed", sha = id, hasLog = true,
            failure = """{ "code": "start_failed", "message": "the start command could not be run" }""",
            build = """{ "method": "auto", "providers": ["golang"], "start": "go-getting-started", "files": ["go.mod", "main.go", "Procfile"] }""",
        )
        analyzer.startAnswer = """{"start": "./out", "confident": true, "reason": "Railpack 의 Go 빌드는 ./out 을 만듭니다."}"""

        // when
        waitFor(agent, id).andExpect {
            jsonPath("$.done") { value(false) }
            jsonPath("$.status") { value("retrying") }
        }
        val retry = awaitCommands(2).last()
        // 재시도 명령을 배포기가 받기 전이다. 명령을 보낸 뒤 상태를 남기므로 잠깐 retrying 일 수 있다
        awaitStatus(agent, id, "queued")
        putUploadApp("my-shop", commandId = retry.commandId, deploymentId = RETRIED_ID, phase = "succeeded")
        putDeployment(
            "my-shop", RETRIED_ID, "succeeded", sha = id, commandId = retry.commandId,
            build = """{ "method": "auto", "providers": ["golang"], "start": "./out", "retried": true }""",
            retry = """{ "of": "$DEPLOYMENT_ID", "start": "./out" }""",
        )

        // then
        retry.type shouldBe HostingCommand.UPLOAD
        retry.start shouldBe "./out"
        retry.retryOf shouldBe DEPLOYMENT_ID
        retry.source?.sha shouldBe id
        waitFor(agent, id).andExpect {
            jsonPath("$.status") { value("succeeded") }
            jsonPath("$.hint") { value(org.hamcrest.Matchers.containsString("""{"start": "./out"}""")) }
        }
    }

    @Test
    fun `AI 가 고른 명령으로도 시작하지 못하면 해 본 흐름을 안내에 담는다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        putUploadApp("my-shop", commandId = commandId, deploymentId = RETRIED_ID, phase = "failed")
        putDeployment(
            "my-shop", DEPLOYMENT_ID, "failed", sha = id,
            failure = """{ "code": "start_failed", "message": "x" }""",
            build = """{ "method": "auto", "start": "gunicorn app:app", "corrected": "gunicorn main:app" }""",
        )
        putDeployment(
            "my-shop", RETRIED_ID, "failed", sha = id, commandId = commandId,
            failure = """{ "code": "start_failed", "message": "x" }""",
            build = """{ "method": "auto", "start": "python app.py", "retried": true }""",
            retry = """{ "of": "$DEPLOYMENT_ID", "start": "python app.py" }""",
        )

        // when & then
        waitFor(agent, id).andExpect {
            jsonPath("$.done") { value(true) }
            jsonPath("$.hint") { value(org.hamcrest.Matchers.containsString("'gunicorn main:app' 를 규칙으로 'gunicorn app:app' 로 고쳐")) }
            jsonPath("$.hint") { value(org.hamcrest.Matchers.containsString("AI 가 고른 'python app.py' 로 한 번 더 배포했지만")) }
        }
        queue.commands.size shouldBe 1
    }

    @Test
    fun `배포기가 거절했으면 그 이유로 실패를 준다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        store.files["apps/_skipped/recklehs.json"] =
            """{ "version": 1, "sender": { "id": ${GITHUB.id}, "login": "Recklehs" }, "commandId": "$commandId", "app": "my-shop", "reason": "app_limit", "at": "2026-11-13T01:00:00Z" }"""

        // when & then
        waitFor(agent, id).andExpect {
            jsonPath("$.done") { value(true) }
            jsonPath("$.failure.code") { value("app_limit") }
        }
    }

    @Test
    fun `정지된 앱이라 거절했으면 다시 하지 말라고 안내한다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        store.files["apps/_skipped/recklehs.json"] =
            """{ "version": 1, "sender": { "id": ${GITHUB.id}, "login": "Recklehs" }, "commandId": "$commandId", "app": "my-shop", "reason": "suspended", "at": "2026-11-13T01:00:00Z" }"""

        // when & then
        waitFor(agent, id).andExpect {
            jsonPath("$.done") { value(true) }
            jsonPath("$.failure.code") { value("suspended") }
            jsonPath("$.hint") { value(org.hamcrest.Matchers.containsString("정지")) }
        }
    }

    @Test
    fun `진행이 오래 멈췄거나 배포 중에 앱이 지워졌으면 끝났다고 답한다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        putUploadApp("my-shop", commandId = commandId, deploymentId = DEPLOYMENT_ID, phase = "building")
        putDeployment("my-shop", DEPLOYMENT_ID, "building", updatedAt = "2026-01-01T00:00:00Z")

        // when & then
        waitFor(agent, id).andExpect { jsonPath("$.failure.code") { value("stuck") } }
        store.files.remove("apps/recklehs/my-shop.json")
        waitFor(agent, id).andExpect { jsonPath("$.failure.code") { value("app_deleted") } }
    }

    @Test
    fun `시작하지 않은 배포는 기다릴 수 없고 남의 배포는 없는 것과 같다`() {
        // given
        val agent = agent()
        val id = deployId(agent, "my-shop", 1234)
        val stranger = loggedInUser(UserFixture.oAuthUser(email = "stranger@test.com").apply { linkGitHub(GitHubAccount(5, "Stranger")) })

        // when & then
        waitFor(agent, id).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_DEPLOY_NOT_STARTED") }
        }
        mockMvc.get("/hosting/agent/deploys/$id/wait") { authorization(jwt.generateHostingAgentToken(stranger.user.id)) }.andExpect { status { isNotFound() } }
        mockMvc.post("/hosting/agent/deploys/$id/start") { authorization(jwt.generateHostingAgentToken(stranger.user.id)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `결과를 받기 전에 끊긴 배포가 끝났으면 다음 배포를 막지 않는다`() {
        // given: 에이전트가 wait 하지 않은 채 배포가 끝났다
        val agent = agent()
        val commandId = start(agent, uploaded(agent, "my-shop"))
        putUploadApp("my-shop", commandId = commandId, deploymentId = DEPLOYMENT_ID, phase = "succeeded")
        putDeployment("my-shop", DEPLOYMENT_ID, "succeeded")

        // when & then
        create(agent, "my-shop", 1234).andExpect { status { isCreated() } }
    }

    @Test
    fun `다른 배포가 latest 를 덮어도 이 명령의 배포를 기록에서 찾는다`() {
        // given: 이 업로드가 끝난 뒤, 에이전트가 다시 부르기 전에 다른 배포가 latest 가 됐다
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        val commandId = start(agent, id)
        putDeployment("my-shop", DEPLOYMENT_ID, "succeeded", commandId = commandId)
        putUploadApp("my-shop", commandId = "c-other", deploymentId = RETRIED_ID, phase = "building", at = "2099-01-01T00:00:00Z")
        putDeployment("my-shop", RETRIED_ID, "building")

        // when & then
        waitFor(agent, id).andExpect {
            jsonPath("$.done") { value(true) }
            jsonPath("$.status") { value("succeeded") }
        }
    }

    @Test
    fun `명령을 보내지 못하면 되돌려서 다시 시작할 수 있다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        queue.shouldFail = true

        // when
        mockMvc.post("/hosting/agent/deploys/$id/start") { authorization(agent.token) }.andExpect { status { isBadGateway() } }
        queue.shouldFail = false

        // then: 시작 전으로 돌아가 있고, 다시 부르면 보낸다
        waitFor(agent, id).andExpect { status { isConflict() } }
        val commandId = start(agent, id)
        queue.commands.single().commandId shouldBe commandId
    }

    @Test
    fun `시작을 정하고 기록하기 전에 멈춘 표시는 오래되면 풀고 다시 시작한다`() {
        // given: 먼저 부른 쪽이 명령을 정한 뒤 기록하기 전에 멈췄다
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        deploys.claimStart(id, "c-dead", HostingAgentDeploys.RECORD_TTL)

        // when & then: 방금 정한 표시는 겹쳐 부른 것으로 보고 그 명령을 준다
        start(agent, id) shouldBe "c-dead"
        queue.commands.shouldBeEmpty()

        // when & then: 2분 지난 표시는 풀고 새로 정해 보낸다
        redis.expire("hosting:agent:start:$id", HostingAgentDeploys.RECORD_TTL.minus(Duration.ofMinutes(2)))
        val commandId = start(agent, id)
        commandId shouldNotBe "c-dead"
        queue.commands.single().commandId shouldBe commandId
        waitFor(agent, id).andExpect { jsonPath("$.status") { value("queued") } }

        // then: 같은 멈춘 표시를 보고 늦게 푸는 호출은 새로 정한 표시를 지우지 못해 명령이 두 번 나가지 않는다
        deploys.releaseStart(id, "c-dead")
        deploys.claimStart(id, "x", HostingAgentDeploys.RECORD_TTL) shouldBe commandId
        queue.commands.size shouldBe 1
    }

    @Test
    fun `배포를 만든 뒤 GitHub 계정 연결이 바뀌었으면 시작하지 않는다`() {
        // given
        val agent = agent()
        val id = uploaded(agent, "my-shop")
        users.findById(agent.member.user.id).get().also { it.unlinkGitHub() }.let(users::save)

        // when & then
        mockMvc.post("/hosting/agent/deploys/$id/start") { authorization(agent.token) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("HOSTING_ACCOUNT_CHANGED") }
        }
        queue.commands.shouldBeEmpty()
    }

    private fun agent(github: GitHubAccount = GITHUB): Agent {
        val member = loggedInUser(UserFixture.oAuthUser(email = "${github.login}@test.com").apply { linkGitHub(github) })
        store.files["apps/_allowed.json"] = """{ "version": 1, "ids": [${github.id}] }"""
        return Agent(member, jwt.generateHostingAgentToken(member.user.id))
    }

    private fun uploaded(agent: Agent, name: String): String {
        val id = deployId(agent, name, 1234)
        uploads.sizes["uploads/recklehs/$name/$id.tar.gz"] = 1234
        return id
    }

    private fun create(agent: Agent, name: String, bytes: Long) = mockMvc.post("/hosting/agent/deploys") {
        authorization(agent.token)
        contentType = MediaType.APPLICATION_FORM_URLENCODED
        param("name", name)
        param("bytes", bytes.toString())
    }

    private fun deployId(agent: Agent, name: String, bytes: Long): String {
        val body = create(agent, name, bytes).andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return JsonMapper.builder().build().readTree(body).path("deployId").stringValue().also { it shouldMatch Regex("[0-9a-f]{40}") }
    }

    private fun start(agent: Agent, id: String): String {
        val body = mockMvc.post("/hosting/agent/deploys/$id/start") { authorization(agent.token) }
            .andExpect { status { isAccepted() } }.andReturn().response.contentAsString
        return JsonMapper.builder().build().readTree(body).path("commandId").stringValue()
    }

    private fun waitFor(agent: Agent, id: String) = mockMvc.get("/hosting/agent/deploys/$id/wait") {
        authorization(agent.token)
        param("timeout", "0")
    }

    private fun putApp(app: String) {
        store.files["apps/recklehs/$app.json"] = appJson(app, """"repo": { "id": 987654321, "fullName": "Recklehs/$app" }""", null, "20261110T011744Z-0123456", "succeeded")
    }

    private fun putUploadApp(app: String, commandId: String? = null, deploymentId: String = "20261110T011744Z-0123456", phase: String = "succeeded", at: String = "2026-11-10T01:19:36Z") {
        store.files["apps/recklehs/$app.json"] = appJson(app, """"source": { "type": "upload" }""", commandId, deploymentId, phase, at)
    }

    private fun appJson(app: String, origin: String, commandId: String?, deploymentId: String, phase: String, at: String = "2026-11-10T01:19:36Z") = """
        {
          "version": 1,
          "owner": { "id": ${GITHUB.id}, "login": "Recklehs", "type": "User" },
          $origin,
          "team": "recklehs",
          "app": "$app",
          "generation": "$GENERATION",
          "url": "https://$app-recklehs.apps.dev.durokukie.com",
          "config": { "port": 3000, "size": "S", "source": "default" },
          "latest": {
            "deploymentId": "$deploymentId",
            ${commandId?.let { "\"commandId\": \"$it\"," } ?: ""}
            "phase": "$phase",
            "commit": { "sha": "0123456789abcdef", "message": "upload" },
            "at": "$at"
          }
        }
    """.trimIndent()

    private fun awaitStatus(agent: Agent, id: String, status: String) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        var last: String? = null
        while (System.nanoTime() < deadline) {
            last = JsonMapper.builder().build().readTree(waitFor(agent, id).andReturn().response.contentAsString).path("status").stringValue()
            if (last == status) return
            Thread.sleep(50)
        }
        last shouldBe status
    }

    private fun awaitCommands(count: Int): List<HostingCommand> {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (queue.commands.size < count && System.nanoTime() < deadline) Thread.sleep(50)
        queue.commands.size shouldBe count
        return queue.commands.toList()
    }

    private fun putDeployment(
        app: String,
        id: String,
        phase: String,
        failure: String = "null",
        hasLog: Boolean = false,
        updatedAt: String = "2099-11-13T01:17:51Z",
        commandId: String? = null,
        build: String? = null,
        sha: String = "0123456789abcdef",
        retry: String? = null,
    ) {
        store.files["deployments/recklehs/$app/$GENERATION/$id.json"] = """
            {
              "version": 1,
              "deploymentId": "$id",
              "phase": "$phase",
              "trigger": "upload", ${commandId?.let { "\"commandId\": \"$it\"," } ?: ""}
              "commit": { "sha": "$sha", "message": "upload" },
              "config": { "port": 3000, "size": "S", "source": "default" }, ${build?.let { "\"build\": $it," } ?: ""} ${retry?.let { "\"retry\": $it," } ?: ""}
              "imageReused": false,
              "hasLog": $hasLog,
              "failure": $failure,
              "startedAt": "2026-11-13T01:17:44Z",
              "updatedAt": "$updatedAt"
            }
        """.trimIndent()
    }

    private data class Agent(val member: LoggedInUser, val token: String)

    companion object {
        private const val GENERATION = "20261113T021500Z"
        private const val DEPLOYMENT_ID = "20261113T021744Z-3f9a1c0"
        private const val RETRIED_ID = "20261113T031744Z-3f9a1c0"
        private val GITHUB = GitHubAccount(id = 127021195, login = "Recklehs")
    }
}
