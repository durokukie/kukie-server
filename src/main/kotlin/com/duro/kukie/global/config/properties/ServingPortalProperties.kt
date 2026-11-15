package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("serving.portal")
data class ServingPortalProperties(val publicApiBaseUrl: String? = null)
