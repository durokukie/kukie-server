package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "verification-code")
data class VerificationCodeProperties(
    val expiration: Duration,
)
