package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.global.config.properties.OAuthProperties
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OAuthCredentialsResolverTest {

    private val github = OAuthProperties.Registration("gh-desktop-id", "gh-desktop-secret", "gh-web-id", "gh-web-secret")
    private val google = OAuthProperties.Registration("g-desktop-id", "g-desktop-secret", "g-web-id", "g-web-secret")
    private val resolver = OAuthCredentialsResolver(OAuthProperties(github = github, google = google))

    @Test
    fun `앱 루프백 주소로 돌아온 요청은 데스크톱 쌍으로 교환한다`() {
        // when
        val withPort = resolver.resolve(OAuthProvider.GITHUB, "http://127.0.0.1:53211/callback")
        val bare = resolver.resolve(OAuthProvider.GITHUB, "http://127.0.0.1")

        // then
        withPort shouldBe OAuthProperties.Credentials("gh-desktop-id", "gh-desktop-secret")
        bare shouldBe OAuthProperties.Credentials("gh-desktop-id", "gh-desktop-secret")
    }

    @Test
    fun `루프백처럼 보이게 꾸민 주소는 데스크톱 쌍을 받지 못한다`() {
        // given — userinfo 자리에 127.0.0.1 을 넣어 접두사 검사를 속이는 모양. host 는 evil.com 이다
        val disguised = "http://127.0.0.1:8080@evil.com/callback"

        // when
        val credentials = resolver.resolve(OAuthProvider.GITHUB, disguised)

        // then
        credentials shouldBe OAuthProperties.Credentials("gh-web-id", "gh-web-secret")
    }

    @Test
    fun `웹 주소로 돌아온 요청은 웹 쌍으로 교환한다`() {
        // when
        val production = resolver.resolve(OAuthProvider.GITHUB, "https://kukie.io/auth/callback")
        val localDev = resolver.resolve(OAuthProvider.GITHUB, "http://localhost:5173/auth/callback")

        // then
        production shouldBe OAuthProperties.Credentials("gh-web-id", "gh-web-secret")
        localDev shouldBe OAuthProperties.Credentials("gh-web-id", "gh-web-secret")
    }
}
