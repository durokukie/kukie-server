package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "internal")
data class InternalProperties(
    @field:NotBlank val token: String,
    val gatewayToken: String = "",
) {
    init {
        require(gatewayToken.isEmpty() || (gatewayToken.isNotBlank() && gatewayToken != token)) {
            "Gateway token must be nonblank and distinct from the agent token"
        }
    }
}
