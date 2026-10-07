package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "web-app")
data class WebAppProperties(
    @field:NotBlank val url: String,
)
