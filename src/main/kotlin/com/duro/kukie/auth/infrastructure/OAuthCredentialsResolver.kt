package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.global.config.properties.OAuthProperties
import org.springframework.stereotype.Component
import java.net.URI

/** `redirectUri` 의 host 가 127.0.0.1 이면 데스크톱, 그 밖은 웹으로 간주한다. */
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
            registration.web
        }
    }

    private fun isDesktopLoopback(redirectUri: String): Boolean {
        val uri = runCatching { URI(redirectUri) }.getOrNull() ?: return false

        return uri.scheme.equals("http", ignoreCase = true) && uri.host == DESKTOP_LOOPBACK_HOST
    }

    companion object {
        private const val DESKTOP_LOOPBACK_HOST = "127.0.0.1"
    }
}
