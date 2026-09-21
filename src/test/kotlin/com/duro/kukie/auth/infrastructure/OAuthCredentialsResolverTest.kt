package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.OAuthClientNotConfiguredException
import com.duro.kukie.global.config.properties.OAuthProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OAuthCredentialsResolverTest {

    private val github = OAuthProperties.Registration("gh-desktop-id", "gh-desktop-secret", "gh-web-id", "gh-web-secret")
    private val google = OAuthProperties.Registration("g-desktop-id", "g-desktop-secret", webClientId = "", webClientSecret = "")
    private val resolver = OAuthCredentialsResolver(OAuthProperties(github = github, google = google))

    @Test
    fun `앱 루프백 주소로 돌아온 요청은 데스크톱 쌍으로 교환한다`() {
        // when
        val credentials = resolver.resolve(OAuthProvider.GITHUB, "http://127.0.0.1:53211/callback")

        // then
        credentials shouldBe OAuthProperties.Credentials("gh-desktop-id", "gh-desktop-secret")
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

    @Test
    fun `웹 쌍이 비어 있으면 웹 요청은 설정 없음 예외가 나고 앱 요청은 그대로 된다`() {
        // when & then
        shouldThrow<OAuthClientNotConfiguredException> {
            resolver.resolve(OAuthProvider.GOOGLE, "https://kukie.io/auth/callback")
        }
        resolver.resolve(OAuthProvider.GOOGLE, "http://127.0.0.1:53211/callback") shouldBe
            OAuthProperties.Credentials("g-desktop-id", "g-desktop-secret")
    }
}
