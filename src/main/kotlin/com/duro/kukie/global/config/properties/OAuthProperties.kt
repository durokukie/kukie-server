package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    val github: Registration,
    val google: Registration,
) {
    data class Registration(
        val clientId: String,
        val clientSecret: String,
    )
}
