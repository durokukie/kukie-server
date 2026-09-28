package com.duro.kukie.global.config.properties

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** 제공자마다 데스크톱·웹 두 쌍. 컴포즈는 빠진 값을 빈 문자열로 넘기므로 웹 쌍은 `@NotBlank` 로 막는다. */
@Validated
@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    @field:Valid val github: Registration,
    @field:Valid val google: Registration,
) {
    data class Registration(
        val clientId: String,
        val clientSecret: String,
        @field:NotBlank val webClientId: String,
        @field:NotBlank val webClientSecret: String,
    ) {
        val desktop: Credentials
            get() = Credentials(clientId, clientSecret)

        val web: Credentials
            get() = Credentials(webClientId, webClientSecret)
    }

    data class Credentials(
        val clientId: String,
        val clientSecret: String,
    )
}
