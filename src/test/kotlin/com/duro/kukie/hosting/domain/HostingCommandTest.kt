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
        command.target shouldBe "_new"
    }

    @Test
    fun `연결 명령은 업로드 앱에 레포와 세대, 주인의 계정명을 함께 적는다`() {
        // given
        val app = HostingApp(
            version = 1,
            owner = HostingAccount(127021195, "Recklehs", "User"),
            source = HostingSource(HostingSource.UPLOAD),
            team = "recklehs",
            app = "my-shop",
            generation = "20261113T021500Z",
            url = "https://my-shop-recklehs.apps.dev.durokukie.com",
            config = HostingConfig(3000, "S", "default"),
            latest = HostingLatest("20261113T021744Z-3f9a1c0", null, "succeeded", HostingCommit("3f9a1c0", "upload"), Instant.parse("2026-11-13T02:19:36Z")),
        )

        // when
        val command = HostingCommand.connect(GitHubAccount(127021195, "Recklehs"), app, "Recklehs/shop-frontend")
        val json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(command))

        // then
        json.path("type").stringValue() shouldBe "connect"
        json.path("owner").path("login").stringValue() shouldBe "Recklehs"
        json.path("repo").path("fullName").stringValue() shouldBe "Recklehs/shop-frontend"
        json.path("app").stringValue() shouldBe "my-shop"
        json.path("generation").stringValue() shouldBe "20261113T021500Z"
        app.uploaded shouldBe true
    }

    @Test
    fun `업로드 앱의 삭제 명령에는 레포가 없다`() {
        // given
        val app = HostingApp(
            version = 1,
            owner = HostingAccount(127021195, "Recklehs", "User"),
            source = HostingSource(HostingSource.UPLOAD),
            team = "recklehs",
            app = "my-shop",
            generation = "20261113T021500Z",
            url = "https://my-shop-recklehs.apps.dev.durokukie.com",
            config = HostingConfig(3000, "S", "default"),
            latest = HostingLatest("20261113T021744Z-3f9a1c0", null, "succeeded", HostingCommit("3f9a1c0", "upload"), Instant.parse("2026-11-13T02:19:36Z")),
        )

        // when
        val json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(HostingCommand.of(HostingCommand.DELETE, app)))

        // then
        json.has("repo") shouldBe false
        json.path("generation").stringValue() shouldBe "20261113T021500Z"
    }
}
