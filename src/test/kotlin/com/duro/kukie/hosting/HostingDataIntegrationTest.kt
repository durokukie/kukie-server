package com.duro.kukie.hosting

import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.support.FakeHostingCommandQueue
import com.duro.kukie.support.FakeHostingStateStore
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.User
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 사용자가 운영 중에 가져가는 것: 실행 상태, 실행 로그, 앱 기록, 팀 DB 내보내기. */
class HostingDataIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var store: FakeHostingStateStore

    @Autowired
    private lateinit var queue: FakeHostingCommandQueue

    @Test
    fun `앱 상세는 배포기가 본 실행 상태를 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        store.files["apps/_runtime/recklehs/shop.json"] = """
            { "version": 1, "team": "recklehs", "app": "shop", "state": "crashing", "replicas": 1, "ready": 0, "restarts": 4,
              "lastExit": { "reason": "OOMKilled", "exitCode": 137, "at": "2026-11-20T03:02:10Z" }, "since": "2026-11-20T03:01:00Z" }
        """.trimIndent()

        // when & then
        mockMvc.get("/hosting/apps/shop") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.runtime.state") { value("crashing") }
            jsonPath("$.runtime.restarts") { value(4) }
            jsonPath("$.runtime.lastExit.reason") { value("OOMKilled") }
            jsonPath("$.runtime.since") { value("2026-11-20T03:01:00Z") }
        }
    }

    @Test
    fun `배포기가 아직 보지 않은 앱은 실행 상태가 없다`() {
        // given
        val member = linkedUser()
        putApp("shop")

        // when & then
        mockMvc.get("/hosting/apps/shop") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.runtime") { value(null) }
        }
    }

    @Test
    fun `실행 로그는 기간 안의 줄만 이어 붙여 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        putLog("shop", "20261120T030100Z", "2026-11-20T03:00:10.000000000Z before", "2026-11-20T03:00:40.000000000Z first")
        putLog("shop", "20261120T030200Z", "2026-11-20T03:01:30.000000000Z second", "--- the app restarted ---")
        putLog("shop", "20261120T031000Z", "2026-11-20T03:09:30.000000000Z after")

        // when & then
        mockMvc.get("/hosting/apps/shop/logs") {
            authorization(member.accessToken)
            param("from", "2026-11-20T03:00:30Z")
            param("to", "2026-11-20T03:05:00Z")
        }.andExpect {
            status { isOk() }
            content { string("2026-11-20T03:00:40.000000000Z first\n2026-11-20T03:01:30.000000000Z second\n--- the app restarted ---\n") }
        }
    }

    @Test
    fun `지우고 다시 만든 앱에는 그 전 앱의 로그를 주지 않는다`() {
        // given
        val member = linkedUser()
        putApp("shop", generation = "20261120T030500Z")
        putLog("shop", "20261120T030100Z", "2026-11-20T03:00:40.000000000Z old app")
        putLog("shop", "20261120T030700Z", "2026-11-20T03:06:40.000000000Z new app")

        // when & then
        mockMvc.get("/hosting/apps/shop/logs") {
            authorization(member.accessToken)
            param("from", "2026-11-20T02:00:00Z")
            param("to", "2026-11-20T04:00:00Z")
        }.andExpect {
            content { string("2026-11-20T03:06:40.000000000Z new app\n") }
        }
    }

    @Test
    fun `로그 기간이 거꾸로이거나 31일을 넘으면 받지 않는다`() {
        // given
        val member = linkedUser()
        putApp("shop")

        // when & then
        listOf("2026-11-20T03:00:00Z" to "2026-11-20T02:00:00Z", "2026-10-01T00:00:00Z" to "2026-11-20T00:00:00Z").forEach { (from, to) ->
            mockMvc.get("/hosting/apps/shop/logs") {
                authorization(member.accessToken)
                param("from", from)
                param("to", to)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("HOSTING_INVALID_LOG_RANGE") }
            }
        }
    }

    @Test
    fun `다른 계정이 주인인 앱의 실행 로그와 기록은 볼 수 없다`() {
        // given
        val member = linkedUser()
        putApp("shop", ownerId = 999)
        putLog("shop", "20261120T030100Z", "2026-11-20T03:00:40.000000000Z secret")

        // when & then
        listOf("/hosting/apps/shop/logs", "/hosting/apps/shop/record").forEach { path ->
            mockMvc.get(path) {
                authorization(member.accessToken)
            }.andExpect {
                status { isNotFound() }
            }
        }
    }

    @Test
    fun `앱 기록은 앱과 남은 배포 전부를 파일로 준다`() {
        // given
        val member = linkedUser()
        putApp("shop")
        (1..25).forEach { putDeployment("shop", "20261110T0100%02dZ-0123456".format(it)) }

        // when & then
        mockMvc.get("/hosting/apps/shop/record") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            header { string("Content-Disposition", "attachment; filename=\"kukie-app-shop.json\"") }
            jsonPath("$.app.app") { value("shop") }
            jsonPath("$.deployments.length()") { value(25) }
            jsonPath("$.deployments[0].deploymentId") { value("20261110T010025Z-0123456") }
        }
    }

    @Test
    fun `팀 DB 내보내기를 요청하면 배포기에 명령을 보내고 10분 동안 다시 보내지 않는다`() {
        // given
        val member = linkedUser()
        putMember()

        // when & then
        mockMvc.post("/hosting/db/exports") {
            authorization(member.accessToken)
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.commandId") { value(queue.commands.single().commandId) }
        }
        with(queue.commands.single()) {
            type shouldBe HostingCommand.DB_EXPORT
            team shouldBe "recklehs"
            owner.id shouldBe GITHUB.id
            app shouldBe null
            commandId shouldMatch Regex("\\d{8}T\\d{6}Z-[0-9a-f]{8}")
        }

        mockMvc.post("/hosting/db/exports") {
            authorization(member.accessToken)
        }.andExpect {
            status { isTooManyRequests() }
        }
    }

    @Test
    fun `사용 신청을 하지 않은 계정은 팀 DB 를 내보낼 수 없다`() {
        // given
        val member = linkedUser()

        // when & then
        mockMvc.post("/hosting/db/exports") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
        }
        queue.commands.size shouldBe 0
    }

    @Test
    fun `내보내기 목록은 내 것만 최신순으로 주고 끝난 것에 내려받기 주소를 붙인다`() {
        // given
        val member = linkedUser()
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        putExport("20261120T030000Z-00000001", "succeeded", now, bytes = 1234)
        putExport("20261120T031000Z-00000002", "running", now)
        putExport("20261120T032000Z-00000003", "running", now.minus(31, ChronoUnit.MINUTES))
        putExport("20261120T033000Z-00000004", "succeeded", now, ownerId = 999)

        // when & then
        mockMvc.get("/hosting/db/exports") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.exports.length()") { value(3) }
            jsonPath("$.exports[0].exportId") { value("20261120T032000Z-00000003") }
            jsonPath("$.exports[0].phase") { value("failed") }
            jsonPath("$.exports[0].failure.code") { value("stale") }
            jsonPath("$.exports[1].phase") { value("running") }
            jsonPath("$.exports[1].downloadUrl") { value(null) }
            jsonPath("$.exports[2].bytes") { value(1234) }
            jsonPath("$.exports[2].downloadUrl") {
                value("https://bucket.example/deployments/recklehs/_db/20261120T030000Z-00000001.sql.gz?filename=kukie-db-recklehs-20261120T030000Z-00000001.sql.gz")
            }
        }
    }

    private fun linkedUser(user: User = UserFixture.oAuthUser()): LoggedInUser = loggedInUser(user.apply { linkGitHub(GITHUB) })

    private fun putMember() {
        store.files["apps/_members/${GITHUB.id}.json"] =
            """{ "version": 1, "owner": { "id": ${GITHUB.id}, "login": "Recklehs" }, "at": "2026-11-17T03:00:00Z" }"""
    }

    private fun putApp(app: String, ownerId: Long = GITHUB.id, generation: String = GENERATION) {
        store.files["apps/recklehs/$app.json"] = """
            {
              "version": 1,
              "owner": { "id": $ownerId, "login": "Recklehs", "type": "User" },
              "repo": { "id": 987654321, "fullName": "Recklehs/$app" },
              "team": "recklehs",
              "app": "$app",
              "generation": "$generation",
              "url": "https://$app--recklehs.apps.dev.durokukie.com",
              "config": { "port": 3000, "size": "S", "source": "default" },
              "latest": {
                "deploymentId": "20261110T011744Z-0123456",
                "phase": "succeeded",
                "commit": { "sha": "0123456789abcdef" },
                "at": "2026-11-10T01:19:36Z"
              }
            }
        """.trimIndent()
    }

    private fun putDeployment(app: String, id: String) {
        store.files["deployments/recklehs/$app/$GENERATION/$id.json"] = """
            { "version": 1, "deploymentId": "$id", "phase": "succeeded", "trigger": "push", "commit": { "sha": "0123456789abcdef" },
              "config": { "port": 3000, "size": "S", "source": "default" }, "imageReused": false, "hasLog": false, "failure": null,
              "startedAt": "2026-11-10T01:17:44Z", "updatedAt": "2026-11-10T01:17:51Z" }
        """.trimIndent()
    }

    private fun putLog(app: String, stamp: String, vararg lines: String) {
        store.files["deployments/recklehs/$app/_logs/$stamp.log"] = lines.joinToString("\n", postfix = "\n")
    }

    private fun putExport(id: String, phase: String, updatedAt: Instant, bytes: Long? = null, ownerId: Long = GITHUB.id) {
        store.files["deployments/recklehs/_db/$id.json"] = """
            { "version": 1, "exportId": "$id", "owner": { "id": $ownerId }, "phase": "$phase", ${bytes?.let { "\"bytes\": $it," } ?: ""}
              "failure": null, "startedAt": "$updatedAt", "updatedAt": "$updatedAt" }
        """.trimIndent()
    }

    companion object {
        private const val GENERATION = "20261101T031500Z"
        private val GITHUB = GitHubAccount(id = 127021195, login = "Recklehs")
    }
}
