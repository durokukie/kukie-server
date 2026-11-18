package com.duro.kukie.hosting.domain

import com.duro.kukie.user.domain.GitHubAccount
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import java.util.UUID

class HostingIdentityTest {

    @Test
    fun `GitHub 를 연결했으면 그 계정이 주인이다`() {
        // given
        val userId = UUID.randomUUID()

        // when
        val identity = HostingIdentity.of(userId, GitHubAccount(127021195, "Recklehs"))

        // then
        identity shouldBe HostingIdentity(127021195, "Recklehs")
        identity.team shouldBe "recklehs"
        identity.gitHub shouldBe true
    }

    @Test
    fun `GitHub 를 연결하지 않았으면 회원 id 로 늘 같은 Kukie 계정을 만든다`() {
        // given
        val userId = UUID.fromString("019a8b3c-1d2e-7f00-8a1b-2c3d4e5f6a7b")

        // when
        val identity = HostingIdentity.of(userId, null)

        // then
        identity shouldBe HostingIdentity.kukie(userId)
        identity.gitHub shouldBe false
        identity.login shouldMatch Regex("u-[0-9a-z]{10}")
        identity.team shouldBe identity.login
        (identity.id < 9_007_199_254_740_992L) shouldBe true
    }

    @Test
    fun `회원마다 다른 Kukie 계정이 된다`() {
        // given
        val ids = List(1000) { UUID.randomUUID() }

        // when
        val identities = ids.map(HostingIdentity::kukie)

        // then
        identities.map { it.id }.toSet().size shouldBe 1000
        identities.map { it.team }.toSet().size shouldBe 1000
    }
}
