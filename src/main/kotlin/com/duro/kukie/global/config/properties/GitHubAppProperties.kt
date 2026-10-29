package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "github-app")
data class GitHubAppProperties(
    @field:Positive val id: Long,
    @field:NotBlank val slug: String,
    /** PEM(PKCS#1 또는 PKCS#8). 환경 변수로만 준다. 줄바꿈이 `\n` 글자로 들어와도 받는다. */
    @field:NotBlank val privateKey: String,
    @field:NotBlank val apiUrl: String,
    @field:NotBlank val webUrl: String,
    /** 설치 때 사용자 인증(OAuth)을 켜면 주는 App 의 client id·secret. 둘 다 있으면 설치한 사람이 그 설치에 접근할 수 있는지 확인한다. */
    val clientId: String? = null,
    val clientSecret: String? = null,
) {
    val verifiesInstaller: Boolean
        get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()

    fun installUrl(state: String): String = "$webUrl/apps/$slug/installations/new?state=$state"
}
