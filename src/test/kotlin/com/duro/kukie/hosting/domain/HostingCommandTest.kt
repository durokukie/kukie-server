package com.duro.kukie.hosting.domain

import com.duro.kukie.user.domain.GitHubAccount
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class HostingCommandTest {

    @Test
    fun `배포기가 읽는 이름과 모양으로 명령을 적는다`() {
        // given
        val app = HostingApp(
            version = 1,
            owner = HostingAccount(127021195, "Recklehs", "User"),
            repo = HostingRepo(987654321, "Recklehs/shop"),
            team = "recklehs",
            app = "shop",
            generation = "20261101T031500Z",
            url = "https://shop-recklehs.apps.dev.durokukie.com",
            config = HostingConfig(3000, "S", "default"),
            latest = HostingLatest("20261110T011744Z-0123456", null, "succeeded", HostingCommit("0123456"), Instant.parse("2026-11-10T01:19:36Z")),
        )

        // when
        val json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(HostingCommand.of(HostingCommand.DELETE, app)))

        // then
        json.path("version").intValue() shouldBe 1
        json.path("commandId").stringValue() shouldMatch Regex("[0-9a-f-]{36}")
        json.path("type").stringValue() shouldBe "delete"
        json.path("at").stringValue() shouldMatch Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
        json.path("owner").path("id").longValue() shouldBe 127021195
        json.path("repo").path("fullName").stringValue() shouldBe "Recklehs/shop"
        json.path("team").stringValue() shouldBe "recklehs"
        json.path("app").stringValue() shouldBe "shop"
        json.path("generation").stringValue() shouldBe "20261101T031500Z"
        json.has("target") shouldBe false
    }

    @Test
    fun `배포 시작 명령에는 앱 이름과 세대가 없다`() {
        // when
        val command = HostingCommand.deploy(GitHubAccount(127021195, "Recklehs"), "Recklehs/Shop")
        val json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(command))

        // then
        json.path("type").stringValue() shouldBe "deploy"
        json.path("repo").path("fullName").stringValue() shouldBe "Recklehs/Shop"
        json.path("team").stringValue() shouldBe "recklehs"
        json.has("app") shouldBe false
        json.has("generation") shouldBe false
        command.target shouldBe "recklehs/shop"
    }
}
