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
) {
    fun installUrl(state: String): String = "$webUrl/apps/$slug/installations/new?state=$state"
}
