package com.duro.kukie.hosting

import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.support.FakeHostingStateStore
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get

/** 지운 앱의 배포 이력: 앱 파일은 없어도 배포 기록은 30일 남는다. */
class HostingHistoryIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var store: FakeHostingStateStore

    @Test
    fun `지운 앱과 다시 만든 앱의 그 전 세대 기록을 최근 배포순으로 준다`() {
        // given
        val member = linkedUser()
        putApp("shop", generation = NEW_GENERATION)
        putDeployment("shop", NEW_GENERATION, "20261115T010000Z-1111111")
        putDeployment("shop", OLD_GENERATION, "20261105T010000Z-2222222")
        putDeployment("blog", OLD_GENERATION, "20261108T010000Z-3333333", phase = "failed", hasLog = true)
        putDeployment("blog", OLD_GENERATION, "20261107T010000Z-4444444")
        store.files["deployments/recklehs/blog/_logs/20261108T010100Z.log"] = "line\n"
        store.files["deployments/recklehs/_db/20261110T000000Z-00000001.json"] = "{}"

        // when & then
        mockMvc.get("/hosting/history") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.apps.length()") { value(2) }
            jsonPath("$.apps[0].app") { value("blog") }
            jsonPath("$.apps[0].generation") { value(OLD_GENERATION) }
            jsonPath("$.apps[0].repository") { value("Recklehs/blog") }
            jsonPath("$.apps[0].uploaded") { value(false) }
            jsonPath("$.apps[0].deployments.length()") { value(2) }
            jsonPath("$.apps[0].deployments[0].deploymentId") { value("20261108T010000Z-3333333") }
            jsonPath("$.apps[0].deployments[0].phase") { value("failed") }
            jsonPath("$.apps[1].app") { value("shop") }
            jsonPath("$.apps[1].deployments[0].deploymentId") { value("20261105T010000Z-2222222") }
        }
    }

    @Test
    fun `GitHub 계정 팀에서는 다른 주인의 기록과 주인이 적히지 않은 옛 기록을 주지 않는다`() {
        // given
        val member = linkedUser()
        putDeployment("shop", OLD_GENERATION, "20261105T010000Z-2222222", ownerId = 999)
        putDeployment("blog", OLD_GENERATION, "20261108T010000Z-3333333", ownerId = null)

        // when & then
        mockMvc.get("/hosting/history") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.apps.length()") { value(0) }
        }
    }

    @Test
    fun `Kukie 계정 팀은 주인이 적히지 않은 옛 기록도 준다`() {
        // given
        val member = loggedInUser(UserFixture.oAuthUser())
        val team = HostingIdentity.kukie(member.user.id).team
        putDeployment("shop", OLD_GENERATION, "20261105T010000Z-2222222", ownerId = null, team = team)

        // when & then
        mockMvc.get("/hosting/history") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.apps.length()") { value(1) }
            jsonPath("$.apps[0].app") { value("shop") }
        }
    }

    @Test
    fun `지운 앱의 빌드 로그를 준다`() {
        // given
        val member = linkedUser()
        putDeployment("blog", OLD_GENERATION, "20261108T010000Z-3333333", phase = "failed", hasLog = true)
        store.files["deployments/recklehs/blog/$OLD_GENERATION/20261108T010000Z-3333333.log"] = "npm ERR! missing script: start\n"

        // when & then
        mockMvc.get("/hosting/history/blog/$OLD_GENERATION/deployments/20261108T010000Z-3333333/log") {
            authorization(member.accessToken)
        }.andExpect {
            status { isOk() }
            content { string("npm ERR! missing script: start\n") }
        }
    }

    @Test
    fun `다른 주인의 지운 앱 빌드 로그는 볼 수 없다`() {
        // given
        val member = linkedUser()
        putDeployment("blog", OLD_GENERATION, "20261108T010000Z-3333333", hasLog = true, ownerId = 999)
        store.files["deployments/recklehs/blog/$OLD_GENERATION/20261108T010000Z-3333333.log"] = "secret\n"

        // when & then
        mockMvc.get("/hosting/history/blog/$OLD_GENERATION/deployments/20261108T010000Z-3333333/log") {
            authorization(member.accessToken)
        }.andExpect {
            status { isNotFound() }
        }
    }

    private fun linkedUser(user: User = UserFixture.oAuthUser()): LoggedInUser = loggedInUser(user.apply { linkGitHub(GITHUB) })

    private fun putApp(app: String, generation: String) {
        store.files["apps/recklehs/$app.json"] = """
            {
              "version": 1,
              "owner": { "id": ${GITHUB.id}, "login": "Recklehs", "type": "User" },
              "repo": { "id": 987654321, "fullName": "Recklehs/$app" },
              "team": "recklehs",
              "app": "$app",
              "generation": "$generation",
              "url": "https://$app--recklehs.apps.dev.durokukie.com",
              "config": { "port": 3000, "size": "S", "source": "default" },
              "latest": { "deploymentId": "20261115T010000Z-1111111", "phase": "succeeded", "commit": { "sha": "0123456789abcdef" }, "at": "2026-11-15T01:02:00Z" }
            }
        """.trimIndent()
    }

    private fun putDeployment(
        app: String,
        generation: String,
        id: String,
        phase: String = "succeeded",
        hasLog: Boolean = false,
        ownerId: Long? = GITHUB.id,
        team: String = "recklehs",
    ) {
        val startedAt = "${id.substring(0, 4)}-${id.substring(4, 6)}-${id.substring(6, 8)}T01:00:00Z"
        val owner = ownerId?.let { """, "owner": { "id": $it, "login": "Recklehs" }, "repo": { "id": 1, "fullName": "Recklehs/$app" }""" } ?: ""
        store.files["deployments/$team/$app/$generation/$id.json"] = """
            { "version": 1, "deploymentId": "$id", "phase": "$phase", "trigger": "push", "commit": { "sha": "0123456789abcdef" },
              "config": { "port": 3000, "size": "S", "source": "default" }, "imageReused": false, "hasLog": $hasLog, "failure": null,
              "startedAt": "$startedAt", "updatedAt": "$startedAt"$owner }
        """.trimIndent()
    }

    companion object {
        private const val OLD_GENERATION = "20261101T031500Z"
        private const val NEW_GENERATION = "20261112T000000Z"
        private val GITHUB = GitHubAccount(id = 127021195, login = "Recklehs")
    }
}
