package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("serving.relay")
data class ServingRelayProperties(
    val allowedHosts: Set<String> = emptySet(),
    @field:Min(1) val maxConnections: Int = 32,
    @field:Min(1) val maxConnectionsPerKey: Int = 4,
    @field:Min(1) val connectSeconds: Long = 5,
    @field:Min(1) val idleSeconds: Long = 30,
    @field:Min(1) val totalSeconds: Long = 300,
)
