package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.OAuthClientNotConfiguredException
import com.duro.kukie.global.config.properties.OAuthProperties
import org.springframework.stereotype.Component

/**
 * 요청의 `redirectUri` 를 보고 데스크톱 쌍과 웹 쌍 중 하나를 고른다.
 *
 * 앱은 `http://127.0.0.1:<포트>/callback` 으로만 code 를 받는다 (kukie-electron `main/auth/loopback.ts` — `localhost` 는
 * IPv6 로 풀릴 수 있어 일부러 안 쓴다). 그 밖의 주소(`https://<도메인>/auth/callback`, 로컬 웹 개발의 `http://localhost:5173/...`)는
 * 전부 웹이다. 제공자가 `redirect_uri` 를 등록값과 대조하므로 여기서 주소를 더 검사하지는 않는다 — 쌍만 고른다.
 */
@Component
class OAuthCredentialsResolver(
    private val oAuthProperties: OAuthProperties,
) {

    fun resolve(provider: OAuthProvider, redirectUri: String): OAuthProperties.Credentials {
        val registration = when (provider) {
            OAuthProvider.GITHUB -> oAuthProperties.github
            OAuthProvider.GOOGLE -> oAuthProperties.google
        }

        return if (isDesktopLoopback(redirectUri)) {
            registration.desktop
        } else {
            registration.web ?: throw OAuthClientNotConfiguredException()
        }
    }

    private fun isDesktopLoopback(redirectUri: String): Boolean =
        DESKTOP_LOOPBACK_PREFIXES.any { redirectUri.startsWith(it) }

    companion object {
        private val DESKTOP_LOOPBACK_PREFIXES = listOf("http://127.0.0.1:", "http://127.0.0.1/")
    }
}
