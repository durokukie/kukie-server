package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "aws")
data class AwsProperties(
    @field:NotBlank val region: String,
)
