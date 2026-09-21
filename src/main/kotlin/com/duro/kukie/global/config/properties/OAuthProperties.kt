package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 제공자마다 OAuth 클라이언트가 **두 쌍**이다.
 *
 * - 데스크톱 쌍(`clientId`/`clientSecret`): 앱(Electron)이 `http://127.0.0.1:<포트>` 루프백으로 code 를 받는 흐름
 * - 웹 쌍(`webClientId`/`webClientSecret`): 브라우저가 `/auth/callback` 으로 code 를 받는 흐름.
 *   구글은 클라이언트 타입이 달라서("데스크톱 앱" 은 https 리다이렉트를 못 받는다), 깃허브는 OAuth App 이
 *   콜백 주소를 하나만 받아서 따로 등록해야 한다
 *
 * code 교환은 그 흐름을 시작한 클라이언트의 secret 으로만 되므로 요청의 `redirectUri` 로 쌍을 고른다
 * (`OAuthCredentialsResolver`). 웹 쌍이 비어 있으면(환경변수 없음) 웹 로그인만 503 이고 앱은 그대로 된다.
 */
@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    val github: Registration,
    val google: Registration,
) {
    data class Registration(
        val clientId: String,
        val clientSecret: String,
        val webClientId: String? = null,
        val webClientSecret: String? = null,
    ) {
        val desktop: Credentials
            get() = Credentials(clientId, clientSecret)

        /** 둘 다 채워졌을 때만. `${VAR:}` 로 비어 들어온 빈 문자열은 "없음" 으로 본다. */
        val web: Credentials?
            get() = if (webClientId.isNullOrBlank() || webClientSecret.isNullOrBlank()) null
            else Credentials(webClientId, webClientSecret)
    }

    data class Credentials(
        val clientId: String,
        val clientSecret: String,
    )
}
