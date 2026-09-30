package com.duro.kukie.global.config.properties

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    @field:Valid val github: Credential,
    @field:Valid val google: Credential,
) {
    data class Credential(
        @field:NotBlank val clientId: String,
        @field:NotBlank val clientSecret: String,
    )
}
